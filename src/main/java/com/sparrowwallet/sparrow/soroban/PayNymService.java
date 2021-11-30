package com.sparrowwallet.sparrow.soroban;

import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.IHttpClient;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.sparrowwallet.nightjar.http.JavaHttpClientService;
import io.reactivex.Observable;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import java8.util.Optional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class PayNymService {
    private final JavaHttpClientService httpClientService;

    public PayNymService(JavaHttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    public Observable<Map<String, Object>> createPayNym(PaymentCode paymentCode) {
        if(paymentCode == null) {
            throw new IllegalStateException("Payment code is null");
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");

        HashMap<String, Object> body = new HashMap<>();
        body.put("code", paymentCode.toString());

        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        return httpClient.postJson("https://paynym.is/api/v1/create", Map.class, headers, body)
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

        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        return httpClient.postJson("https://paynym.is/api/v1/token", Map.class, headers, body)
                .subscribeOn(Schedulers.io())
                .observeOn(JavaFxScheduler.platform())
                .map(Optional::get);
    }

    public Observable<Map<String, Object>> claimPayNym(String authToken, String signature) {
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");
        headers.put("auth-token", authToken);

        HashMap<String, Object> body = new HashMap<>();
        body.put("signature", signature);

        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        return httpClient.postJson("https://paynym.is/api/v1/claim", Map.class, headers, body)
                .subscribeOn(Schedulers.io())
                .observeOn(JavaFxScheduler.platform())
                .map(Optional::get);
    }

    public Observable<Map<String, Object>> addSamouraiPaymentCode(PaymentCode paymentCode, String authToken, String signature) {
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");
        headers.put("auth-token", authToken);

        HashMap<String, Object> body = new HashMap<>();
        body.put("nym", paymentCode.toString());
        body.put("code", paymentCode.makeSamouraiPaymentCode());
        body.put("signature", signature);

        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        return httpClient.postJson("https://paynym.is/api/v1/nym/add", Map.class, headers, body)
                .subscribeOn(Schedulers.io())
                .observeOn(JavaFxScheduler.platform())
                .map(Optional::get);
    }

    public Observable<Map<String, Object>> followPaymentCode(PaymentCode paymentCode, String authToken, String signature) {
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");
        headers.put("auth-token", authToken);

        HashMap<String, Object> body = new HashMap<>();
        body.put("signature", signature);
        body.put("target", paymentCode.toString());

        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        return httpClient.postJson("https://paynym.is/api/v1/follow", Map.class, headers, body)
                .subscribeOn(Schedulers.io())
                .observeOn(JavaFxScheduler.platform())
                .map(Optional::get);
    }

    public Observable<Map<String, Object>> fetchPayNym(String nymIdentifier) {
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");

        HashMap<String, Object> body = new HashMap<>();
        body.put("nym", nymIdentifier);

        IHttpClient httpClient = httpClientService.getHttpClient(HttpUsage.COORDINATOR_REST);
        return httpClient.postJson("https://paynym.is/api/v1/nym", Map.class, headers, body)
                .subscribeOn(Schedulers.io())
                .observeOn(JavaFxScheduler.platform())
                .map(Optional::get);
    }

    public Observable<PayNym> getPayNym(String nymIdentifier) {
        return fetchPayNym(nymIdentifier).map(nymMap -> {
            List<Map<String, Object>> codes = (List<Map<String, Object>>)nymMap.get("codes");
            PaymentCode code = new PaymentCode((String)codes.stream().filter(codeMap -> codeMap.get("segwit") == Boolean.FALSE).map(codeMap -> codeMap.get("code")).findFirst().orElse(codes.get(0).get("code")));

            List<Map<String, Object>> followingMaps = (List<Map<String, Object>>)nymMap.get("following");
            List<PayNym> following = followingMaps.stream().map(followingMap -> {
                return new PayNym(new PaymentCode((String)followingMap.get("code")), (String)followingMap.get("nymId"), (String)followingMap.get("nymName"), (Boolean)followingMap.get("segwit"), Collections.emptyList(), Collections.emptyList());
            }).collect(Collectors.toList());

            List<Map<String, Object>> followersMaps = (List<Map<String, Object>>)nymMap.get("followers");
            List<PayNym> followers = followersMaps.stream().map(followerMap -> {
                return new PayNym(new PaymentCode((String)followerMap.get("code")), (String)followerMap.get("nymId"), (String)followerMap.get("nymName"), (Boolean)followerMap.get("segwit"), Collections.emptyList(), Collections.emptyList());
            }).collect(Collectors.toList());

            return new PayNym(code, (String)nymMap.get("nymID"), (String)nymMap.get("nymName"), (Boolean)nymMap.get("segwit"), following, followers);
        });
    }
}
