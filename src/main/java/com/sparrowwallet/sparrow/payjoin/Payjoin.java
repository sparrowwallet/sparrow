package com.sparrowwallet.sparrow.payjoin;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.*;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.net.HttpClientService;
import com.sparrowwallet.sparrow.net.Protocol;
import com.sparrowwallet.tern.http.client.HttpResponseException;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class Payjoin {
    private static final Logger log = LoggerFactory.getLogger(Payjoin.class);

    private final BitcoinURI payjoinURI;
    private final Wallet wallet;
    private final PSBT psbt;

    public Payjoin(BitcoinURI payjoinURI, Wallet wallet, PSBT psbt) {
        this.payjoinURI = payjoinURI;
        this.wallet = wallet;
        this.psbt = psbt.getForExport();

        if(payjoinURI.getAddress() == null) {
            throw new IllegalArgumentException("Payjoin URI must have an address");
        }

        for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
            if(psbtInput.getUtxo() == null) {
                throw new IllegalArgumentException("Original PSBT for payjoin transaction must have non_witness_utxo or witness_utxo fields for all inputs");
            }
        }

        if(!psbt.isFinalized()) {
            throw new IllegalArgumentException("Original PSBT for payjoin transaction must be finalized");
        }
    }

    public PSBT requestPayjoinPSBT(boolean allowOutputSubstitution) throws PayjoinReceiverException, PSBTProofException {
        if(!payjoinURI.isPayjoinOutputSubstitutionAllowed()) {
            allowOutputSubstitution = false;
        }

        URI uri = payjoinURI.getPayjoinUrl();
        if(uri == null) {
            log.error("No payjoin URL provided");
            throw new PayjoinReceiverException("No payjoin URL provided");
        }

        long additionalFeeContribution = getAdditionalFeeContribution();

        try {
            String base64Psbt = psbt.getPublicCopy().toBase64String();

            double minFeeRate = AppServices.getMinimumRelayFeeRate();
            String appendQuery = "v=1&minfeerate=" + minFeeRate;
            int changeOutputIndex = getChangeOutputIndex();
            long maxAdditionalFeeContribution = 0;
            if(changeOutputIndex > -1) {
                appendQuery += "&additionalfeeoutputindex=" + changeOutputIndex;
                maxAdditionalFeeContribution = additionalFeeContribution;
                appendQuery += "&maxadditionalfeecontribution=" + maxAdditionalFeeContribution;
            }

            URI finalUri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery() == null ? appendQuery : uri.getQuery() + "&" + appendQuery, uri.getFragment());
            log.info("Sending PSBT to " + finalUri.toURL());

            HttpClientService httpClientService = AppServices.getHttpClientService();
            if(httpClientService.getTorProxy() == null && Protocol.isOnionHost(finalUri.getHost())) {
                throw new PayjoinReceiverException("Configure a Tor proxy to get a payjoin transaction from " + finalUri.getHost() + ".");
            }

            String response = httpClientService.postString(finalUri.toString(), null, "text/plain", base64Psbt);
            PSBT proposalPsbt = PSBT.fromString(response.trim());
            checkProposal(psbt, proposalPsbt, changeOutputIndex, maxAdditionalFeeContribution, minFeeRate, allowOutputSubstitution);

            return proposalPsbt;
        } catch(HttpResponseException e) {
            PayjoinReceiverError payjoinReceiverError = getPayjoinReceiverError(e);
            if(payjoinReceiverError == null) {
                log.warn("Payjoin receiver returned a status of " + e.getStatusCode() + " with an unrecognised body");
                throw new PayjoinReceiverException("The payjoin receiver returned an error (HTTP " + e.getStatusCode() + ").");
            }

            log.warn("Payjoin receiver returned an error of " + payjoinReceiverError.getErrorCode() + " (" + payjoinReceiverError.getMessage() + ")");
            throw new PayjoinReceiverException(payjoinReceiverError.getSafeMessage());
        } catch(URISyntaxException e) {
            log.error("Invalid payjoin receiver URI", e);
            throw new PayjoinReceiverException("Invalid payjoin receiver URI", e);
        } catch(FileNotFoundException e) {
            log.error("Could not find resource at payjoin URL " + uri, e);
            throw new PayjoinReceiverException("Could not find resource at payjoin URL " + uri, e);
        } catch(IOException e) {
            log.error("Payjoin receiver error", e);
            throw new PayjoinReceiverException("Payjoin receiver error", e);
        } catch(PSBTParseException e) {
            log.error("Error parsing received PSBT", e);
            throw new PayjoinReceiverException("Payjoin receiver returned invalid PSBT", e);
        } catch(PayjoinReceiverException e) {
            log.error("Payjoin receiver error", e);
            throw e;
        } catch(Exception e) {
            log.error("Payjoin error", e);
            throw new PayjoinReceiverException("Payjoin error", e);
        }
    }

    void checkProposal(PSBT original, PSBT proposal, int changeOutputIndex, long maxAdditionalFeeContribution, double minFeeRate, boolean allowOutputSubstitution) throws PayjoinReceiverException, PSBTProofException {
        Transaction originalTx = original.getTransaction();
        Queue<Map.Entry<TransactionInput, PSBTInput>> originalInputs = new ArrayDeque<>();
        for(int i = 0; i < original.getPsbtInputs().size(); i++) {
            originalInputs.add(Map.entry(originalTx.getInputs().get(i), original.getPsbtInputs().get(i)));
        }

        Queue<Map.Entry<TransactionOutput, PSBTOutput>> originalOutputs = new ArrayDeque<>();
        for(int i = 0; i < original.getPsbtOutputs().size(); i++) {
            originalOutputs.add(Map.entry(originalTx.getOutputs().get(i), original.getPsbtOutputs().get(i)));
        }

        // Checking that the PSBT of the receiver is clean
        if(!proposal.getExtendedPublicKeys().isEmpty()) {
            throw new PayjoinReceiverException("Global xpubs should not be included in the receiver's PSBT");
        }

        Transaction proposalTx = proposal.getTransaction();
        // Verify that the transaction version, and nLockTime are unchanged.
        if(proposalTx.getVersion() != originalTx.getVersion()) {
            throw new PayjoinReceiverException("The proposal PSBT changed the transaction version");
        }
        if(proposalTx.getLocktime() != originalTx.getLocktime()) {
            throw new PayjoinReceiverException("The proposal PSBT changed the nLocktime");
        }

        Set<Long> sequences = new HashSet<>();
        // For each inputs in the proposal:
        for(PSBTInput proposedPSBTInput : proposal.getPsbtInputs()) {
            if(!proposedPSBTInput.getDerivedPublicKeys().isEmpty() || !proposedPSBTInput.getTapDerivedPublicKeys().isEmpty() || proposedPSBTInput.getTapInternalKey() != null) {
                throw new PayjoinReceiverException("The receiver added keypaths to an input");
            }
            if(!proposedPSBTInput.getPartialSignatures().isEmpty() || proposedPSBTInput.getTapKeyPathSignature() != null) {
                throw new PayjoinReceiverException("The receiver added partial signatures to an input");
            }

            TransactionInput proposedTxIn = proposedPSBTInput.getInput();
            boolean isOriginalInput = !originalInputs.isEmpty() && originalInputs.peek().getKey().getOutpoint().equals(proposedTxIn.getOutpoint());
            if(isOriginalInput) {
                Map.Entry<TransactionInput, PSBTInput> originalInput = originalInputs.remove();
                TransactionInput originalTxIn = originalInput.getKey();

                // Verify that sequence is unchanged.
                if(originalTxIn.getSequenceNumber() != proposedTxIn.getSequenceNumber()) {
                    throw new PayjoinReceiverException("The proposed transaction input modified the sequence of one of the original inputs");
                }
                // Verify the PSBT input is not finalized
                if(proposedPSBTInput.isFinalized()) {
                    throw new PayjoinReceiverException("The receiver finalized one of the original inputs");
                }
                sequences.add(proposedTxIn.getSequenceNumber());

                PSBTInput originalPSBTInput = originalInput.getValue();
                // Fill up the info from the original PSBT input so we can sign and get fees.
                proposedPSBTInput.setNonWitnessUtxo(originalPSBTInput.getNonWitnessUtxo());
                proposedPSBTInput.setWitnessUtxo(originalPSBTInput.getWitnessUtxo());
                // We fill up information we had on the signed PSBT, so we can sign it.
                proposedPSBTInput.getDerivedPublicKeys().putAll(originalPSBTInput.getDerivedPublicKeys());
                proposedPSBTInput.getTapDerivedPublicKeys().putAll(originalPSBTInput.getTapDerivedPublicKeys());
                proposedPSBTInput.setTapInternalKey(originalPSBTInput.getTapInternalKey());
                proposedPSBTInput.getProprietary().putAll(originalPSBTInput.getProprietary());
                proposedPSBTInput.setRedeemScript(originalPSBTInput.getFinalScriptSig() == null ? null : originalPSBTInput.getFinalScriptSig().getFirstNestedScript());
                proposedPSBTInput.setWitnessScript(originalPSBTInput.getFinalScriptWitness() == null ? null : originalPSBTInput.getFinalScriptWitness().getWitnessScript());
                proposedPSBTInput.setSigHash(originalPSBTInput.getSigHash());
            } else {
                // Verify the PSBT input is finalized
                if(!proposedPSBTInput.isFinalized()) {
                    throw new PayjoinReceiverException("The receiver did not finalize one of their inputs");
                }
                // Verify that non_witness_utxo or witness_utxo are filled in.
                if(proposedPSBTInput.getNonWitnessUtxo() == null && proposedPSBTInput.getWitnessUtxo() == null) {
                    throw new PayjoinReceiverException("The receiver did not specify non_witness_utxo or witness_utxo for one of their inputs");
                }
                sequences.add(proposedTxIn.getSequenceNumber());
                // Verify that the payjoin proposal did not introduced mixed inputs' type.
                if(wallet.getScriptType() != proposedPSBTInput.getScriptType()) {
                    throw new PayjoinReceiverException("Proposal script type of " + proposedPSBTInput.getScriptType() + " did not match wallet script type of " + wallet.getScriptType());
                }
            }
        }

        // Verify that all of sender's inputs from the original PSBT are in the proposal.
        if(!originalInputs.isEmpty()) {
            throw new PayjoinReceiverException("Some of the original inputs are not included in the proposal");
        }

        // Verify that the payjoin proposal did not introduced mixed inputs' sequence.
        if(sequences.size() != 1) {
            throw new PayjoinReceiverException("Mixed sequences detected in the proposal");
        }

        Long newFee = proposal.getFee();
        long additionalFee = newFee - original.getFee();
        if(additionalFee < 0) {
            throw new PayjoinReceiverException("The receiver decreased absolute fee");
        }

        TransactionOutput changeOutput = (changeOutputIndex > -1 ? originalTx.getOutputs().get(changeOutputIndex) : null);
        Script paymentScript = payjoinURI.getAddress().getOutputScript();

        // For each outputs in the proposal:
        for(int i = 0; i < proposal.getPsbtOutputs().size(); i++) {
            PSBTOutput proposedPSBTOutput = proposal.getPsbtOutputs().get(i);
            // Verify that no keypaths is in the PSBT output
            if(!proposedPSBTOutput.getDerivedPublicKeys().isEmpty() || !proposedPSBTOutput.getTapDerivedPublicKeys().isEmpty() || proposedPSBTOutput.getTapInternalKey() != null) {
                throw new PayjoinReceiverException("The receiver added keypaths to an output");
            }

            TransactionOutput proposedTxOut = proposalTx.getOutputs().get(i);
            Map.Entry<TransactionOutput, PSBTOutput> originalOutput = originalOutputs.peek();
            boolean isOriginalOutput = originalOutput != null && originalOutput.getKey().getScript().equals(proposedTxOut.getScript());
            boolean isPaymentOutput = originalOutput != null && originalOutput.getKey().getScript().equals(paymentScript);
            // The receiver may have substituted the payment output with one paying to a different script
            boolean isSubstitutedOutput = !isOriginalOutput && isPaymentOutput && allowOutputSubstitution;

            if(isOriginalOutput || isSubstitutedOutput) {
                originalOutputs.remove();
                if(originalOutput.getKey() == changeOutput) {
                    var actualContribution = originalOutput.getKey().getValue() - proposedTxOut.getValue();
                    // The amount that was subtracted from the output's value is less than or equal to maxadditionalfeecontribution
                    if(actualContribution > maxAdditionalFeeContribution) {
                        throw new PayjoinReceiverException("The actual contribution is more than maxadditionalfeecontribution");
                    }
                    // Make sure the actual contribution is only paying fee
                    if(actualContribution > additionalFee) {
                        throw new PayjoinReceiverException("The actual contribution is not only paying fee");
                    }
                    // Make sure the actual contribution is only paying for fee incurred by additional inputs
                    int additionalInputsCount = proposalTx.getInputs().size() - originalTx.getInputs().size();
                    if(actualContribution > getSingleInputFee() * additionalInputsCount) {
                        throw new PayjoinReceiverException("The actual contribution is not only paying for additional inputs");
                    }
                } else if(allowOutputSubstitution && isPaymentOutput) {
                    // That's the payment output, the receiver may have changed it.
                } else {
                    if(originalOutput.getKey().getValue() > proposedTxOut.getValue()) {
                        throw new PayjoinReceiverException("The receiver decreased the value of one of the outputs from " + originalOutput.getKey().getValue() + " sats to " + proposedTxOut.getValue() + " sats");
                    }
                }

                if(isOriginalOutput) {
                    PSBTOutput originalPSBTOutput = originalOutput.getValue();
                    // We fill up information we had on the signed PSBT, so we can sign it. A substituted output pays to a different script, so this information does not apply to it.
                    proposedPSBTOutput.getDerivedPublicKeys().putAll(originalPSBTOutput.getDerivedPublicKeys());
                    proposedPSBTOutput.getTapDerivedPublicKeys().putAll(originalPSBTOutput.getTapDerivedPublicKeys());
                    proposedPSBTOutput.setTapInternalKey(originalPSBTOutput.getTapInternalKey());
                    proposedPSBTOutput.getProprietary().putAll(originalPSBTOutput.getProprietary());
                    proposedPSBTOutput.setRedeemScript(originalPSBTOutput.getRedeemScript());
                    proposedPSBTOutput.setWitnessScript(originalPSBTOutput.getWitnessScript());
                }
            }
        }

        // Verify that all of sender's outputs from the original PSBT are in the proposal.
        if(!originalOutputs.isEmpty()) {
            // The payment output may have been removed without being substituted
            if(!allowOutputSubstitution || originalOutputs.size() != 1 || !originalOutputs.remove().getKey().getScript().equals(paymentScript)) {
                throw new PayjoinReceiverException("Some of our outputs are not included in the proposal");
            }
        }

        // Once signed, the fee rate of the payjoin transaction must not be less than the minfeerate we requested
        double proposalFeeRate = getProposalFeeRate(original, proposal);
        if(proposalFeeRate < minFeeRate) {
            throw new PayjoinReceiverException("The fee rate of the payjoin transaction of " + String.format("%.2f", proposalFeeRate) + " sats/vB is less than the requested minimum of " + String.format("%.2f", minFeeRate) + " sats/vB");
        }

        //Add global pubkey map for signing
        proposal.getExtendedPublicKeys().putAll(psbt.getExtendedPublicKeys());
        proposal.getGlobalProprietary().putAll(psbt.getGlobalProprietary());
    }

    /**
     * Estimates the fee rate of the payjoin transaction once the sender's inputs have been signed.
     * The extracted proposal transaction already carries the receiver's finalized inputs, so the weight the sender still has to add
     * is the difference between the signed and unsigned forms of the original transaction.
     */
    private double getProposalFeeRate(PSBT original, PSBT proposal) throws PSBTProofException {
        Transaction signedOriginalTx = original.extractTransaction();
        Transaction finalizedProposalTx = proposal.extractTransaction();
        int signedWeightUnits = signedOriginalTx.getWeightUnits() - original.getTransaction().getWeightUnits();
        if(signedOriginalTx.isSegwit() && finalizedProposalTx.isSegwit()) {
            //Both transactions include the segwit marker and flag, so don't count them twice
            signedWeightUnits -= 2;
        }

        double vSize = (double)(finalizedProposalTx.getWeightUnits() + signedWeightUnits) / Transaction.WITNESS_SCALE_FACTOR;

        return proposal.getFee().doubleValue() / vSize;
    }

    private int getChangeOutputIndex() {
        Map<Script, WalletNode> changeScriptNodes = wallet.getWalletOutputScripts(wallet.getChangeKeyPurpose());
        for(int i = 0; i < psbt.getTransaction().getOutputs().size(); i++) {
            if(changeScriptNodes.containsKey(psbt.getTransaction().getOutputs().get(i).getScript())) {
                return i;
            }
        }

        return -1;
    }

    private long getAdditionalFeeContribution() throws PSBTProofException {
        return getSingleInputFee();
    }

    private long getSingleInputFee() throws PSBTProofException {
        Transaction transaction = psbt.extractTransaction();
        double feeRate = psbt.getFee().doubleValue() / transaction.getVirtualSize();
        int vSize = 68;

        if(!transaction.getInputs().isEmpty()) {
            TransactionInput input = transaction.getInputs().getFirst();
            vSize = input.getLength() * Transaction.WITNESS_SCALE_FACTOR;
            vSize += input.getWitness() != null ? input.getWitness().getLength() : 0;
            vSize = (int)Math.ceil((double)vSize / Transaction.WITNESS_SCALE_FACTOR);
        }

        return (long) (vSize * feeRate);
    }

    private PayjoinReceiverError getPayjoinReceiverError(HttpResponseException e) {
        try {
            return new Gson().fromJson(e.getResponseBody(), PayjoinReceiverError.class);
        } catch(JsonSyntaxException jse) {
            return null;
        }
    }

    private static class PayjoinReceiverError {
        //Must be static so it cannot be overridden by the deserialized receiver response
        private static final Map<String, String> KNOWN_ERRORS = ImmutableMap.of(
                "unavailable", "The payjoin endpoint is not available for now.",
                "not-enough-money", "The receiver added some inputs but could not bump the fee of the payjoin proposal.",
                "version-unsupported", "This version of payjoin is not supported.",
                "original-psbt-rejected", "The receiver rejected the original PSBT."
        );

        public String errorCode;
        public String message;

        public String getErrorCode() {
            return errorCode;
        }

        public String getMessage() {
            return message;
        }

        public String getSafeMessage() {
            String message = KNOWN_ERRORS.get(errorCode);
            return (message == null ? "Unknown Error" : message);
        }
    }

    public static class RequestPayjoinPSBTService extends Service<PSBT> {
        private final Payjoin payjoin;
        private final boolean allowOutputSubstitution;

        public RequestPayjoinPSBTService(Payjoin payjoin, boolean allowOutputSubstitution) {
            this.payjoin = payjoin;
            this.allowOutputSubstitution = allowOutputSubstitution;
        }

        @Override
        protected Task<PSBT> createTask() {
            return new Task<>() {
                protected PSBT call() throws PayjoinReceiverException, PSBTProofException {
                    return payjoin.requestPayjoinPSBT(allowOutputSubstitution);
                }
            };
        }
    }
}
