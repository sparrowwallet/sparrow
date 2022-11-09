package com.sparrowwallet.sparrow.control;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TrayManager {
    private static final Logger log = LoggerFactory.getLogger(TrayManager.class);

    private final SystemTray tray;
    private final TrayIcon trayIcon;
    private final PopupMenu popupMenu = new PopupMenu();

    public TrayManager() {
        if(!SystemTray.isSupported()) {
            throw new UnsupportedOperationException("SystemTray icons are not supported by the current desktop environment.");
        }

        tray = SystemTray.getSystemTray();

        try {
            List<Image> imgList = new ArrayList<>();
            if(org.controlsfx.tools.Platform.getCurrent() == org.controlsfx.tools.Platform.WINDOWS) {
                imgList.add(ImageIO.read(getClass().getResource("/image/sparrow-black-small.png")));
                imgList.add(ImageIO.read(getClass().getResource("/image/sparrow-black-small@2x.png")));
                imgList.add(ImageIO.read(getClass().getResource("/image/sparrow-black-small@3x.png")));
            } else {
                imgList.add(ImageIO.read(getClass().getResource("/image/sparrow-white-small.png")));
                imgList.add(ImageIO.read(getClass().getResource("/image/sparrow-white-small@2x.png")));
                imgList.add(ImageIO.read(getClass().getResource("/image/sparrow-white-small@3x.png")));
            }

            BaseMultiResolutionImage mrImage = new BaseMultiResolutionImage(imgList.toArray(new Image[0]));

            this.trayIcon = new TrayIcon(mrImage, "Sparrow", popupMenu);

            MenuItem miExit = new MenuItem("Quit Sparrow");
            miExit.addActionListener(e -> {
                SwingUtilities.invokeLater(() -> { tray.remove(this.trayIcon); });
                Platform.exit();
            });
            this.popupMenu.add(miExit);
        } catch(IOException e) {
            log.error("Could not load system tray image", e);
            throw new IllegalStateException(e);
        }
    }

    public void addStage(Stage stage) {
        EventQueue.invokeLater(() -> {
            MenuItem miStage = new MenuItem(stage.getTitle());
            miStage.setFont(Font.decode(null).deriveFont(Font.BOLD));
            miStage.addActionListener(e -> Platform.runLater(() -> {
                stage.show();
                stage.setAlwaysOnTop(true);
                stage.setAlwaysOnTop(false);
                EventQueue.invokeLater(() -> {
                    popupMenu.remove(miStage);

                    if(popupMenu.getItemCount() == 1) {
                        Platform.setImplicitExit(true);
                        SwingUtilities.invokeLater(() -> tray.remove(trayIcon));
                    }
                });
            }));
            //Make sure it's always at the top
            this.popupMenu.insert(miStage,popupMenu.getItemCount() - 1);

            if(!isShowing()) {
                // Keeps the JVM running even if there are no
                // visible JavaFX Stages, otherwise JVM would
                // exit and we lose the TrayIcon
                Platform.setImplicitExit(false);

                SwingUtilities.invokeLater(() -> {
                    try {
                        tray.add(this.trayIcon);
                    } catch(AWTException e) {
                        log.error("Unable to add system tray icon", e);
                    }
                });
            }
        });
    }

    public boolean isShowing() {
        return Arrays.stream(tray.getTrayIcons()).collect(Collectors.toList()).contains(trayIcon);
    }

    public static boolean isSupported() {
        return Desktop.isDesktopSupported() && SystemTray.isSupported();
    }
}
