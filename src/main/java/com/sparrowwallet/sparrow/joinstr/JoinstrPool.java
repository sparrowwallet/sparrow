package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.CoinbaseTxoFilter;
import com.sparrowwallet.drongo.wallet.FrozenTxoFilter;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.SpentTxoFilter;
import com.sparrowwallet.drongo.wallet.TxoFilter;
import com.sparrowwallet.drongo.wallet.UtxoSelector;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.QRScanDialog;
import com.sparrowwallet.sparrow.net.Tor;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import com.sparrowwallet.sparrow.wallet.WalletForm;

import java.util.*;

public class JoinstrPool {

    private final String relay;
    private final Integer port;
    private final String pubkey;
    private final long denomination;        // Should be in sats, like transaction amounts
    private final WalletForm walletForm;    // [FormController].getWalletForm()

    public JoinstrPool(WalletForm walletForm,String relay, Integer port, String pubkey, long denomination) {

        this.walletForm = walletForm;
        this.relay = relay;
        this.port = port;
        this.pubkey = pubkey;
        this.denomination = denomination;

    }

    public String getRelay() {
        return relay;
    }

    public Integer getPort() {
        return port;
    }

    public String getPubkey() {
        return pubkey;
    }

    public long getDenomination() {
        return denomination;
    }

    public void getNewTorRoute() {

        Tor tor = Tor.getDefault();
        tor.changeIdentity();

    }

    private Address getNewSendAddress() {
        NodeEntry freshNodeEntry = walletForm.getFreshNodeEntry(KeyPurpose.SEND, null);
        return freshNodeEntry.getAddress();
    }

    public void createPSBT() {

        Address sendAddress =  getNewSendAddress();

        /* String to byte[]

            if(Utils.isBase64(string) && !Utils.isHex(string)) {
                addTransactionTab(name, file, Base64.getDecoder().decode(string));
            } else if(Utils.isHex(string)) {
                addTransactionTab(name, file, Utils.hexToBytes(string));
            } else {
                throw new ParseException("Input is not base64 or hex", 0);
            }

        */

        /* PSBT from byte[]

            if(PSBT.isPSBT(bytes)) {
                //Don't verify signatures here - provided PSBT may omit UTXO data that can be found when combining with an existing PSBT
                PSBT psbt = new PSBT(bytes, false);
            } else {
                throw new ParseException("Not a valid PSBT or transaction", 0);
            }

        */

        /* PSBT from QR Code

             QRScanDialog qrScanDialog = new QRScanDialog();
             qrScanDialog.initOwner(rootStack.getScene().getWindow());
             Optional<QRScanDialog.Result> optionalResult = qrScanDialog.showAndWait();
             QRScanDialog.Result result = optionalResult.get();
             result.psbt;
        */

        /* PSBT from TransactionData
            TransactionData txdata;
        /// TODO fill txdata
            psbt = txdata.getPsbt();
         */

        //  PSBT from WalletTransaction

            List<UtxoSelector> utxoSelectors;
        /// TODO select UTXO for transaction

            SpentTxoFilter spentTxoFilter = new SpentTxoFilter(null);
            List<TxoFilter> txoFilters = List.of(spentTxoFilter, new FrozenTxoFilter(), new CoinbaseTxoFilter(walletForm.getWallet()));

            long satsLeft = 0L;
            Payment coinjoinPayment = new Payment(sendAddress, "coinjoin", denomination, false);
        /// TODO create multiple coinjoin payments for selected UTXO
            Payment changePayment = new Payment(sendAddress, "change", satsLeft, false);
            List<Payment> payments = List.of(coinjoinPayment, changePayment);

            List<byte[]> opReturns = null;

            Set<WalletNode> excludedChangeNodes;
            /// TODO fill excludedChangeNodes

            double feeRate = 10.0;
            double longTermFeeRate = 10.0;
            Long fee = 10L;
            Integer currentBlockHeight = AppServices.getCurrentBlockHeight();
            boolean groupByAddress = false;
            boolean includeMempoolOutputs = false;

            WalletTransaction toSign = walletForm.getWallet().createWalletTransaction(utxoSelectors, txoFilters, payments, opReturns, excludedChangeNodes, feeRate, longTermFeeRate, fee, currentBlockHeight, groupByAddress, includeMempoolOutputs);
            PSBT psbt = toSign.createPSBT();

         // decryptedWallet.sign(psbt);
         // decryptedWallet.finalise(psbt);
         // Transaction transaction = psbt.extractTransaction();

        /* PSBT from Transaction


            PSBT psbt = new PSBT(toSign);
            PSBTInput psbtInput = psbt.getPsbtInputs().get(0);

            Transaction toSpend;
            /// TODO fill toSpend
            TransactionOutput utxoOutput = toSpend.getOutputs().get(0);
            psbtInput.setWitnessUtxo(utxoOutput);

            psbtInput.setSigHash(SigHash.ALL);
            psbtInput.sign(scriptType.getOutputKey(privKey));

         */

    }

}
