package com.sparrowwallet.sparrow.terminal.wallet.table;

import com.google.common.base.Strings;
import com.googlecode.lanterna.Symbols;
import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.wallet.beans.MixProgress;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;

public class MixTableCell extends TableCell {
    private static final int ERROR_DISPLAY_MILLIS = 5 * 60 * 1000;
    public static final int WIDTH = 18;

    public MixTableCell(Entry entry) {
        super(entry);
    }

    @Override
    public String formatCell() {
        if(entry instanceof UtxoEntry utxoEntry) {
            if(utxoEntry.getMixStatus() != null) {
                UtxoEntry.MixStatus mixStatus = utxoEntry.getMixStatus();
                if(mixStatus.getNextMixUtxo() != null) {
                    return getMixSuccess(mixStatus);
                } else if(mixStatus.getMixFailReason() != null) {
                    return getMixFail(mixStatus);
                } else if(mixStatus.getMixProgress() != null) {
                    return getMixProgress(mixStatus, mixStatus.getMixProgress());
                }
            }

            return getMixCountOnly(utxoEntry.getMixStatus());
        }

        return "";
    }

    private String getMixSuccess(UtxoEntry.MixStatus mixStatus) {
        String msg = "Success!";
        String mixesDone = Strings.padStart(Integer.toString(mixStatus.getMixesDone()), WIDTH - msg.length(), ' ');
        return msg + mixesDone;
    }

    private String getMixFail(UtxoEntry.MixStatus mixStatus) {
        long elapsed = mixStatus.getMixErrorTimestamp() == null ? 0L : System.currentTimeMillis() - mixStatus.getMixErrorTimestamp();
        if(mixStatus.getMixFailReason() == MixFailReason.CANCEL || elapsed >= ERROR_DISPLAY_MILLIS) {
            return getMixCountOnly(mixStatus);
        }

        String msg = mixStatus.getMixFailReason().getMessage();
        msg = msg.length() > 14 ? msg.substring(0, 14) : msg;
        String mixesDone = Strings.padStart(Integer.toString(mixStatus.getMixesDone()), WIDTH - msg.length(), ' ');
        return msg + mixesDone;
    }

    private String getMixProgress(UtxoEntry.MixStatus mixStatus, MixProgress mixProgress) {
        int progress = mixProgress.getMixStep().getProgressPercent();
        String progressBar = Strings.padEnd(Strings.repeat(Character.toString(Symbols.BLOCK_SOLID), progress / 10), 10, ' ');
        String mixesDone = Strings.padStart(Integer.toString(mixStatus.getMixesDone()), WIDTH - 10, ' ');
        return progressBar + mixesDone;
    }

    private String getMixCountOnly(UtxoEntry.MixStatus mixStatus) {
        return Strings.padStart(Integer.toString(mixStatus == null ? 0 : mixStatus.getMixesDone()), WIDTH, ' ');
    }
}
