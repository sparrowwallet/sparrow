package com.sparrowwallet.sparrow.transaction;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.CopyableCoinLabel;
import com.sparrowwallet.sparrow.control.CopyableLabel;
import com.sparrowwallet.sparrow.event.*;
import javafx.collections.MapChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class InputsController extends TransactionFormController implements Initializable {
    private InputsForm inputsForm;

    @FXML
    private CopyableLabel count;

    @FXML
    private CopyableCoinLabel total;

    @FXML
    private CopyableLabel signatures;

    @FXML
    private PieChart inputsPie;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    public void setModel(InputsForm form) {
        this.inputsForm = form;
        initialiseView();
    }

    @Override
    protected TransactionForm getTransactionForm() {
        return inputsForm;
    }

    private void initialiseView() {
        Transaction tx = inputsForm.getTransaction();
        count.setText(Integer.toString(tx.getInputs().size()));

        signatures.setText("Unknown");

        if(inputsForm.getPsbt() != null) {
            updatePSBTInputs(inputsForm.getPsbt());
            inputsForm.getSignatureKeystoreMap().addListener((MapChangeListener<TransactionSignature, Keystore>) c -> {
                updatePSBTInputs(inputsForm.getPsbt());
            });
        } else if(inputsForm.getInputTransactions() != null) {
            updateBlockTransactionInputs(inputsForm.getInputTransactions());
        }
    }

    private void updatePSBTInputs(PSBT psbt) {
        int reqSigs = 0;
        int foundSigs = 0;
        boolean showDenominator = true;

        List<TransactionOutput> outputs = new ArrayList<>();
        for(PSBTInput psbtInput : psbt.getPsbtInputs()) {
            TransactionOutput output = psbtInput.getUtxo();
            if(output != null) {
                outputs.add(output);
            }

            if(psbtInput.getUtxo() != null && psbtInput.getSigningScript() != null) {
                try {
                    reqSigs += psbtInput.getSigningScript().getNumRequiredSignatures();
                } catch (NonStandardScriptException e) {
                    showDenominator = false;
                    //TODO: Handle unusual transaction sig
                }
            } else {
                showDenominator = false;
            }

            foundSigs += psbtInput.getSignatures().size();
        }

        long totalAmt = 0;
        for(TransactionOutput output : outputs) {
            totalAmt += output.getValue();
        }
        total.setValue(totalAmt);
        if(showDenominator) {
            signatures.setText(foundSigs + "/" + reqSigs);
        } else {
            signatures.setText(foundSigs + "/?");
        }

        addPieData(inputsPie, outputs);
    }

    private void updateBlockTransactionInputs(Map<Sha256Hash, BlockTransaction> inputTransactions) {
        List<TransactionOutput> outputs = new ArrayList<>();

        int foundSigs = 0;
        for(TransactionInput input : inputsForm.getTransaction().getInputs()) {
            if(input.hasWitness()) {
                foundSigs += input.getWitness().getSignatures().size();
            } else {
                foundSigs += input.getScriptSig().getSignatures().size();
            }

            if(input.isCoinBase()) {
                long totalAmt = 0;
                for(TransactionOutput output : inputsForm.getTransaction().getOutputs()) {
                    totalAmt += output.getValue();
                }
                total.setValue(totalAmt);
                signatures.setText("N/A");
                addCoinbasePieData(inputsPie, totalAmt);
                return;
            } else {
                BlockTransaction inputTx = inputTransactions.get(input.getOutpoint().getHash());
                if(inputTx == null) {
                    inputTx = inputsForm.getInputTransactions().get(input.getOutpoint().getHash());
                }

                if(inputTx == null) {
                    if(inputsForm.allInputsFetched()) {
                        throw new IllegalStateException("Cannot find transaction for hash " + input.getOutpoint().getHash());
                    } else {
                        //Still paging
                        total.setText("Unknown (" + inputsForm.getMaxInputFetched() + " of " + inputsForm.getTransaction().getInputs().size() + " inputs fetched)");
                        return;
                    }
                }

                TransactionOutput output = inputTx.getTransaction().getOutputs().get((int)input.getOutpoint().getIndex());
                outputs.add(output);
            }
        }

        long totalAmt = 0;
        for(TransactionOutput output : outputs) {
            totalAmt += output.getValue();
        }
        total.setValue(totalAmt);

        //TODO: Find signing script and get required num sigs
        signatures.setText(foundSigs + "/" + foundSigs);
        addPieData(inputsPie, outputs);
    }

    @Subscribe
    public void blockTransactionFetched(BlockTransactionFetchedEvent event) {
        if(event.getTxId().equals(inputsForm.getTransaction().getTxId()) && !event.getInputTransactions().isEmpty() && inputsForm.getPsbt() == null) {
            updateBlockTransactionInputs(event.getInputTransactions());
        }
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        total.refresh(event.getUnitFormat(), event.getBitcoinUnit());
    }

    @Subscribe
    public void psbtCombined(PSBTCombinedEvent event) {
        if(event.getPsbt().equals(inputsForm.getPsbt())) {
            updatePSBTInputs(inputsForm.getPsbt());
        }
    }

    @Subscribe
    public void psbtFinalized(PSBTFinalizedEvent event) {
        if(event.getPsbt().equals(inputsForm.getPsbt())) {
            updatePSBTInputs(inputsForm.getPsbt());
        }
    }
}
