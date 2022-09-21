package com.sparrowwallet.sparrow.transaction;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.CopyableCoinLabel;
import com.sparrowwallet.sparrow.control.CopyableLabel;
import com.sparrowwallet.sparrow.event.UnitFormatChangedEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;

import java.net.URL;
import java.util.ResourceBundle;

public class OutputsController extends TransactionFormController implements Initializable {
    private OutputsForm outputsForm;

    @FXML
    private CopyableLabel count;

    @FXML
    private CopyableCoinLabel total;

    @FXML
    private PieChart outputsPie;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
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
        total.setValue(totalAmt);

        if(totalAmt > 0) {
            addPieData(outputsPie, tx.getOutputs());
        }
    }

    @Override
    protected TransactionForm getTransactionForm() {
        return outputsForm;
    }

    @Subscribe
    public void unitFormatChanged(UnitFormatChangedEvent event) {
        total.refresh(event.getUnitFormat(), event.getBitcoinUnit());
    }
}
