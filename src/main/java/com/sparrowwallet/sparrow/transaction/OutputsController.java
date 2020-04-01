package com.sparrowwallet.sparrow.transaction;

import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

public class OutputsController extends TransactionFormController implements Initializable {
    private OutputsForm outputsForm;

    @FXML
    private TextField count;

    @FXML
    private TextField total;

    @FXML
    private PieChart outputsPie;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void setModel(OutputsForm form) {
        this.outputsForm = form;
        initialiseView();
    }

    private void initialiseView() {
        Transaction tx = outputsForm.getTransaction();
        count.setText(Integer.toString(tx.getOutputs().size()));

        long totalAmt = 0;
        for(TransactionOutput output : tx.getOutputs()) {
            totalAmt += output.getValue();
        }
        total.setText(totalAmt + " sats");

        if(totalAmt > 0) {
            addPieData(outputsPie, tx.getOutputs());
        }
    }
}
