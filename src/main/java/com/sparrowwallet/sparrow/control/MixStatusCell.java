package com.sparrowwallet.sparrow.control;

import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.wallet.beans.MixProgress;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import com.sparrowwallet.sparrow.whirlpool.WhirlpoolException;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.util.Duration;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.tools.Platform;

import java.util.Locale;

public class MixStatusCell extends TreeTableCell<Entry, UtxoEntry.MixStatus> {
    private static final int ERROR_DISPLAY_MILLIS = 5 * 60 * 1000;

    public MixStatusCell() {
        super();
        setAlignment(Pos.CENTER_RIGHT);
        setContentDisplay(ContentDisplay.LEFT);
        setGraphicTextGap(8);
        getStyleClass().add("mixstatus-cell");
    }

    @Override
    protected void updateItem(UtxoEntry.MixStatus mixStatus, boolean empty) {
        super.updateItem(mixStatus, empty);

        EntryCell.applyRowStyles(this, mixStatus == null ? null : mixStatus.getUtxoEntry());

        if(empty || mixStatus == null) {
            setText(null);
            setGraphic(null);
        } else {
            setText(Integer.toString(mixStatus.getMixesDone()));
            if(mixStatus.getNextMixUtxo() == null) {
                setContextMenu(new MixStatusContextMenu(mixStatus.getUtxoEntry(), mixStatus.getMixProgress() != null && mixStatus.getMixProgress().getMixStep() != MixStep.FAIL));
            } else {
                setContextMenu(null);
            }

            if(mixStatus.getNextMixUtxo() != null) {
                setMixSuccess(mixStatus.getNextMixUtxo());
            } else if(mixStatus.getMixFailReason() != null) {
                setMixFail(mixStatus.getMixFailReason(), mixStatus.getMixError(), mixStatus.getMixErrorTimestamp());
            } else if(mixStatus.getMixProgress() != null) {
                setMixProgress(mixStatus.getUtxoEntry(), mixStatus.getMixProgress());
            } else {
                setGraphic(null);
                setTooltip(null);
            }
        }
    }

    private void setMixSuccess(Utxo nextMixUtxo) {
        ProgressIndicator progressIndicator = getProgressIndicator();
        progressIndicator.setProgress(-1);
        setGraphic(progressIndicator);
        Tooltip tt = new Tooltip();
        tt.setText("Waiting for broadcast of " + nextMixUtxo.getHash().substring(0, 8) + "..." + ":" + nextMixUtxo.getIndex() );
        setTooltip(tt);
    }

    private void setMixFail(MixFailReason mixFailReason, String mixError, Long mixErrorTimestamp) {
        if(mixFailReason != MixFailReason.CANCEL) {
            long elapsed = mixErrorTimestamp == null ? 0L : System.currentTimeMillis() - mixErrorTimestamp;
            if(elapsed >= ERROR_DISPLAY_MILLIS) {
                //Old error, don't set again.
                return;
            }

            Glyph failGlyph = getFailGlyph();
            setGraphic(failGlyph);
            Tooltip tt = new Tooltip();
            tt.setText(mixFailReason.getMessage() + (mixError == null ? "" : ": " + mixError) +
                    "\nMix failures are generally caused by peers disconnecting during a mix." +
                    "\nMake sure your internet connection is stable and the computer is configured to prevent sleeping." +
                    "\nTo prevent sleeping, use the " + getPlatformSleepConfig() + " or enable the function in the Tools menu.");
            setTooltip(tt);

            Duration fadeDuration = Duration.millis(ERROR_DISPLAY_MILLIS - elapsed);
            double fadeFromValue = 1.0 - ((double)elapsed / ERROR_DISPLAY_MILLIS);
            Timeline timeline = AnimationUtil.getSlowFadeOut(failGlyph, fadeDuration, fadeFromValue, 10);
            timeline.setOnFinished(event -> {
                setTooltip(null);
            });
            timeline.play();
        } else {
            setGraphic(null);
            setTooltip(null);
        }
    }

