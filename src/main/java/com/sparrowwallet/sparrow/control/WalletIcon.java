package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.Theme;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.ImageUtils;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import org.controlsfx.glyphfont.Glyph;
import org.girod.javafx.svgimage.SVGImage;
import org.girod.javafx.svgimage.SVGLoader;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class WalletIcon extends StackPane {
    public static final String PROTOCOL = "walleticon";
    private static final String QUERY = "icon";
    public static final int WIDTH = 15;
    public static final int HEIGHT = 15;
    public static final int SAVE_WIDTH = WIDTH * 2;
    public static final int SAVE_HEIGHT = HEIGHT * 2;

    private final Storage storage;
    private final ObjectProperty<Wallet> walletProperty = new SimpleObjectProperty<>();
    private final Keystore keystore;
    private final Glyph fallbackIcon;

    public WalletIcon(Storage storage, Wallet wallet) {
        this(storage, wallet, null, null);
    }

    public WalletIcon(Storage storage, Wallet wallet, Keystore keystore, Glyph fallbackIcon) {
        super();
        this.storage = storage;
        this.keystore = keystore;
        this.fallbackIcon = fallbackIcon;
        setPrefSize(WIDTH, HEIGHT);
        walletProperty.addListener((observable, oldValue, newValue) -> {
            refresh();
        });
        walletProperty.set(wallet);
    }

    public void refresh() {
        Wallet wallet = getWallet();

        getChildren().clear();
        if(wallet.getWalletConfig() != null && wallet.getWalletConfig().getIconData() != null) {
            String walletId = storage.getWalletId(wallet);
            if(AppServices.get().getWallet(walletId) != null) {
                addWalletIcon(walletId);
            } else {
                Platform.runLater(() -> addWalletIcon(walletId));
            }
        } else if(this.keystore != null || wallet.getKeystores().size() == 1) {
            Keystore keystore = this.keystore == null ? wallet.getKeystores().get(0) : this.keystore;
            if(keystore.getSource() == KeystoreSource.HW_USB || keystore.getSource() == KeystoreSource.HW_AIRGAPPED) {
                WalletModel walletModel = keystore.getWalletModel();

                SVGImage svgImage;
                if(Config.get().getTheme() == Theme.DARK) {
                    svgImage = loadSVGImage("/image/" + walletModel.getType() + "-icon-invert.svg");
                } else {
                    svgImage = loadSVGImage("/image/" + walletModel.getType() + "-icon.svg");
                }

                if(svgImage != null) {
                    getChildren().add(svgImage);
                    return;
                }

                Image image = null;
                if(Config.get().getTheme() == Theme.DARK) {
                    image = loadImage("image/" + walletModel.getType() + "-icon-invert.png");
                }

                if(image == null) {
                    image = loadImage("image/" + walletModel.getType() + "-icon.png");
                }

                if(image == null) {
                    image = loadImage("image/" + walletModel.getType() + ".png");
                }

                if(image != null && !image.isError()) {
                    ImageView imageView = new ImageView(image);
                    getChildren().add(imageView);
                }
            }
        }

        if(getChildren().isEmpty()) {
            Glyph glyph = fallbackIcon;
            if(glyph == null) {
                glyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.WALLET);
            }
            glyph.setFontSize(10.0);
            getChildren().add(glyph);
        }
    }

    private SVGImage loadSVGImage(String imageName) {
        try {
            URL url = AppServices.class.getResource(imageName);
            if(url != null) {
                return SVGLoader.load(url);
            }
        } catch(Exception e) {
            //ignore
        }

        return null;
    }

    private Image loadImage(String imageName) {
        try {
            return new Image(imageName, 15, 15, true, true);
        } catch(Exception e) {
            //ignore
        }

        return null;
    }

    private void addWalletIcon(String walletId) {
        Image image = new Image(PROTOCOL + ":" + walletId + "?" + QUERY, WIDTH, HEIGHT, true, false);
        getChildren().clear();
        Circle circle = new Circle(getPrefWidth() / 2,getPrefHeight() / 2,getPrefWidth() / 2);
        circle.setFill(new ImagePattern(image));
        getChildren().add(circle);
    }

    public boolean addFailure() {
        if(getChildren().stream().noneMatch(node -> node.getStyleClass().contains("failure"))) {
            Glyph failGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
            failGlyph.setFontSize(10);
            failGlyph.getStyleClass().add("failure");
            getChildren().add(failGlyph);
            StackPane.setAlignment(failGlyph, Pos.TOP_RIGHT);
            failGlyph.setTranslateX(5);
            failGlyph.setTranslateY(-4);
            return true;
        }

        return false;
    }

    public void removeFailure() {
        getChildren().removeIf(node -> node.getStyleClass().contains("failure"));
    }

    public Wallet getWallet() {
        return walletProperty.get();
    }

    public ObjectProperty<Wallet> walletProperty() {
        return walletProperty;
    }

    public void setWallet(Wallet wallet) {
        this.walletProperty.set(wallet);
    }

    public static class WalletIconStreamHandler extends URLStreamHandler {
        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            return new URLConnection(url) {
                @Override
                public void connect() throws IOException {
                    //Nothing required
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    String walletId = url.getPath();
                    String query = url.getQuery();

                    Wallet wallet = AppServices.get().getWallet(walletId);
                    if(wallet == null) {
                        throw new IOException("Cannot find wallet for wallet id " + walletId);
                    }

                    if(query.startsWith(QUERY)) {
                        if(wallet.getWalletConfig() == null || wallet.getWalletConfig().getIconData() == null) {
                            throw new IOException("No icon data for " + walletId);
                        }

                        ByteArrayInputStream bais = new ByteArrayInputStream(wallet.getWalletConfig().getIconData());
                        if(query.endsWith("@2x")) {
                            return bais;
                        } else {
                            return ImageUtils.resize(bais, WalletIcon.WIDTH, WalletIcon.HEIGHT);
                        }
                    }

                    throw new MalformedURLException("Cannot load url " + url);
                }
            };
        }
    }
}
