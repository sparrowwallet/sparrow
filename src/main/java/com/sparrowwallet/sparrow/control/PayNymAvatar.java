package com.sparrowwallet.sparrow.control;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Config;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.Proxy;
import java.net.URL;

public class PayNymAvatar extends StackPane {
    private static final Logger log = LoggerFactory.getLogger(PayNymAvatar.class);

    private final ObjectProperty<PaymentCode> paymentCodeProperty = new SimpleObjectProperty<>(null);

    public PayNymAvatar() {
        super();

        paymentCodeProperty.addListener((observable, oldValue, newValue) -> {
            if(Config.get().isUsePayNym()) {
                PayNymAvatarService payNymAvatarService = new PayNymAvatarService(newValue);
                payNymAvatarService.setOnRunning(runningEvent -> {
                    getChildren().clear();
                });
                payNymAvatarService.setOnSucceeded(successEvent -> {
                    Circle circle = new Circle(getWidth() / 2,getHeight() / 2,getWidth() / 2);
                    circle.setFill(new ImagePattern(payNymAvatarService.getValue()));
                    getChildren().add(circle);
                });
                payNymAvatarService.start();
            }
        });
    }

    public PaymentCode getPaymentCode() {
        return paymentCodeProperty.get();
    }

    public ObjectProperty<PaymentCode> paymentCodeProperty() {
        return paymentCodeProperty;
    }

    public void setPaymentCode(PaymentCode paymentCode) {
        this.paymentCodeProperty.set(paymentCode);
    }

    private class PayNymAvatarService extends Service<Image> {
        private final PaymentCode paymentCode;

        public PayNymAvatarService(PaymentCode paymentCode) {
            this.paymentCode = paymentCode;
        }

        @Override
        protected Task<Image> createTask() {
            return new Task<>() {
                @Override
                protected Image call() throws Exception {
                    String paymentCodeStr = paymentCode.toString();
                    String url = "https://paynym.is/" + paymentCodeStr + "/avatar";
                    Proxy proxy = AppServices.getProxy();

                    try(InputStream is = (proxy == null ? new URL(url).openStream() : new URL(url).openConnection(proxy).getInputStream())) {
                        return new Image(is, getWidth(), getHeight(), true, false);
                    } catch(Exception e) {
                        log.debug("Error loading PayNym avatar", e);
                        throw e;
                    }
                }
            };
        }
    }
}