    private String getPlatformSleepConfig() {
        Platform platform = Platform.getCurrent();
        if(platform == Platform.OSX) {
            return "OSX System Preferences";
        } else if(platform == Platform.WINDOWS) {
            return "Windows Control Panel";
        }

        return "system power settings";
    }

    private void setMixProgress(UtxoEntry utxoEntry, MixProgress mixProgress) {
        if(mixProgress.getMixStep() != MixStep.FAIL) {
            ProgressIndicator progressIndicator = getProgressIndicator();
            progressIndicator.setProgress(mixProgress.getMixStep().getProgressPercent() == 100 ? -1 : mixProgress.getMixStep().getProgressPercent() / 100.0);
            setGraphic(progressIndicator);
            Tooltip tt = new Tooltip();
            String status = mixProgress.getMixStep().getMessage().substring(0, 1).toUpperCase(Locale.ROOT) + mixProgress.getMixStep().getMessage().substring(1);
            tt.setText(status);
            setTooltip(tt);

            if(mixProgress.getMixStep() == MixStep.REGISTERED_INPUT) {
                tt.setOnShowing(event -> {
                    Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(utxoEntry.getWallet());
                    Whirlpool.RegisteredInputsService registeredInputsService = new Whirlpool.RegisteredInputsService(whirlpool, mixProgress.getPoolId());
                    registeredInputsService.setOnSucceeded(eventStateHandler -> {
                        if(registeredInputsService.getValue() != null) {
                            tt.setText(status + " (1 of " + registeredInputsService.getValue() + ")");
                        }
                    });
                    registeredInputsService.start();
                });
            }
        } else {
            setGraphic(null);
            setTooltip(null);
        }
    }

    private ProgressIndicator getProgressIndicator() {
        ProgressIndicator progressIndicator;
        if(getGraphic() instanceof ProgressIndicator) {
            progressIndicator = (ProgressIndicator)getGraphic();
        } else {
            progressIndicator = new ProgressBar();
        }

        return progressIndicator;
    }

    private static Glyph getMixGlyph() {
        Glyph copyGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.RANDOM);
        copyGlyph.setFontSize(12);
        return copyGlyph;
    }

    private static Glyph getStopGlyph() {
        Glyph copyGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.STOP_CIRCLE);
        copyGlyph.setFontSize(12);
        return copyGlyph;
    }

    public static Glyph getFailGlyph() {
        Glyph failGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
        failGlyph.getStyleClass().add("fail-warning");
        failGlyph.setFontSize(12);
        return failGlyph;
    }

    private static class MixStatusContextMenu extends ContextMenu {
        public MixStatusContextMenu(UtxoEntry utxoEntry, boolean isMixing) {
            Whirlpool pool = AppServices.getWhirlpoolServices().getWhirlpool(utxoEntry.getWallet());
            if(isMixing) {
                MenuItem mixStop = new MenuItem("Stop Mixing");
                if(pool != null) {
                    mixStop.disableProperty().bind(pool.mixingProperty().not());
                }
                mixStop.setGraphic(getStopGlyph());
                mixStop.setOnAction(event -> {
                    hide();
                    Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(utxoEntry.getWallet());
                    if(whirlpool != null) {
                        try {
                            whirlpool.mixStop(utxoEntry.getHashIndex());
                        } catch(WhirlpoolException e) {
                            AppServices.showErrorDialog("Error stopping mixing UTXO", e.getMessage());
                        }
                    }
                });
                getItems().add(mixStop);
            } else {
                MenuItem mixNow = new MenuItem("Mix Now");
                if(pool != null) {
                    mixNow.disableProperty().bind(pool.mixingProperty().not());
                }

                mixNow.setGraphic(getMixGlyph());
                mixNow.setOnAction(event -> {
                    hide();
                    Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(utxoEntry.getWallet());
                    if(whirlpool != null) {
                        try {
                            whirlpool.mix(utxoEntry.getHashIndex());
                        } catch(WhirlpoolException e) {
                            AppServices.showErrorDialog("Error mixing UTXO", e.getMessage());
                        }
                    }
                });
                getItems().add(mixNow);
            }
        }
    }
}
