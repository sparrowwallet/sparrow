package com.sparrowwallet.sparrow.payjoin;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.Script;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.psbt.PSBTOutput;
import com.sparrowwallet.drongo.psbt.PSBTParseException;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.nightjar.http.JavaHttpException;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.net.HttpClientService;
import com.sparrowwallet.sparrow.net.Protocol;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
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
        this.psbt = psbt;

        for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
            if(psbtInput.getUtxo() == null) {
                throw new IllegalArgumentException("Original PSBT for payjoin transaction must have non_witness_utxo or witness_utxo fields for all inputs");
            }
        }

        if(!psbt.isFinalized()) {
            throw new IllegalArgumentException("Original PSBT for payjoin transaction must be finalized");
        }
    }

    public PSBT requestPayjoinPSBT(boolean allowOutputSubstitution) throws PayjoinReceiverException {
        if(!payjoinURI.isPayjoinOutputSubstitutionAllowed()) {
            allowOutputSubstitution = false;
        }

        URI uri = payjoinURI.getPayjoinUrl();
        if(uri == null) {
            log.error("No payjoin URL provided");
            throw new PayjoinReceiverException("No payjoin URL provided");
        }

        try {
            String base64Psbt = psbt.getPublicCopy().toBase64String();

            String appendQuery = "v=1&minfeerate=" + AppServices.getMinimumRelayFeeRate();
            int changeOutputIndex = getChangeOutputIndex();
            long maxAdditionalFeeContribution = 0;
            if(changeOutputIndex > -1) {
                appendQuery += "&additionalfeeoutputindex=" + changeOutputIndex;
                maxAdditionalFeeContribution = getAdditionalFeeContribution();
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
            checkProposal(psbt, proposalPsbt, changeOutputIndex, maxAdditionalFeeContribution, allowOutputSubstitution);

            return proposalPsbt;
        } catch(JavaHttpException e) {
            Gson gson = new Gson();
            PayjoinReceiverError payjoinReceiverError = gson.fromJson(e.getResponseBody(), PayjoinReceiverError.class);
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
        } catch(Exception e) {
            log.error("Payjoin error", e);
            throw new PayjoinReceiverException("Payjoin error", e);
        }
    }

    private void checkProposal(PSBT original, PSBT proposal, int changeOutputIndex, long maxAdditionalFeeContribution, boolean allowOutputSubstitution) throws PayjoinReceiverException {
        Queue<Map.Entry<TransactionInput, PSBTInput>> originalInputs = new ArrayDeque<>();
        for(int i = 0; i < original.getPsbtInputs().size(); i++) {
            originalInputs.add(Map.entry(original.getTransaction().getInputs().get(i), original.getPsbtInputs().get(i)));
        }

        Queue<Map.Entry<TransactionOutput, PSBTOutput>> originalOutputs = new ArrayDeque<>();
        for(int i = 0; i < original.getPsbtOutputs().size(); i++) {
            originalOutputs.add(Map.entry(original.getTransaction().getOutputs().get(i), original.getPsbtOutputs().get(i)));
        }

        // Checking that the PSBT of the receiver is clean
        if(!proposal.getExtendedPublicKeys().isEmpty()) {
            throw new PayjoinReceiverException("Global xpubs should not be included in the receiver's PSBT");
        }

        Transaction originalTx = original.getTransaction();
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
            if(!proposedPSBTInput.getDerivedPublicKeys().isEmpty()) {
                throw new PayjoinReceiverException("The receiver added keypaths to an input");
            }
            if(!proposedPSBTInput.getPartialSignatures().isEmpty()) {
                throw new PayjoinReceiverException("The receiver added partial signatures to an input");
            }

            TransactionInput proposedTxIn = proposedPSBTInput.getInput();
            boolean isOriginalInput = originalInputs.size() > 0 && originalInputs.peek().getKey().getOutpoint().equals(proposedTxIn.getOutpoint());
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
                // Verify that non_witness_utxo and witness_utxo are not specified.
                if(proposedPSBTInput.getNonWitnessUtxo() != null || proposedPSBTInput.getWitnessUtxo() != null) {
                    throw new PayjoinReceiverException("The receiver added non_witness_utxo or witness_utxo to one of the original inputs");
                }
                sequences.add(proposedTxIn.getSequenceNumber());

                PSBTInput originalPSBTInput = originalInput.getValue();
                // Fill up the info from the original PSBT input so we can sign and get fees.
                proposedPSBTInput.setNonWitnessUtxo(originalPSBTInput.getNonWitnessUtxo());
                proposedPSBTInput.setWitnessUtxo(originalPSBTInput.getWitnessUtxo());
                // We fill up information we had on the signed PSBT, so we can sign it.
                proposedPSBTInput.getDerivedPublicKeys().putAll(originalPSBTInput.getDerivedPublicKeys());
                proposedPSBTInput.getProprietary().putAll(originalPSBTInput.getProprietary());
                proposedPSBTInput.setRedeemScript(originalPSBTInput.getFinalScriptSig().getFirstNestedScript());
                proposedPSBTInput.setWitnessScript(originalPSBTInput.getFinalScriptWitness().getWitnessScript());
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

        // For each outputs in the proposal:
        for(int i = 0; i < proposal.getPsbtOutputs().size(); i++) {
            PSBTOutput proposedPSBTOutput = proposal.getPsbtOutputs().get(i);
            // Verify that no keypaths is in the PSBT output
            if(!proposedPSBTOutput.getDerivedPublicKeys().isEmpty()) {
                throw new PayjoinReceiverException("The receiver added keypaths to an output");
            }

            TransactionOutput proposedTxOut = proposalTx.getOutputs().get(i);
            boolean isOriginalOutput = originalOutputs.size() > 0 && originalOutputs.peek().getKey().getScript().equals(proposedTxOut.getScript());
            if(isOriginalOutput) {
                Map.Entry<TransactionOutput, PSBTOutput> originalOutput = originalOutputs.remove();
                if(originalOutput.getKey() == changeOutput) {
                    var actualContribution = changeOutput.getValue() - proposedTxOut.getValue();
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
                } else if(allowOutputSubstitution && originalOutput.getKey().getScript().equals(payjoinURI.getAddress().getOutputScript())) {
                    // That's the payment output, the receiver may have changed it.
                } else {
                    if(originalOutput.getKey().getValue() > proposedTxOut.getValue()) {
                        throw new PayjoinReceiverException("The receiver decreased the value of one of the outputs");
                    }
                }

                PSBTOutput originalPSBTOutput = originalOutput.getValue();
                // We fill up information we had on the signed PSBT, so we can sign it.
                proposedPSBTOutput.getDerivedPublicKeys().putAll(originalPSBTOutput.getDerivedPublicKeys());
                proposedPSBTOutput.getProprietary().putAll(originalPSBTOutput.getProprietary());
                proposedPSBTOutput.setRedeemScript(originalPSBTOutput.getRedeemScript());
                proposedPSBTOutput.setWitnessScript(originalPSBTOutput.getWitnessScript());
            }
        }

        // Verify that all of sender's outputs from the original PSBT are in the proposal.
        if(!originalOutputs.isEmpty()) {
            // The payment output may have been substituted
            if(!allowOutputSubstitution || originalOutputs.size() != 1 || !originalOutputs.remove().getKey().getScript().equals(payjoinURI.getAddress().getOutputScript())) {
                throw new PayjoinReceiverException("Some of our outputs are not included in the proposal");
            }
        }

        //Add global pubkey map for signing
        proposal.getExtendedPublicKeys().putAll(psbt.getExtendedPublicKeys());
        proposal.getGlobalProprietary().putAll(psbt.getGlobalProprietary());
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

    private long getAdditionalFeeContribution() {
        return getSingleInputFee();
    }

    private long getSingleInputFee() {
        Transaction transaction = psbt.extractTransaction();
        double feeRate = psbt.getFee().doubleValue() / transaction.getVirtualSize();
        int vSize = 68;

        if(transaction.getInputs().size() > 0) {
            TransactionInput input = transaction.getInputs().get(0);
            vSize = input.getLength() * Transaction.WITNESS_SCALE_FACTOR;
            vSize += input.getWitness() != null ? input.getWitness().getLength() : 0;
            vSize = (int)Math.ceil((double)vSize / Transaction.WITNESS_SCALE_FACTOR);
        }

        return (long) (vSize * feeRate);
    }

    private static class PayjoinReceiverError {
        Map<String, String> knownErrors = ImmutableMap.of(
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
            String message = knownErrors.get(errorCode);
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
                protected PSBT call() throws PayjoinReceiverException {
                    return payjoin.requestPayjoinPSBT(allowOutputSubstitution);
                }
            };
        }
    }
}
