package com.sparrowwallet.sparrow.joinstr;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.BnBUtxoSelector;
import com.sparrowwallet.drongo.wallet.CoinbaseTxoFilter;
import com.sparrowwallet.drongo.wallet.FrozenTxoFilter;
import com.sparrowwallet.drongo.wallet.InsufficientFundsException;
import com.sparrowwallet.drongo.wallet.KnapsackUtxoSelector;
import com.sparrowwallet.drongo.wallet.Payment;
import com.sparrowwallet.drongo.wallet.SpentTxoFilter;
import com.sparrowwallet.drongo.wallet.StonewallUtxoSelector;
import com.sparrowwallet.drongo.wallet.TxoFilter;
import com.sparrowwallet.drongo.wallet.UtxoSelector;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.wallet.WalletTransaction;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.QRScanDialog;
import com.sparrowwallet.sparrow.io.WalletTransactions;
import com.sparrowwallet.sparrow.net.Tor;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import com.sparrowwallet.sparrow.wallet.OptimizationStrategy;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import com.sparrowwallet.sparrow.wallet.WalletUtxosEntry;

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

    public void createPSBT() throws InsufficientFundsException {

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

        //  PSBT from WalletTransaction

        Address sendAddress =  getNewSendAddress();
        Wallet wallet = walletForm.getWallet();
        double feeRate = 1.0;
        double longTermFeeRate = 10.0;
        long fee = 10L;
        long dustThreshold = getRecipientDustThreshold(sendAddress);
        long amount = wallet.getWalletTxos().keySet().stream().mapToLong(BlockTransactionHashIndex::getValue).sum();
        long satsLeft = amount;

        String paymentLabel = "coinjoin";

        ArrayList<Payment> payments = new ArrayList<Payment>();
        while(satsLeft > denomination + dustThreshold) {
            Payment payment = new Payment(sendAddress, paymentLabel, denomination, false);
            satsLeft -= denomination;
            payment.setType(Payment.Type.COINJOIN);
            payments.add(payment);
        }

        List<UtxoSelector> selectors = new ArrayList<>();
        long noInputsFee = wallet.getNoInputsFee(payments, feeRate);
        long costOfChange = wallet.getCostOfChange(feeRate, longTermFeeRate);
        selectors.addAll(List.of(new BnBUtxoSelector(noInputsFee, costOfChange), new KnapsackUtxoSelector(noInputsFee)));

        SpentTxoFilter spentTxoFilter = new SpentTxoFilter(null);
        List<TxoFilter> txoFilters = List.of(spentTxoFilter, new FrozenTxoFilter(), new CoinbaseTxoFilter(walletForm.getWallet()));

        ArrayList<byte[]> opReturns = new ArrayList<>();
        TreeSet<WalletNode> excludedChangeNodes = new TreeSet<>();

        Integer currentBlockHeight = AppServices.getCurrentBlockHeight();
        boolean groupByAddress = false;
        boolean includeMempoolOutputs = false;

        WalletTransaction walletTransaction = walletForm.getWallet().createWalletTransaction(selectors, txoFilters, payments, opReturns, excludedChangeNodes, feeRate, longTermFeeRate, fee, currentBlockHeight, groupByAddress, includeMempoolOutputs);
        PSBT psbt = walletTransaction.createPSBT();


    }

    private long getRecipientDustThreshold(Address address) {
        TransactionOutput txOutput = new TransactionOutput(new Transaction(), 1L, address.getOutputScript());
        return address.getScriptType().getDustThreshold(txOutput, Transaction.DUST_RELAY_TX_FEE);
    }

}
