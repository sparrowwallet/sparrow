package com.craigraw.sparrow.form;

import com.craigraw.drongo.protocol.*;
import com.craigraw.drongo.psbt.PSBTInput;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class InputsController extends FormController implements Initializable {
    private InputsForm inputsForm;

    @FXML
    private TextField count;

    @FXML
    private TextField total;

    @FXML
    private TextField signatures;

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

            List<TransactionOutput> outputs = new ArrayList<>();
            for(int i = 0; i < tx.getInputs().size(); i++) {
                TransactionInput input = tx.getInputs().get(i);
                PSBTInput psbtInput = inputsForm.getPsbt().getPsbtInputs().get(i);

                if(psbtInput.getNonWitnessUtxo() != null) {
                    outputs.add(psbtInput.getNonWitnessUtxo().getOutputs().get((int)input.getOutpoint().getIndex()));
                } else if(psbtInput.getWitnessUtxo() != null) {
                    outputs.add(psbtInput.getWitnessUtxo());
                }

                try {
                    reqSigs += psbtInput.getSigningScript().getNumRequiredSignatures();
                    foundSigs += psbtInput.getPartialSignatures().size();
                } catch (NonStandardScriptException e) {
                    //TODO: Handle unusual transaction sig
                }
            }

            long totalAmt = 0;
            for(TransactionOutput output : outputs) {
                totalAmt += output.getValue();
            }
            total.setText(totalAmt + " sats");
            signatures.setText(foundSigs + "/" + reqSigs);
            addPieData(inputsPie, outputs);
        }
    }
}
