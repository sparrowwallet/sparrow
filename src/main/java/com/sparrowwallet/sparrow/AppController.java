package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTParseException;
import com.sparrowwallet.sparrow.transaction.TransactionController;
import com.sparrowwallet.sparrow.transaction.TransactionListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

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

    void initializeView() {
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, old_val, new_val) -> {
            String tabName = new_val.getText();
            if(tabs.getScene() != null) {
                Stage tabStage = (Stage)tabs.getScene().getWindow();
                tabStage.setTitle("Sparrow - " + tabName);
            }
        });

        addExampleTxTabs();
    }

    public void open(ActionEvent event) {
        Stage window = new Stage();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Transaction");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("PSBT", "*.psbt"),
                new FileChooser.ExtensionFilter("TXN", "*.txn")
        );

        File file = fileChooser.showOpenDialog(window);
        if (file != null) {
            if(file.exists()) {
                try {
                    byte[] bytes = new byte[(int)file.length()];
                    FileInputStream stream = new FileInputStream(file);
                    stream.read(bytes);
                    stream.close();

                    Tab tab;
                    if(PSBT.isPSBT(bytes)) {
                        try {
                            PSBT psbt = new PSBT(bytes);
                            tab = addTransactionTab(file.getName(), null, psbt);
                        } catch(PSBTParseException e) {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("PSBT File Invalid");
                            alert.setHeaderText("PSBT File Invalid");
                            alert.setContentText(e.getMessage());
                            alert.showAndWait();
                            return;
                        }
                    } else {
                        try {
                            Transaction transaction = new Transaction(bytes);
                            tab = addTransactionTab(file.getName(), transaction, null);
                        } catch(RuntimeException e) {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("File Invalid");
                            alert.setHeaderText("File Invalid");
                            alert.setContentText("Unknown file format or invalid transaction");
                            alert.showAndWait();
                            return;
                        }
                    }

                    tabs.getSelectionModel().select(tab);
                } catch(IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void addExampleTxTabs() {
        addTransactionTab("p2pkh", "01000000019c2e0f24a03e72002a96acedb12a632e72b6b74c05dc3ceab1fe78237f886c48010000006a47304402203da9d487be5302a6d69e02a861acff1da472885e43d7528ed9b1b537a8e2cac9022002d1bca03a1e9715a99971bafe3b1852b7a4f0168281cbd27a220380a01b3307012102c9950c622494c2e9ff5a003e33b690fe4832477d32c2d256c67eab8bf613b34effffffff02b6f50500000000001976a914bdf63990d6dc33d705b756e13dd135466c06b3b588ac845e0201000000001976a9145fb0e9755a3424efd2ba0587d20b1e98ee29814a88ac06241559", null);
        addTransactionTab("p2sh", "0100000003a5ee1a0fd80dfbc3142df136ab56e082b799c13aa977c048bdf8f61bd158652c000000006b48304502203b0160de302cded63589a88214fe499a25aa1d86a2ea09129945cd632476a12c022100c77727daf0718307e184d55df620510cf96d4b5814ae3258519c0482c1ca82fa0121024f4102c1f1cf662bf99f2b034eb03edd4e6c96793cb9445ff519aab580649120ffffffff0fce901eb7b7551ba5f414735ff93b83a2a57403df11059ec88245fba2aaf1a0000000006a47304402204089adb8a1de1a9e22aa43b94d54f1e54dc9bea745d57df1a633e03dd9ede3c2022037d1e53e911ed7212186028f2e085f70524930e22eb6184af090ba4ab779a5b90121030644cb394bf381dbec91680bdf1be1986ad93cfb35603697353199fb285a119effffffff0fce901eb7b7551ba5f414735ff93b83a2a57403df11059ec88245fba2aaf1a0010000009300493046022100a07b2821f96658c938fa9c68950af0e69f3b2ce5f8258b3a6ad254d4bc73e11e022100e82fab8df3f7e7a28e91b3609f91e8ebf663af3a4dc2fd2abd954301a5da67e701475121022afc20bf379bc96a2f4e9e63ffceb8652b2b6a097f63fbee6ecec2a49a48010e2103a767c7221e9f15f870f1ad9311f5ab937d79fcaeee15bb2c722bca515581b4c052aeffffffff02a3b81b00000000001976a914ea00917f128f569cbdf79da5efcd9001671ab52c88ac80969800000000001976a9143dec0ead289be1afa8da127a7dbdd425a05e25f688ac00000000", null);
        addTransactionTab("p2sh-p2wpkh", "01000000000101db6b1b20aa0fd7b23880be2ecbd4a98130974cf4748fb66092ac4d3ceb1a5477010000001716001479091972186c449eb1ded22b78e40d009bdf0089feffffff02b8b4eb0b000000001976a914a457b684d7f0d539a46a45bbc043f35b59d0d96388ac0008af2f000000001976a914fd270b1ee6abcaea97fea7ad0402e8bd8ad6d77c88ac02473044022047ac8e878352d3ebbde1c94ce3a10d057c24175747116f8288e5d794d12d482f0220217f36a485cae903c713331d877c1f64677e3622ad4010726870540656fe9dcb012103ad1d8e89212f0b92c74d23bb710c00662ad1470198ac48c43f7d6f93a2a2687392040000", null);
        addTransactionTab("p2wpkh", "01000000000102fff7f7881a8099afa6940d42d1e7f6362bec38171ea3edf433541db4e4ad969f00000000494830450221008b9d1dc26ba6a9cb62127b02742fa9d754cd3bebf337f7a55d114c8e5cdd30be022040529b194ba3f9281a99f2b1c0a19c0489bc22ede944ccf4ecbab4cc618ef3ed01eeffffffef51e1b804cc89d182d279655c3aa89e815b1b309fe287d9b2b55d57b90ec68a0100000000ffffffff02202cb206000000001976a9148280b37df378db99f66f85c95a783a76ac7a6d5988ac9093510d000000001976a9143bde42dbee7e4dbe6a21b2d50ce2f0167faa815988ac000247304402203609e17b84f6a7d30c80bfa610b5b4542f32a8a0d5447a12fb1366d7f01cc44a0220573a954c4518331561406f90300e8f3358f51928d43c212a8caed02de67eebee0121025476c2e83188368da1ff3e292e7acafcdb3566bb0ad253f62fc70f07aeee635711000000", null);
        addTransactionTab("p2wsh", "01000000000102fe3dc9208094f3ffd12645477b3dc56f60ec4fa8e6f5d67c565d1c6b9216b36e000000004847304402200af4e47c9b9629dbecc21f73af989bdaa911f7e6f6c2e9394588a3aa68f81e9902204f3fcf6ade7e5abb1295b6774c8e0abd94ae62217367096bc02ee5e435b67da201ffffffff0815cf020f013ed6cf91d29f4202e8a58726b1ac6c79da47c23d1bee0a6925f80000000000ffffffff0100f2052a010000001976a914a30741f8145e5acadf23f751864167f32e0963f788ac000347304402200de66acf4527789bfda55fc5459e214fa6083f936b430a762c629656216805ac0220396f550692cd347171cbc1ef1f51e15282e837bb2b30860dc77c8f78bc8501e503473044022027dc95ad6b740fe5129e7e62a75dd00f291a2aeb1200b84b09d9e3789406b6c002201a9ecd315dd6a0e632ab20bbb98948bc0c6fb204f2c286963bb48517a7058e27034721026dccc749adc2a9d0d89497ac511f760f45c47dc5ed9cf352a58ac706453880aeadab210255a9626aebf5e29c0e6538428ba0d1dcf6ca98ffdf086aa8ced5e0d0215ea465ac00000000", null);
        addTransactionTab("test1", "02000000000102ba4dc5a4a14bfaa941b7d115b379b5e15f960635cf694c178b9116763cbd63b11600000017160014fc164cbcac023f5eacfcead2d17d8768c41949affeffffff074d44d2856beb68ba52e8832da60a1682768c2421c2d9a8109ef4e66babd1fd1e000000171600148c3098be6b430859115f5ee99c84c368afecd048feffffff02305310000000000017a914ffaf369c2212b178c7a2c21c9ccdd5d126e74c4187327f0300000000001976a914a7cda2e06b102a143ab606937a01d152e300cd3e88ac02473044022006da0ca227f765179219e08a33026b94e7cacff77f87b8cd8eb1b46d6dda11d6022064faa7912924fd23406b6ed3328f1bbbc3760dc51109a49c1b38bf57029d304f012103c6a2fcd030270427d4abe1041c8af929a9e2dbab07b243673453847ab842ee1f024730440220786316a16095105a0af28dccac5cf80f449dea2ea810a9559a89ecb989c2cb3d02205cbd9913d1217ffec144ae4f2bd895f16d778c2ec49ae9c929fdc8bcc2a2b1db0121024d4985241609d072a59be6418d700e87688f6c4d99a51ad68e66078211f076ee38820900", null);
        addTransactionTab("3of3-1signed.psbt", null, "70736274ff0100550200000001294c4871c059bb76be81e94b78059ee2e0c9b1b47f38edb6b4e75916062394930000000000feffffff01f82a0000000000001976a914e65b294f890792f2c2725d488567018d660f0cf488ac701c09004f0102aa7ed3044b1635bb800000021bf4bfc48934b7966b39bdebb689525d9b8bfed5c8b16e8c58f9afe4641d6d5f03800b5dbec0355c9f0b5e8227bc903e9d0ff1fe6ced0dcfb6d416541c7412c4331406b57041300000800000008000000080020000804f0102aa7ed3042cd31dee80000002d544b2364010378f8c6cec85f6b7ed83a8203dcdbedb97e2625f431f897b837e0363428de8fcfbfe373c0d9e1e0cc8163d886764bafe71c5822eaa232981356589145f63394f300000800000008000000080020000804f0102aa7ed3049ec7d9f580000002793e04aff18b4e40ebc48bcdc6232c54c69cf7265a38fbd85b35705e34d2d42f03368e79aa2b2b7f736d156905a7a45891df07baa2d0b7f127a537908cb82deed514130a48af300000800000008000000080020000800001012b983a000000000000220020f64748dad1cbad107761aaed5c59f25aba006498d260b440e0a091691350c9aa010569532102f26969eb8d1da34d17d33ff99e2f020cc33b3d11d9798ec14f46b82bc455d3262103171d9b824205cd5db6e9353676a292ca954b24d8310a36fc983469ba3fb507a221037f3794f3be4c4acc086ac84d6902c025713eabf8890f20f44acf0b34e3c0f0f753ae220602f26969eb8d1da34d17d33ff99e2f020cc33b3d11d9798ec14f46b82bc455d3261c130a48af300000800000008000000080020000800000000000000000220603171d9b824205cd5db6e9353676a292ca954b24d8310a36fc983469ba3fb507a21c5f63394f300000800000008000000080020000800000000000000000220203171d9b824205cd5db6e9353676a292ca954b24d8310a36fc983469ba3fb507a24830450221008d27cc4b03bc543726e73b69e7980e7364d6f33f979a5cd9b92fb3d050666bd002204fc81fc9c67baf7c3b77041ed316714a9c117a5bdbb020e8c771ea3bdc342434012206037f3794f3be4c4acc086ac84d6902c025713eabf8890f20f44acf0b34e3c0f0f71c06b570413000008000000080000000800200008000000000000000000000");
    }

    private void addTransactionTab(String name, String transactionHex, String psbtHex) {
        if(transactionHex != null) {
            byte[] txbytes = Utils.hexToBytes(transactionHex);
            Transaction transaction = new Transaction(txbytes);
            addTransactionTab(name, transaction, null);
        } else if(psbtHex != null) {
            try {
                PSBT psbt = PSBT.fromString(psbtHex);
                addTransactionTab(name, null, psbt);
            } catch(Exception e) {
                //ignore
            }
        }
    }

    private Tab addTransactionTab(String name, Transaction transaction, PSBT psbt) {
        try {
            Tab tab = new Tab(name);
            tab.setClosable(true);
            FXMLLoader transactionLoader = new FXMLLoader(getClass().getResource("transaction/transaction.fxml"));
            tab.setContent(transactionLoader.load());
            TransactionController controller = transactionLoader.getController();

            if(transaction != null) {
                controller.setTransaction(transaction);
            } else if(psbt != null) {
                controller.setPSBT(psbt);
            }

            tabs.getTabs().add(tab);
            return tab;
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
