package com.sparrowwallet.sparrow;

import com.google.common.base.Charsets;
import com.google.common.eventbus.Subscribe;
import com.google.common.io.ByteSource;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECIESKeyCrypter;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTParseException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.FileType;
import com.sparrowwallet.sparrow.io.IOUtils;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.transaction.TransactionController;
import com.sparrowwallet.sparrow.wallet.WalletController;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.net.URL;
import java.text.ParseException;
import java.util.*;

public class AppController implements Initializable {
    private static final String TRANSACTION_TAB_TYPE = "transaction";
    public static final String DRAG_OVER_CLASS = "drag-over";

    @FXML
    private MenuItem exportWallet;

    @FXML
    private CheckMenuItem showTxHex;

    @FXML
    private StackPane rootStack;

    @FXML
    private TabPane tabs;

    public static boolean showTxHexProperty;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    void initializeView() {
        rootStack.setOnDragOver(event -> {
            if(event.getGestureSource() != rootStack && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        rootStack.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if(db.hasFiles()) {
                for(File file : db.getFiles()) {
                    openFile(file);
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        rootStack.setOnDragEntered(event -> {
            rootStack.getStyleClass().add(DRAG_OVER_CLASS);
        });

        rootStack.setOnDragExited(event -> {
            rootStack.getStyleClass().removeAll(DRAG_OVER_CLASS);
        });

        tabs.getSelectionModel().selectedItemProperty().addListener((observable, old_val, selectedTab) -> {
            if(selectedTab != null) {
                TabData tabData = (TabData)selectedTab.getUserData();
                if(tabData.getType() == TabData.TabType.TRANSACTION) {
                    EventManager.get().post(new TransactionTabSelectedEvent(selectedTab));
                    exportWallet.setDisable(true);
                    showTxHex.setDisable(false);
                } else if(tabData.getType() == TabData.TabType.WALLET) {
                    EventManager.get().post(new WalletTabSelectedEvent(selectedTab));
                    exportWallet.setDisable(false);
                    showTxHex.setDisable(true);
                }
            }
        });

        showTxHex.setSelected(true);
        showTxHexProperty = true;
        exportWallet.setDisable(true);

        //addWalletTab("newWallet", new Wallet(PolicyType.SINGLE, ScriptType.P2WPKH));
    }

    public void openFromFile(ActionEvent event) {
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
            openFile(file);
        }
    }

    private void openFile(File file) {
        for(Tab tab : tabs.getTabs()) {
            TabData tabData = (TabData)tab.getUserData();
            if(file.equals(tabData.getFile())) {
                tabs.getSelectionModel().select(tab);
                return;
            }
        }

        if(file.exists()) {
            try {
                byte[] bytes = new byte[(int)file.length()];
                FileInputStream stream = new FileInputStream(file);
                stream.read(bytes);
                stream.close();
                String name = file.getName();

                try {
                    Tab tab = addTransactionTab(name, bytes);
                    TabData tabData = (TabData)tab.getUserData();
                    tabData.setFile(file);
                    tabs.getSelectionModel().select(tab);
                } catch(ParseException e) {
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                    ByteSource byteSource = new ByteSource() {
                        @Override
                        public InputStream openStream() {
                            return inputStream;
                        }
                    };

                    String text = byteSource.asCharSource(Charsets.UTF_8).read().trim();
                    Tab tab = addTransactionTab(name, text);
                    tabs.getSelectionModel().select(tab);
                }
            } catch(IOException e) {
                showErrorDialog("Error opening file", e.getMessage());
            } catch(PSBTParseException e) {
                showErrorDialog("Invalid PSBT", e.getMessage());
            } catch(TransactionParseException e) {
                showErrorDialog("Invalid transaction", e.getMessage());
            } catch(ParseException e) {
                showErrorDialog("Invalid file", e.getMessage());
            }
        }
    }

    public void openFromText(ActionEvent event) {
        TextAreaDialog dialog = new TextAreaDialog();
        dialog.setTitle("Open from text");
        dialog.getDialogPane().setHeaderText("Paste a transaction or PSBT:");
        Optional<String> text = dialog.showAndWait();
        if(text.isPresent() && !text.get().isEmpty()) {
            try {
                Tab tab = addTransactionTab(null, text.get().trim());
                TabData tabData = (TabData)tab.getUserData();
                tabData.setText(text.get());
                tabs.getSelectionModel().select(tab);
            } catch(PSBTParseException e) {
                showErrorDialog("Invalid PSBT", e.getMessage());
            } catch(TransactionParseException e) {
                showErrorDialog("Invalid transaction", e.getMessage());
            } catch(ParseException e) {
                showErrorDialog("Could not recognise input", e.getMessage());
            }
        }
    }

    public static void showErrorDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void closeTab(ActionEvent event) {
        tabs.getTabs().remove(tabs.getSelectionModel().getSelectedItem());
    }

    public void showTxHex(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem)event.getSource();
        EventManager.get().post(new TransactionTabChangedEvent(tabs.getSelectionModel().getSelectedItem(), item.isSelected()));
        showTxHexProperty = item.isSelected();
    }

    public void newWallet(ActionEvent event) {
        WalletNameDialog dlg = new WalletNameDialog();
        Optional<String> walletName = dlg.showAndWait();
        if(walletName.isPresent()) {
            File walletFile = Storage.getStorage().getWalletFile(walletName.get());
            Wallet wallet = new Wallet(walletName.get(), PolicyType.SINGLE, ScriptType.P2WPKH);
            Tab tab = addWalletTab(walletFile, null, wallet);
            tabs.getSelectionModel().select(tab);
        }
    }

    public void openWallet(ActionEvent event) {
        Stage window = new Stage();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Wallet");
        fileChooser.setInitialDirectory(Storage.getStorage().getWalletsDir());

        File file = fileChooser.showOpenDialog(window);
        if(file != null) {
            try {
                Wallet wallet;
                ECKey encryptionPubKey = WalletForm.NO_PASSWORD_KEY;
                FileType fileType = IOUtils.getFileType(file);
                if(FileType.JSON.equals(fileType)) {
                    wallet = Storage.getStorage().loadWallet(file);
                } else if(FileType.BINARY.equals(fileType)) {
                    WalletPasswordDialog dlg = new WalletPasswordDialog(WalletPasswordDialog.PasswordRequirement.LOAD);
                    Optional<String> password = dlg.showAndWait();
                    if(!password.isPresent()) {
                        return;
                    }

                    ECKey encryptionFullKey = ECIESKeyCrypter.deriveECKey(password.get());
                    wallet = Storage.getStorage().loadWallet(file, encryptionFullKey);
                    encryptionPubKey = ECKey.fromPublicOnly(encryptionFullKey);
                } else {
                    throw new IOException("Unsupported file type");
                }

                Tab tab = addWalletTab(file, encryptionPubKey, wallet);
                tabs.getSelectionModel().select(tab);
            } catch (InvalidPasswordException e) {
                showErrorDialog("Invalid Password", "The password was invalid.");
            } catch (Exception e) {
                showErrorDialog("Error opening wallet", e.getMessage());
            }
        }
    }

    public void importWallet(ActionEvent event) {
        WalletImportDialog dlg = new WalletImportDialog();
        Optional<Wallet> optionalWallet = dlg.showAndWait();
        if(optionalWallet.isPresent()) {
            Wallet wallet = optionalWallet.get();
            File walletFile = Storage.getStorage().getWalletFile(wallet.getName());
            Tab tab = addWalletTab(walletFile, null, wallet);
            tabs.getSelectionModel().select(tab);
        }
    }

    public void exportWallet(ActionEvent event) {
        Tab selectedTab = tabs.getSelectionModel().getSelectedItem();
        TabData tabData = (TabData)selectedTab.getUserData();
        if(tabData.getType() == TabData.TabType.WALLET) {
            WalletTabData walletTabData = (WalletTabData)tabData;
            WalletExportDialog dlg = new WalletExportDialog(walletTabData.getWallet());
            Optional<Wallet> wallet = dlg.showAndWait();
            if(wallet.isPresent()) {
                //Successful export
            }
        }
    }

    public Tab addWalletTab(File walletFile, ECKey encryptionPubKey, Wallet wallet) {
        try {
            String name = walletFile.getName();
            if(name.endsWith(".json")) {
                name = name.substring(0, name.lastIndexOf('.'));
            }
            Tab tab = new Tab(name);
            TabData tabData = new WalletTabData(TabData.TabType.WALLET, wallet, walletFile);
            tab.setUserData(tabData);
            tab.setContextMenu(getTabContextMenu(tab));
            tab.setClosable(true);
            FXMLLoader walletLoader = new FXMLLoader(getClass().getResource("wallet/wallet.fxml"));
            tab.setContent(walletLoader.load());
            WalletController controller = walletLoader.getController();
            WalletForm walletForm = new WalletForm(walletFile, encryptionPubKey, wallet);
            controller.setWalletForm(walletForm);

            tabs.getTabs().add(tab);
            return tab;
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void openExamples(ActionEvent event) {
        try {
            addTransactionTab("p2pkh", "01000000019c2e0f24a03e72002a96acedb12a632e72b6b74c05dc3ceab1fe78237f886c48010000006a47304402203da9d487be5302a6d69e02a861acff1da472885e43d7528ed9b1b537a8e2cac9022002d1bca03a1e9715a99971bafe3b1852b7a4f0168281cbd27a220380a01b3307012102c9950c622494c2e9ff5a003e33b690fe4832477d32c2d256c67eab8bf613b34effffffff02b6f50500000000001976a914bdf63990d6dc33d705b756e13dd135466c06b3b588ac845e0201000000001976a9145fb0e9755a3424efd2ba0587d20b1e98ee29814a88ac06241559");
            addTransactionTab("p2sh", "0100000003a5ee1a0fd80dfbc3142df136ab56e082b799c13aa977c048bdf8f61bd158652c000000006b48304502203b0160de302cded63589a88214fe499a25aa1d86a2ea09129945cd632476a12c022100c77727daf0718307e184d55df620510cf96d4b5814ae3258519c0482c1ca82fa0121024f4102c1f1cf662bf99f2b034eb03edd4e6c96793cb9445ff519aab580649120ffffffff0fce901eb7b7551ba5f414735ff93b83a2a57403df11059ec88245fba2aaf1a0000000006a47304402204089adb8a1de1a9e22aa43b94d54f1e54dc9bea745d57df1a633e03dd9ede3c2022037d1e53e911ed7212186028f2e085f70524930e22eb6184af090ba4ab779a5b90121030644cb394bf381dbec91680bdf1be1986ad93cfb35603697353199fb285a119effffffff0fce901eb7b7551ba5f414735ff93b83a2a57403df11059ec88245fba2aaf1a0010000009300493046022100a07b2821f96658c938fa9c68950af0e69f3b2ce5f8258b3a6ad254d4bc73e11e022100e82fab8df3f7e7a28e91b3609f91e8ebf663af3a4dc2fd2abd954301a5da67e701475121022afc20bf379bc96a2f4e9e63ffceb8652b2b6a097f63fbee6ecec2a49a48010e2103a767c7221e9f15f870f1ad9311f5ab937d79fcaeee15bb2c722bca515581b4c052aeffffffff02a3b81b00000000001976a914ea00917f128f569cbdf79da5efcd9001671ab52c88ac80969800000000001976a9143dec0ead289be1afa8da127a7dbdd425a05e25f688ac00000000");
            addTransactionTab("p2sh-p2wpkh", "01000000000101db6b1b20aa0fd7b23880be2ecbd4a98130974cf4748fb66092ac4d3ceb1a5477010000001716001479091972186c449eb1ded22b78e40d009bdf0089feffffff02b8b4eb0b000000001976a914a457b684d7f0d539a46a45bbc043f35b59d0d96388ac0008af2f000000001976a914fd270b1ee6abcaea97fea7ad0402e8bd8ad6d77c88ac02473044022047ac8e878352d3ebbde1c94ce3a10d057c24175747116f8288e5d794d12d482f0220217f36a485cae903c713331d877c1f64677e3622ad4010726870540656fe9dcb012103ad1d8e89212f0b92c74d23bb710c00662ad1470198ac48c43f7d6f93a2a2687392040000");
            addTransactionTab("p2sh-p2wsh", "01000000000101708256c5896fb3f00ef37601f8e30c5b460dbcd1fca1cd7199f9b56fc4ecd5400000000023220020615ae01ed1bc1ffaad54da31d7805d0bb55b52dfd3941114330368c1bbf69b4cffffffff01603edb0300000000160014bbef244bcad13cffb68b5cef3017c7423675552204004730440220010d2854b86b90b7c33661ca25f9d9f15c24b88c5c4992630f77ff004b998fb802204106fc3ec8481fa98e07b7e78809ac91b6ccaf60bf4d3f729c5a75899bb664a501473044022046d66321c6766abcb1366a793f9bfd0e11e0b080354f18188588961ea76c5ad002207262381a0661d66f5c39825202524c45f29d500c6476176cd910b1691176858701695221026ccfb8061f235cc110697c0bfb3afb99d82c886672f6b9b5393b25a434c0cbf32103befa190c0c22e2f53720b1be9476dcf11917da4665c44c9c71c3a2d28a933c352102be46dc245f58085743b1cc37c82f0d63a960efa43b5336534275fc469b49f4ac53ae00000000");
            addTransactionTab("p2wpkh", "01000000000101109d2e41430bfdec7e6dfb02bf78b5827eeb717ef25210ff3203b0db8c76c9260000000000ffffffff01a032eb0500000000160014bbef244bcad13cffb68b5cef3017c742367555220247304402202f7cac3494e521018ae0be4ca18517639ef7c00658d42a9f938b2b344c8454e2022039a54218832fad5d14b331329d9042c51ee6be287e95e49ee5b96fda1f5ce13f0121026ccfb8061f235cc110697c0bfb3afb99d82c886672f6b9b5393b25a434c0cbf300000000");
            addTransactionTab("p2wsh", "0100000000010193a2db37b841b2a46f4e9bb63fe9c1012da3ab7fe30b9f9c974242778b5af8980000000000ffffffff01806fb307000000001976a914bbef244bcad13cffb68b5cef3017c7423675552288ac040047304402203cdcaf02a44e37e409646e8a506724e9e1394b890cb52429ea65bac4cc2403f1022024b934297bcd0c21f22cee0e48751c8b184cc3a0d704cae2684e14858550af7d01483045022100feb4e1530c13e72226dc912dcd257df90d81ae22dbddb5a3c2f6d86f81d47c8e022069889ddb76388fa7948aaa018b2480ac36132009bb9cfade82b651e88b4b137a01695221026ccfb8061f235cc110697c0bfb3afb99d82c886672f6b9b5393b25a434c0cbf32103befa190c0c22e2f53720b1be9476dcf11917da4665c44c9c71c3a2d28a933c352102be46dc245f58085743b1cc37c82f0d63a960efa43b5336534275fc469b49f4ac53ae00000000");
            addTransactionTab("test1", "02000000000102ba4dc5a4a14bfaa941b7d115b379b5e15f960635cf694c178b9116763cbd63b11600000017160014fc164cbcac023f5eacfcead2d17d8768c41949affeffffff074d44d2856beb68ba52e8832da60a1682768c2421c2d9a8109ef4e66babd1fd1e000000171600148c3098be6b430859115f5ee99c84c368afecd0481500400002305310000000000017a914ffaf369c2212b178c7a2c21c9ccdd5d126e74c4187327f0300000000001976a914a7cda2e06b102a143ab606937a01d152e300cd3e88ac02473044022006da0ca227f765179219e08a33026b94e7cacff77f87b8cd8eb1b46d6dda11d6022064faa7912924fd23406b6ed3328f1bbbc3760dc51109a49c1b38bf57029d304f012103c6a2fcd030270427d4abe1041c8af929a9e2dbab07b243673453847ab842ee1f024730440220786316a16095105a0af28dccac5cf80f449dea2ea810a9559a89ecb989c2cb3d02205cbd9913d1217ffec144ae4f2bd895f16d778c2ec49ae9c929fdc8bcc2a2b1db0121024d4985241609d072a59be6418d700e87688f6c4d99a51ad68e66078211f076ee38820900");
            addTransactionTab("3of3-1s.psbt", "70736274ff0100550200000001294c4871c059bb76be81e94b78059ee2e0c9b1b47f38edb6b4e75916062394930000000000feffffff01f82a0000000000001976a914e65b294f890792f2c2725d488567018d660f0cf488ac701c09004f0102aa7ed3044b1635bb800000021bf4bfc48934b7966b39bdebb689525d9b8bfed5c8b16e8c58f9afe4641d6d5f03800b5dbec0355c9f0b5e8227bc903e9d0ff1fe6ced0dcfb6d416541c7412c4331406b57041300000800000008000000080020000804f0102aa7ed3042cd31dee80000002d544b2364010378f8c6cec85f6b7ed83a8203dcdbedb97e2625f431f897b837e0363428de8fcfbfe373c0d9e1e0cc8163d886764bafe71c5822eaa232981356589145f63394f300000800000008000000080020000804f0102aa7ed3049ec7d9f580000002793e04aff18b4e40ebc48bcdc6232c54c69cf7265a38fbd85b35705e34d2d42f03368e79aa2b2b7f736d156905a7a45891df07baa2d0b7f127a537908cb82deed514130a48af300000800000008000000080020000800001012b983a000000000000220020f64748dad1cbad107761aaed5c59f25aba006498d260b440e0a091691350c9aa010569532102f26969eb8d1da34d17d33ff99e2f020cc33b3d11d9798ec14f46b82bc455d3262103171d9b824205cd5db6e9353676a292ca954b24d8310a36fc983469ba3fb507a221037f3794f3be4c4acc086ac84d6902c025713eabf8890f20f44acf0b34e3c0f0f753ae220602f26969eb8d1da34d17d33ff99e2f020cc33b3d11d9798ec14f46b82bc455d3261c130a48af300000800000008000000080020000800000000000000000220603171d9b824205cd5db6e9353676a292ca954b24d8310a36fc983469ba3fb507a21c5f63394f300000800000008000000080020000800000000000000000220203171d9b824205cd5db6e9353676a292ca954b24d8310a36fc983469ba3fb507a24830450221008d27cc4b03bc543726e73b69e7980e7364d6f33f979a5cd9b92fb3d050666bd002204fc81fc9c67baf7c3b77041ed316714a9c117a5bdbb020e8c771ea3bdc342434012206037f3794f3be4c4acc086ac84d6902c025713eabf8890f20f44acf0b34e3c0f0f71c06b570413000008000000080000000800200008000000000000000000000");
            addTransactionTab("signer.psbt", "70736274ff01009a020000000258e87a21b56daf0c23be8e7070456c336f7cbaa5c8757924f545887bb2abdd750000000000ffffffff838d0427d0ec650a68aa46bb0b098aea4422c071b2ca78352a077959d07cea1d0100000000ffffffff0270aaf00800000000160014d85c2b71d0060b09c9886aeb815e50991dda124d00e1f5050000000016001400aea9a2e5f0f876a588df5546e8742d1d87008f00000000000100bb0200000001aad73931018bd25f84ae400b68848be09db706eac2ac18298babee71ab656f8b0000000048473044022058f6fc7c6a33e1b31548d481c826c015bd30135aad42cd67790dab66d2ad243b02204a1ced2604c6735b6393e5b41691dd78b00f0c5942fb9f751856faa938157dba01feffffff0280f0fa020000000017a9140fb9463421696b82c833af241c78c17ddbde493487d0f20a270100000017a91429ca74f8a08f81999428185c97b5d852e4063f618765000000220202dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d7483045022100f61038b308dc1da865a34852746f015772934208c6d24454393cd99bdf2217770220056e675a675a6d0a02b85b14e5e29074d8a25a9b5760bea2816f661910a006ea01010304010000000104475221029583bf39ae0a609747ad199addd634fa6108559d6c5cd39b4c2183f1ab96e07f2102dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d752ae2206029583bf39ae0a609747ad199addd634fa6108559d6c5cd39b4c2183f1ab96e07f10d90c6a4f000000800000008000000080220602dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d710d90c6a4f0000008000000080010000800001012000c2eb0b0000000017a914b7f5faf40e3d40a5a459b1db3535f2b72fa921e8872202023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e73473044022065f45ba5998b59a27ffe1a7bed016af1f1f90d54b3aa8f7450aa5f56a25103bd02207f724703ad1edb96680b284b56d4ffcb88f7fb759eabbe08aa30f29b851383d2010103040100000001042200208c2353173743b595dfb4a07b72ba8e42e3797da74e87fe7d9d7497e3b2028903010547522103089dc10c7ac6db54f91329af617333db388cead0c231f723379d1b99030b02dc21023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e7352ae2206023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e7310d90c6a4f000000800000008003000080220603089dc10c7ac6db54f91329af617333db388cead0c231f723379d1b99030b02dc10d90c6a4f00000080000000800200008000220203a9a4c37f5996d3aa25dbac6b570af0650394492942460b354753ed9eeca5877110d90c6a4f000000800000008004000080002202027f6399757d2eff55a136ad02c684b1838b6556e5f1b6b34282a94b6b5005109610d90c6a4f00000080000000800500008000");
            addTransactionTab("combiner.psbt", "70736274ff01009a020000000258e87a21b56daf0c23be8e7070456c336f7cbaa5c8757924f545887bb2abdd750000000000ffffffff838d0427d0ec650a68aa46bb0b098aea4422c071b2ca78352a077959d07cea1d0100000000ffffffff0270aaf00800000000160014d85c2b71d0060b09c9886aeb815e50991dda124d00e1f5050000000016001400aea9a2e5f0f876a588df5546e8742d1d87008f00000000000100bb0200000001aad73931018bd25f84ae400b68848be09db706eac2ac18298babee71ab656f8b0000000048473044022058f6fc7c6a33e1b31548d481c826c015bd30135aad42cd67790dab66d2ad243b02204a1ced2604c6735b6393e5b41691dd78b00f0c5942fb9f751856faa938157dba01feffffff0280f0fa020000000017a9140fb9463421696b82c833af241c78c17ddbde493487d0f20a270100000017a91429ca74f8a08f81999428185c97b5d852e4063f6187650000002202029583bf39ae0a609747ad199addd634fa6108559d6c5cd39b4c2183f1ab96e07f473044022074018ad4180097b873323c0015720b3684cc8123891048e7dbcd9b55ad679c99022073d369b740e3eb53dcefa33823c8070514ca55a7dd9544f157c167913261118c01220202dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d7483045022100f61038b308dc1da865a34852746f015772934208c6d24454393cd99bdf2217770220056e675a675a6d0a02b85b14e5e29074d8a25a9b5760bea2816f661910a006ea01010304010000000104475221029583bf39ae0a609747ad199addd634fa6108559d6c5cd39b4c2183f1ab96e07f2102dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d752ae2206029583bf39ae0a609747ad199addd634fa6108559d6c5cd39b4c2183f1ab96e07f10d90c6a4f000000800000008000000080220602dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d710d90c6a4f0000008000000080010000800001012000c2eb0b0000000017a914b7f5faf40e3d40a5a459b1db3535f2b72fa921e887220203089dc10c7ac6db54f91329af617333db388cead0c231f723379d1b99030b02dc473044022062eb7a556107a7c73f45ac4ab5a1dddf6f7075fb1275969a7f383efff784bcb202200c05dbb7470dbf2f08557dd356c7325c1ed30913e996cd3840945db12228da5f012202023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e73473044022065f45ba5998b59a27ffe1a7bed016af1f1f90d54b3aa8f7450aa5f56a25103bd02207f724703ad1edb96680b284b56d4ffcb88f7fb759eabbe08aa30f29b851383d2010103040100000001042200208c2353173743b595dfb4a07b72ba8e42e3797da74e87fe7d9d7497e3b2028903010547522103089dc10c7ac6db54f91329af617333db388cead0c231f723379d1b99030b02dc21023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e7352ae2206023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e7310d90c6a4f000000800000008003000080220603089dc10c7ac6db54f91329af617333db388cead0c231f723379d1b99030b02dc10d90c6a4f00000080000000800200008000220203a9a4c37f5996d3aa25dbac6b570af0650394492942460b354753ed9eeca5877110d90c6a4f000000800000008004000080002202027f6399757d2eff55a136ad02c684b1838b6556e5f1b6b34282a94b6b5005109610d90c6a4f00000080000000800500008000");
            addTransactionTab("finalizer.psbt", "70736274ff01009a020000000258e87a21b56daf0c23be8e7070456c336f7cbaa5c8757924f545887bb2abdd750000000000ffffffff838d0427d0ec650a68aa46bb0b098aea4422c071b2ca78352a077959d07cea1d0100000000ffffffff0270aaf00800000000160014d85c2b71d0060b09c9886aeb815e50991dda124d00e1f5050000000016001400aea9a2e5f0f876a588df5546e8742d1d87008f00000000000100bb0200000001aad73931018bd25f84ae400b68848be09db706eac2ac18298babee71ab656f8b0000000048473044022058f6fc7c6a33e1b31548d481c826c015bd30135aad42cd67790dab66d2ad243b02204a1ced2604c6735b6393e5b41691dd78b00f0c5942fb9f751856faa938157dba01feffffff0280f0fa020000000017a9140fb9463421696b82c833af241c78c17ddbde493487d0f20a270100000017a91429ca74f8a08f81999428185c97b5d852e4063f6187650000000107da00473044022074018ad4180097b873323c0015720b3684cc8123891048e7dbcd9b55ad679c99022073d369b740e3eb53dcefa33823c8070514ca55a7dd9544f157c167913261118c01483045022100f61038b308dc1da865a34852746f015772934208c6d24454393cd99bdf2217770220056e675a675a6d0a02b85b14e5e29074d8a25a9b5760bea2816f661910a006ea01475221029583bf39ae0a609747ad199addd634fa6108559d6c5cd39b4c2183f1ab96e07f2102dab61ff49a14db6a7d02b0cd1fbb78fc4b18312b5b4e54dae4dba2fbfef536d752ae0001012000c2eb0b0000000017a914b7f5faf40e3d40a5a459b1db3535f2b72fa921e8870107232200208c2353173743b595dfb4a07b72ba8e42e3797da74e87fe7d9d7497e3b20289030108da0400473044022062eb7a556107a7c73f45ac4ab5a1dddf6f7075fb1275969a7f383efff784bcb202200c05dbb7470dbf2f08557dd356c7325c1ed30913e996cd3840945db12228da5f01473044022065f45ba5998b59a27ffe1a7bed016af1f1f90d54b3aa8f7450aa5f56a25103bd02207f724703ad1edb96680b284b56d4ffcb88f7fb759eabbe08aa30f29b851383d20147522103089dc10c7ac6db54f91329af617333db388cead0c231f723379d1b99030b02dc21023add904f3d6dcf59ddb906b0dee23529b7ffb9ed50e5e86151926860221f0e7352ae00220203a9a4c37f5996d3aa25dbac6b570af0650394492942460b354753ed9eeca5877110d90c6a4f000000800000008004000080002202027f6399757d2eff55a136ad02c684b1838b6556e5f1b6b34282a94b6b5005109610d90c6a4f00000080000000800500008000");
        } catch(Exception e) {
            System.out.println(e);
        }
    }

    private Tab addTransactionTab(String name, String string) throws ParseException, PSBTParseException, TransactionParseException {
        if(Utils.isBase64(string) && !Utils.isHex(string)) {
            return addTransactionTab(name, Base64.getDecoder().decode(string));
        } else if(Utils.isHex(string)) {
            return addTransactionTab(name, Utils.hexToBytes(string));
        }

        throw new ParseException("Input is not base64 or hex", 0);
    }

    private Tab addTransactionTab(String name, byte[] bytes) throws PSBTParseException, ParseException, TransactionParseException {
        if(PSBT.isPSBT(bytes)) {
            PSBT psbt = new PSBT(bytes);
            return addTransactionTab(name, null, psbt);
        }

        if(Transaction.isTransaction(bytes)) {
            try {
                Transaction transaction = new Transaction(bytes);
                return addTransactionTab(name, transaction, null);
            } catch(Exception e) {
                throw new TransactionParseException(e.getMessage());
            }
        }

        throw new ParseException("Not a valid PSBT or transaction", 0);
    }

    private Tab addTransactionTab(String name, Transaction transaction, PSBT psbt) {
        try {
            String tabName = name;
            if(tabName == null || tabName.isEmpty()) {
                if(transaction != null) {
                    tabName = "[" + transaction.getTxId().toString().substring(0, 6) + "]";
                } else if(psbt != null) {
                    tabName = "[" + psbt.getTransaction().getTxId().toString().substring(0, 6) + "]";
                }
            }

            Tab tab = new Tab(tabName);
            TabData tabData = new TransactionTabData(TabData.TabType.TRANSACTION, transaction);
            tab.setUserData(tabData);
            tab.setContextMenu(getTabContextMenu(tab));
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

    private ContextMenu getTabContextMenu(Tab tab) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem close = new MenuItem("Close");
        close.setOnAction(event -> {
            tabs.getTabs().remove(tab);
        });

        MenuItem closeOthers = new MenuItem("Close Others");
        closeOthers.setOnAction(event -> {
            List<Tab> otherTabs = new ArrayList<>(tabs.getTabs());
            otherTabs.remove(tab);
            tabs.getTabs().removeAll(otherTabs);
        });

        MenuItem closeAll = new MenuItem("Close All");
        closeAll.setOnAction(event -> {
            tabs.getTabs().removeAll(tabs.getTabs());
        });

        contextMenu.getItems().addAll(close, closeOthers, closeAll);
        return contextMenu;
    }

    @Subscribe
    public void tabSelected(TabSelectedEvent event) {
        Tab selectedTab = event.getTab();
        String tabType = (String)selectedTab.getUserData();

        String tabName = selectedTab.getText();
        if(tabs.getScene() != null) {
            Stage tabStage = (Stage)tabs.getScene().getWindow();
            tabStage.setTitle("Sparrow - " + tabName);
        }
    }
}
