package com.craigraw.sparrow;

import com.craigraw.drongo.psbt.PSBT;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.bouncycastle.util.encoders.Hex;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class AppController implements Initializable {

    @FXML
    private TabPane tabs;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void open(ActionEvent event) {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(window);
        if (file != null) {
            System.out.println(file);
            //openFile(file);

            if(file.exists()) {
                try {
                    byte[] bytes = new byte[(int)file.length()];
                    FileInputStream stream = new FileInputStream(file);
                    stream.read(bytes);
                    stream.close();

                    if(PSBT.isPSBT(Hex.toHexString(bytes))) {
                        PSBT psbt = new PSBT(bytes);

                        Tab tab = new Tab(file.getName());
                        FXMLLoader transactionLoader = new FXMLLoader(getClass().getResource("transaction.fxml"));
                        tab.setContent(transactionLoader.load());
                        TransactionController controller = transactionLoader.getController();
                        controller.setPSBT(psbt);

                        tabs.getTabs().add(tab);
                    }
                } catch(IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
