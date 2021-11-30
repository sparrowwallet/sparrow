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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PayNymAvatar extends StackPane {
    private static final Logger log = LoggerFactory.getLogger(PayNymAvatar.class);

    private final ObjectProperty<PaymentCode> paymentCodeProperty = new SimpleObjectProperty<>(null);

    private static final Map<String, Image> paymentCodeCache = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, Object> paymentCodeLoading = Collections.synchronizedMap(new HashMap<>());

    public PayNymAvatar() {
        super();

        paymentCodeProperty.addListener((observable, oldValue, paymentCode) -> {
            if(paymentCode == null) {
                getChildren().clear();
            } else if(Config.get().isUsePayNym() && (oldValue == null || !oldValue.toString().equals(paymentCode.toString()))) {
                String cacheId = getCacheId(paymentCode, getPrefWidth());
                if(paymentCodeCache.containsKey(cacheId)) {
                    setImage(paymentCodeCache.get(cacheId));
                } else {
                    PayNymAvatarService payNymAvatarService = new PayNymAvatarService(paymentCode, getPrefWidth());
                    payNymAvatarService.setOnRunning(runningEvent -> {
                        getChildren().clear();
                    });
                    payNymAvatarService.setOnSucceeded(successEvent -> {
                        setImage(payNymAvatarService.getValue());
                    });
                    payNymAvatarService.setOnFailed(failedEvent -> {
                        log.error("Error", failedEvent.getSource().getException());
                    });
                    payNymAvatarService.start();
                }
            }
        });
    }

    private void setImage(Image image) {
        getChildren().clear();
        Circle circle = new Circle(getPrefWidth() / 2,getPrefHeight() / 2,getPrefWidth() / 2);
        circle.setFill(new ImagePattern(image));
        getChildren().add(circle);
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

    private static String getCacheId(PaymentCode paymentCode, double width) {
        return paymentCode.toString();
    }

    private static class PayNymAvatarService extends Service<Image> {
        private final PaymentCode paymentCode;
        private final double width;

        public PayNymAvatarService(PaymentCode paymentCode, double width) {
            this.paymentCode = paymentCode;
            this.width = width;
        }

        @Override
        protected Task<Image> createTask() {
            return new Task<>() {
                @Override
                protected Image call() throws Exception {
                    String paymentCodeStr = paymentCode.toString();
                    String cacheId = getCacheId(paymentCode, width);

                    Object lock = paymentCodeLoading.get(cacheId);
                    if(lock != null) {
                        synchronized(lock) {
                            if(paymentCodeCache.containsKey(cacheId)) {
                                return paymentCodeCache.get(cacheId);
                            }
                        }
                    } else {
                        lock = new Object();
                        paymentCodeLoading.put(cacheId, lock);
                    }

                    synchronized(lock) {
                        String url = "https://paynym.is/" + paymentCodeStr + "/avatar";
                        Proxy proxy = AppServices.getProxy();

                        try(InputStream is = (proxy == null ? new URL(url).openStream() : new URL(url).openConnection(proxy).getInputStream())) {
                            Image image = new Image(is, 150, 150, true, false);
                            paymentCodeCache.put(cacheId, image);
                            return image;
                        } catch(Exception e) {
                            log.debug("Error loading PayNym avatar", e);
                            throw e;
                        } finally {
                            lock.notifyAll();
                        }
                    }
                }
            };
        }
    }
}
