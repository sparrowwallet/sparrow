package com.sparrowwallet.sparrow.io.satochip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import javax.smartcardio.CardChannel;
import java.util.List;
import java.util.*;

public class SatoCardTransport {
    private static final Logger log = LoggerFactory.getLogger(SatoCardTransport.class);

    private final Card connection;

    SatoCardTransport(byte[] appletAid) throws CardException {
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

        CardChannel cardChannel = connection.getBasicChannel();
        ResponseAPDU resp = cardChannel.transmit(new CommandAPDU(0, 0xA4, 4, 0, appletAid));
        if(resp.getSW() != APDUResponse.SW_OK) {
            throw new CardException("Card initialization error, response was 0x" + Integer.toHexString(resp.getSW()));
        }

        return connection;
    }

    APDUResponse send(APDUCommand capdu) throws CardException {
        javax.smartcardio.CardChannel cardChannel = this.connection.getBasicChannel();

        CommandAPDU cmd = new CommandAPDU(capdu.getCla(), capdu.getIns(), capdu.getP1(), capdu.getP2(), capdu.getData());
        ResponseAPDU resp = cardChannel.transmit(cmd);

        return new APDUResponse(resp.getBytes());
    }

    void disconnect() throws CardException {
        connection.disconnect(true);
    }
}
