package com.sparrowwallet.sparrow.io.satochip;

import com.sparrowwallet.drongo.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.*;
import javax.smartcardio.CardChannel;
import java.util.List;
import java.util.*;

public class SatoCardTransport {
    private static final Logger log = LoggerFactory.getLogger(SatoCardTransport.class);

    private static final int SW_OKAY = 0x9000;

    private final Card connection;

    SatoCardTransport(byte[] applet_aid) throws CardException {
        TerminalFactory tf = TerminalFactory.getDefault();
        List<CardTerminal> terminals = tf.terminals().list();
        if(terminals.isEmpty()) {
            throw new IllegalStateException("No reader connected");
        }

        Card connection = null;
        for(Iterator<CardTerminal> iter = terminals.iterator(); iter.hasNext(); ) {
            try {
                connection = getConnection(iter.next(), applet_aid);
                break;
            } catch(CardException e) {
                if(!iter.hasNext()) {
                    log.error(e.getMessage());
                    throw e;
                }
            }
        }

        this.connection = connection;
    }

    private Card getConnection(CardTerminal cardTerminal, byte[] applet_aid) throws CardException {
        Card connection = cardTerminal.connect("*");

        CardChannel cardChannel = connection.getBasicChannel();
        ResponseAPDU resp = cardChannel.transmit(new CommandAPDU(0, 0xA4, 4, 0, applet_aid));
        if(resp.getSW() != SW_OKAY) {
            throw new CardException("Card initialization error, response was 0x" + Integer.toHexString(resp.getSW()));
        }

        return connection;
    }

    APDUResponse send(APDUCommand capdu) throws CardException{

        javax.smartcardio.CardChannel cardChannel = this.connection.getBasicChannel();

        // todo: convert APDUCommand to CommansdApdu??
        log.trace("SATOCHIP SatoCardTransport send capdu:" + capdu.toHexString());
        CommandAPDU cmd = new CommandAPDU(capdu.getCla(), capdu.getIns(), capdu.getP1(), capdu.getP2(), capdu.getData());
        ResponseAPDU resp = cardChannel.transmit(cmd);

        // convert back to APDUResponse... (todo?)
        APDUResponse rapdu = new APDUResponse(resp.getBytes());
        log.trace("SATOCHIP SatoCardTransport send rapdu:" + rapdu.toHexString());
        return rapdu;
    }

    void disconnect() throws CardException {
        connection.disconnect(true);
    }

}
