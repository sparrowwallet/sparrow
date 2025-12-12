package com.sparrowwallet.sparrow.io.keycard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class KeycardTransport implements CardChannel {
    private static final Logger log = LoggerFactory.getLogger(KeycardTransport.class);

    private final Card connection;

    KeycardTransport(byte[] appletAid) throws CardException {
        TerminalFactory tf = TerminalFactory.getDefault();
        List<CardTerminal> terminals = tf.terminals().list();
        if(terminals.isEmpty()) {
            throw new IllegalStateException("No reader connected");
        }

        Card connection = null;
        for(Iterator<CardTerminal> iter = terminals.iterator(); iter.hasNext(); ) {
            try {
                connection = getConnection(iter.next(), appletAid);
                break;
            } catch(CardException e) {
                if(!iter.hasNext()) {
                    log.info(e.getMessage());
                    throw e;
                }
            }
        }

        this.connection = connection;
    }

    private Card getConnection(CardTerminal cardTerminal, byte[] appletAid) throws CardException {
        Card connection = cardTerminal.connect("*");

        javax.smartcardio.CardChannel cardChannel = connection.getBasicChannel();
        ResponseAPDU resp = cardChannel.transmit(new CommandAPDU(0, 0xA4, 4, 0, appletAid));
        if(resp.getSW() != APDUResponse.SW_OK) {
            throw new CardException("Card initialization error, response was 0x" + Integer.toHexString(resp.getSW()));
        }

        return connection;
    }

    public APDUResponse send(APDUCommand capdu) throws IOException {
        javax.smartcardio.CardChannel cardChannel = this.connection.getBasicChannel();

        CommandAPDU cmd = new CommandAPDU(capdu.getCla(), capdu.getIns(), capdu.getP1(), capdu.getP2(), capdu.getData());
        ResponseAPDU resp;

        try {
            resp = cardChannel.transmit(cmd);
        } catch(CardException e) {
            throw new IOException(e);
        }

        return new APDUResponse(resp.getBytes());
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    void disconnect() throws CardException {
        connection.disconnect(true);
    }
}
