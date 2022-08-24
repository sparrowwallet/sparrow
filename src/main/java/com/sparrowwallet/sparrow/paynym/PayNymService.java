package com.sparrowwallet.sparrow.paynym;

import com.google.common.net.HostAndPort;
import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClient;
import com.sparrowwallet.drongo.bip47.InvalidPaymentCodeException;
import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.nightjar.http.JavaHttpClientService;
import io.reactivex.Observable;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import java8.util.Optional;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class PayNymService {
    private static final Logger log = LoggerFactory.getLogger(PayNymService.class);

    private final JavaHttpClientService httpClientService;

    public PayNymService(HostAndPort torProxy) {
        this.httpClientService = new JavaHttpClientService(torProxy);
    }

    public Observable<Map<String, Object>> createPayNym(Wallet wallet) {
        return createPayNym(getPaymentCode(wallet));
    }

    public Observable<Map<String, Object>> createPayNym(PaymentCode paymentCode) {
        if(paymentCode == null) {
            throw new IllegalStateException("Payment code is null");
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");

        HashMap<String, Object> body = new HashMap<>();
        body.put("code", paymentCode.toString());

        String url = getHostUrl() + "/api/v1/create";
        if(log.isInfoEnabled()) {
            log.info("Creating PayNym using " + url);
        }

        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        return httpClient.postJson(url, Map.class, headers, body)
                .subscribeOn(Schedulers.io())
                .observeOn(JavaFxScheduler.platform())
                .map(Optional::get);
    }

    public Observable<Map<String, Object>> updateToken(PaymentCode paymentCode) {
        if(paymentCode == null) {
            throw new IllegalStateException("Payment code is null");
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");

        HashMap<String, Object> body = new HashMap<>();
        body.put("code", paymentCode.toString());

        String url = getHostUrl() + "/api/v1/token";
        if(log.isInfoEnabled()) {
            log.info("Updating PayNym token using " + url);
        }

        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        return httpClient.postJson(url, Map.class, headers, body)
                .subscribeOn(Schedulers.io())
                .observeOn(JavaFxScheduler.platform())
                .map(Optional::get);
    }

    public void claimPayNym(Wallet wallet, Map<String, Object> createMap, boolean segwit) {
        if(createMap.get("claimed") == Boolean.FALSE) {
            getAuthToken(wallet, createMap).subscribe(authToken -> {
                String signature = getSignature(wallet, authToken);
                claimPayNym(authToken, signature).subscribe(claimMap -> {
                    log.debug("Claimed payment code " + claimMap.get("claimed"));
                    addPaymentCode(getPaymentCode(wallet), authToken, signature, segwit).subscribe(addMap -> {
                        log.debug("Added payment code " + addMap);
                    });
                }, error -> {
                    getAuthToken(wallet, new HashMap<>()).subscribe(newAuthToken -> {
                        String newSignature = getSignature(wallet, newAuthToken);
                        claimPayNym(newAuthToken, newSignature).subscribe(claimMap -> {
                            log.debug("Claimed payment code " + claimMap.get("claimed"));
                            addPaymentCode(getPaymentCode(wallet), newAuthToken, newSignature, segwit).subscribe(addMap -> {
                                log.debug("Added payment code " + addMap);
                            });
                        }, newError -> {
                            log.error("Error claiming PayNym with new authToken", newError);
                        });
                    }, newError -> {
                        log.error("Error retrieving new authToken", newError);
                    });
                });
            }, error -> {
                log.error("Error retrieving authToken", error);
            });
        }
    }

    private Observable<Map<String, Object>> claimPayNym(String authToken, String signature) {
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");
        headers.put("auth-token", authToken);

        HashMap<String, Object> body = new HashMap<>();
        body.put("signature", signature);

        String url = getHostUrl() + "/api/v1/claim";
        if(log.isInfoEnabled()) {
            log.info("Claiming PayNym using " + url);
        }

        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        return httpClient.postJson(url, Map.class, headers, body)
                .subscribeOn(Schedulers.io())
                .observeOn(JavaFxScheduler.platform())
                .map(Optional::get);
    }

    public Observable<Map<String, Object>> addPaymentCode(PaymentCode paymentCode, String authToken, String signature, boolean segwit) {
        String strPaymentCode;
        try {
            strPaymentCode = segwit ? paymentCode.makeSamouraiPaymentCode() : paymentCode.toString();
        } catch(InvalidPaymentCodeException e) {
            log.warn("Error creating segwit enabled payment code", e);
            strPaymentCode = paymentCode.toString();
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");
        headers.put("auth-token", authToken);

        HashMap<String, Object> body = new HashMap<>();
        body.put("nym", paymentCode.toString());
        body.put("code", strPaymentCode);
        body.put("signature", signature);

        String url = getHostUrl() + "/api/v1/nym/add";
        if(log.isInfoEnabled()) {
            log.info("Adding payment code to PayNym using " + url);
        }

        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        return httpClient.postJson(url, Map.class, headers, body)
                .subscribeOn(Schedulers.io())
                .observeOn(JavaFxScheduler.platform())
                .map(Optional::get);
    }

    public Observable<Map<String, Object>> followPaymentCode(com.samourai.wallet.bip47.rpc.PaymentCode paymentCode, String authToken, String signature) {
        return followPaymentCode(PaymentCode.fromString(paymentCode.toString()), authToken, signature);
    }

    public Observable<Map<String, Object>> followPaymentCode(PaymentCode paymentCode, String authToken, String signature) {
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");
        headers.put("auth-token", authToken);

        HashMap<String, Object> body = new HashMap<>();
        body.put("signature", signature);
        body.put("target", paymentCode.toString());

        String url = getHostUrl() + "/api/v1/follow";
        if(log.isInfoEnabled()) {
            log.info("Following payment code using " + url);
        }

        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        return httpClient.postJson(url, Map.class, headers, body)
                .subscribeOn(Schedulers.io())
                .observeOn(JavaFxScheduler.platform())
                .map(Optional::get);
    }

    public Observable<Map<String, Object>> fetchPayNym(String nymIdentifier, boolean compact) {
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");

        HashMap<String, Object> body = new HashMap<>();
        body.put("nym", nymIdentifier);

        String url = getHostUrl() + "/api/v1/nym" + (compact ? "?compact=true" : "");
        if(log.isInfoEnabled()) {
            log.info("Fetching PayNym using " + url);
        }

        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        return httpClient.postJson(url, Map.class, headers, body)
                .subscribeOn(Schedulers.io())
                .observeOn(JavaFxScheduler.platform())
                .map(Optional::get);
    }

    public Observable<PayNym> getPayNym(String nymIdentifier) {
        return getPayNym(nymIdentifier, false);
    }

    public Observable<PayNym> getPayNym(String nymIdentifier, boolean compact) {
        return fetchPayNym(nymIdentifier, compact).map(nymMap -> {
            List<Map<String, Object>> codes = (List<Map<String, Object>>)nymMap.get("codes");
            PaymentCode code = new PaymentCode((String)codes.stream().filter(codeMap -> codeMap.get("segwit") == Boolean.FALSE).map(codeMap -> codeMap.get("code")).findFirst().orElse(codes.get(0).get("code")));

            if(compact) {
                return new PayNym(code, (String)nymMap.get("nymID"), (String)nymMap.get("nymName"), (Boolean)nymMap.get("segwit"), Collections.emptyList(), Collections.emptyList());
            }

            List<Map<String, Object>> followingMaps = (List<Map<String, Object>>)nymMap.get("following");
            List<PayNym> following = followingMaps.stream().map(followingMap -> {
                return PayNym.fromString((String)followingMap.get("code"), (String)followingMap.get("nymId"), (String)followingMap.get("nymName"), (Boolean)followingMap.get("segwit"), Collections.emptyList(), Collections.emptyList());
            }).collect(Collectors.toList());

            List<Map<String, Object>> followersMaps = (List<Map<String, Object>>)nymMap.get("followers");
            List<PayNym> followers = followersMaps.stream().map(followerMap -> {
                return PayNym.fromString((String)followerMap.get("code"), (String)followerMap.get("nymId"), (String)followerMap.get("nymName"), (Boolean)followerMap.get("segwit"), Collections.emptyList(), Collections.emptyList());
            }).collect(Collectors.toList());

            return new PayNym(code, (String)nymMap.get("nymID"), (String)nymMap.get("nymName"), (Boolean)nymMap.get("segwit"), following, followers);
        });
    }

    public Observable<String> getAuthToken(Wallet wallet, Map<String, Object> map) {
        if(map.containsKey("token")) {
            return Observable.just((String)map.get("token"));
        }

        return updateToken(wallet).map(tokenMap -> (String)tokenMap.get("token"));
    }

    public Observable<Map<String, Object>> updateToken(Wallet wallet) {
        return updateToken(getPaymentCode(wallet));
    }

    public String getSignature(Wallet wallet, String authToken) {
        Wallet masterWallet = wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
        Keystore keystore = masterWallet.getKeystores().get(0);
        List<ChildNumber> derivation = keystore.getKeyDerivation().getDerivation();
        ChildNumber derivationStart = derivation.isEmpty() ? ChildNumber.ZERO_HARDENED : derivation.get(derivation.size() - 1);
        ECKey notificationPrivKey = keystore.getBip47ExtendedPrivateKey().getKey(List.of(derivationStart, new ChildNumber(0)));
        return notificationPrivKey.signMessage(authToken, ScriptType.P2PKH);
    }

    private PaymentCode getPaymentCode(Wallet wallet) {
        Wallet masterWallet = wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
        return masterWallet.getPaymentCode();
    }

    public HostAndPort getTorProxy() {
        return httpClientService.getTorProxy();
    }

    public void setTorProxy(HostAndPort torProxy) {
        //Ensure all http clients are shutdown first
        httpClientService.shutdown();
        httpClientService.setTorProxy(torProxy);
    }

    private String getHostUrl() {
        return getHostUrl(getTorProxy() != null);
    }

    public static String getHostUrl(boolean tor) {
        return tor ? "http://paynym7bwekdtb2hzgkpl6y2waqcrs2dii7lwincvxme7mdpcpxzfsad.onion" : "https://paynym.is";
    }

    public void shutdown() {
        httpClientService.shutdown();
    }

    public static class ShutdownService extends Service<Boolean> {
        private final PayNymService payNymService;

        public ShutdownService(PayNymService payNymService) {
            this.payNymService = payNymService;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() throws Exception {
                    payNymService.shutdown();
                    return true;
                }
            };
        }
    }
}
