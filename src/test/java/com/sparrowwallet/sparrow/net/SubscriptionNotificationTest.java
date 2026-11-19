package com.sparrowwallet.sparrow.net;

import org.junit.jupiter.api.Test;

import static com.sparrowwallet.sparrow.net.TcpTransport.MessageType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubscriptionNotificationTest {
    //Single quotes in the message stand for double quotes, for readability
    private static TcpTransport.MessageType type(String message) {
        return TcpTransport.getMessageType(message.replace('\'', '"'));
    }

    private static String notification(String method, String params) {
        return "{'jsonrpc':'2.0','method':'blockchain." + method + "'" + (params == null ? "" : ",'params':" + params) + "}";
    }

    @Test
    public void acceptsSubscriptionNotifications() {
        assertEquals(SUBSCRIPTION_NOTIFICATION, type(notification("headers.subscribe", "[{'height':1,'hex':'00'}]")));
        assertEquals(SUBSCRIPTION_NOTIFICATION, type(notification("scripthash.subscribe", "['ab','cd']")));
        assertEquals(SUBSCRIPTION_NOTIFICATION, type(notification("scripthash.subscribe", "['ab']")));
        assertEquals(SUBSCRIPTION_NOTIFICATION, type("{'params':['ab',null],'method':'blockchain.scripthash.subscribe','jsonrpc':'2.0'}"));
        assertEquals(SUBSCRIPTION_NOTIFICATION, type(notification("silentpayments.subscribe", "[{'address':'sp'},0.5,[]]")));
        assertEquals(SUBSCRIPTION_NOTIFICATION, type(notification("silentpayments.subscribe", "{'subscription':{},'progress':1.0,'history':[]}")));
    }

    @Test
    public void refusesOtherNotifications() {
        assertEquals(OTHER_NOTIFICATION, type(notification("unknown", "[]")));
        assertEquals(OTHER_NOTIFICATION, type(notification("scripthash.subscribe", "[]")));
        assertEquals(OTHER_NOTIFICATION, type(notification("scripthash.subscribe", "['ab','cd','ef']")));
        assertEquals(OTHER_NOTIFICATION, type(notification("scripthash.subscribe", "'ab'")));
        assertEquals(OTHER_NOTIFICATION, type(notification("scripthash.subscribe", null)));
        assertEquals(OTHER_NOTIFICATION, type(notification("silentpayments.subscribe", "{'subscription':{},'other':1}")));
        assertEquals(OTHER_NOTIFICATION, type(notification("headers.subscribe", "[{},{}]")));
    }

    @Test
    public void classifiesResponses() {
        assertEquals(RESPONSE, type("{'jsonrpc':'2.0','id':1,'result':['ab']}"));
        assertEquals(RESPONSE, type("not json"));
    }
}
