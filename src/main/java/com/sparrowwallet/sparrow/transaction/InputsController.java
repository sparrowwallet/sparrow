package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.sparrow.control.CopyableLabel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class InputsController extends TransactionFormController implements Initializable {
    private InputsForm inputsForm;

    @FXML
    private CopyableLabel count;

    @FXML
    private CopyableLabel total;

    @FXML
    private CopyableLabel signatures;

    @FXML
    private PieChart inputsPie;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void setModel(InputsForm form) {
        this.inputsForm = form;
        initialiseView();
    }

    private void initialiseView() {
        Transaction tx = inputsForm.getTransaction();
        count.setText(Integer.toString(tx.getInputs().size()));

        total.setText("Unknown");
        signatures.setText("Unknown");

        if(inputsForm.getPsbt() != null) {
            int reqSigs = 0;
            int foundSigs = 0;
            boolean showDenominator = true;

            List<TransactionOutput> outputs = new ArrayList<>();
            for(int i = 0; i < tx.getInputs().size(); i++) {
                TransactionInput input = tx.getInputs().get(i);
                PSBTInput psbtInput = inputsForm.getPsbt().getPsbtInputs().get(i);

                if(psbtInput.getNonWitnessUtxo() != null) {
                    outputs.add(psbtInput.getNonWitnessUtxo().getOutputs().get((int)input.getOutpoint().getIndex()));
                } else if(psbtInput.getWitnessUtxo() != null) {
                    outputs.add(psbtInput.getWitnessUtxo());
                }

                if((psbtInput.getNonWitnessUtxo() != null || psbtInput.getWitnessUtxo() != null) && psbtInput.getSigningScript() != null) {
                    try {
                        reqSigs += psbtInput.getSigningScript().getNumRequiredSignatures();
                    } catch (NonStandardScriptException e) {
                        showDenominator = false;
                        //TODO: Handle unusual transaction sig
                    }
                } else {
                    showDenominator = false;
                }

                if(psbtInput.getFinalScriptWitness() != null) {
                    foundSigs += psbtInput.getFinalScriptWitness().getSignatures().size();
                } else if(psbtInput.getFinalScriptSig() != null) {
                    foundSigs += psbtInput.getFinalScriptSig().getSignatures().size();
                } else {
                    foundSigs += psbtInput.getPartialSignatures().size();
                }
            }

            long totalAmt = 0;
            for(TransactionOutput output : outputs) {
                totalAmt += output.getValue();
            }
            total.setText(totalAmt + " sats");
            if(showDenominator) {
                signatures.setText(foundSigs + "/" + reqSigs);
            } else {
                signatures.setText(foundSigs + "/?");
            }

            addPieData(inputsPie, outputs);
        }
    }
}
