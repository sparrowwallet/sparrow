package com.sparrowwallet.sparrow.joinstr;

import nostr.api.NIP01;
import nostr.event.BaseTag;
import nostr.event.impl.GenericEvent;
import nostr.id.Identity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NostrPublisher {

    private static final Identity SENDER = Identity.generateRandomIdentity();

    private static final Map<String, String> RELAYS = Map.of(
            "nos", "wss://nos.lol"
    );

    public static void main(String[] args) {
        String defaultDenomination = "100000";
        String defaultPeers = "5";
        GenericEvent event = publishCustomEvent(defaultDenomination, defaultPeers);
        if (event != null) {
            System.out.println("Event ID: " + event.getId());
        }
    }

    public static GenericEvent publishCustomEvent(String denomination, String peers) {
        try {
            System.out.println("Public key: " + SENDER.getPublicKey().toString());
            System.out.println("Private key: " + SENDER.getPrivateKey().toString());

            Identity poolIdentity = Identity.generateRandomIdentity();

            long timeout = Instant.now().getEpochSecond() + 3600;

            List<BaseTag> tags = new ArrayList<>();
            String poolId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            String content = String.format(
                    "{\n" +
                            "  \"type\": \"new_pool\",\n" +
                            "  \"id\": \"%s\",\n" +
                            "  \"public_key\": \"%s\",\n" +
                            "  \"denomination\": %s,\n" +
                            "  \"peers\": %s,\n" +
                            "  \"timeout\": %d,\n" +
                            "  \"relay\": \"%s\",\n" +
                            "  \"fee_rate\": 1,\n" +
                            "  \"transport\": \"tor\",\n" +
                            "  \"vpn_gateway\": null\n" +
                            "}",
                    poolId,
                    poolIdentity.getPublicKey().toString(),
                    denomination,
                    peers,
                    timeout,
                    RELAYS.values().iterator().next()
            );

            NIP01 nip01 = new NIP01(SENDER);

            GenericEvent event = new GenericEvent(
                    SENDER.getPublicKey(),
                    2022,
                    tags,
                    content
            );

            nip01.setEvent(event);
            nip01.sign();

            nip01.send(RELAYS);

            if (event != null) {
                System.out.println("Event ID: " + event.getId());
                System.out.println("Event: " + event.toString());
            }

            return event;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}