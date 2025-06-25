package com.sparrowwallet.sparrow.io;

import io.github.doblon8.jzbar.Config;
import io.github.doblon8.jzbar.Image;
import io.github.doblon8.jzbar.ImageScanner;
import io.github.doblon8.jzbar.SymbolType;
import com.sparrowwallet.sparrow.net.NativeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public class ZBar {
    private static final Logger log = LoggerFactory.getLogger(ZBar.class);

    private final static boolean enabled;

    static { // static initializer
        if(com.sparrowwallet.sparrow.io.Config.get().isUseZbar()) {
            enabled = loadLibrary();
        } else {
            enabled = false;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static Scan scan(BufferedImage bufferedImage) {
        try {
            BufferedImage grayscale = new BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g2d = (Graphics2D)grayscale.getGraphics();
            g2d.drawImage(bufferedImage, 0, 0, null);
            g2d.dispose();

            byte[] data = convertToY800(grayscale);

            try(Image image = new Image()) {
                image.setSize(grayscale.getWidth(), grayscale.getHeight());
                image.setFormat("Y800");
                image.setData(data);

                try(ImageScanner scanner = new ImageScanner()) {
                    scanner.setConfig(SymbolType.NONE, Config.ENABLE, 0);
                    scanner.setConfig(SymbolType.QRCODE, Config.ENABLE, 1);
                    int result = scanner.scanImage(image);
                    if(result != 0) {
                        String symbolData = image.getFirstSymbol().getData();
                        return new Scan(getRawBytes(symbolData), symbolData);
                    }
                }
            }
        } catch(Exception e) {
            log.debug("Error scanning with ZBar", e);
        }

        return null;
    }

    private static byte[] convertToY800(BufferedImage image) {
        // Ensure the image is grayscale
        if (image.getType() != BufferedImage.TYPE_BYTE_GRAY) {
            throw new IllegalArgumentException("Input image must be grayscale");
        }

        // Get the underlying byte array of the image data
        byte[] imageData = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

        // Check if the image size is even
        int width = image.getWidth();
        int height = image.getHeight();
        if (width % 2 != 0 || height % 2 != 0) {
            throw new IllegalArgumentException("Image dimensions must be even");
        }

        // Prepare the output byte array in Y800 format
        byte[] outputData = new byte[width * height];
        int outputIndex = 0;

        // Convert the grayscale image data to Y800 format
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = imageData[y * width + x] & 0xFF; // Extract the grayscale value

                // Write the grayscale value to the output byte array
                outputData[outputIndex++] = (byte) pixel;
            }
        }

        return outputData;
    }

    private static boolean loadLibrary() {
        try {
            String osName = System.getProperty("os.name");
            String osArch = System.getProperty("os.arch");
            if(osName.startsWith("Mac") && osArch.equals("aarch64")) {
                NativeUtils.loadLibraryFromJar("/native/osx/aarch64/libzbar.dylib");
            } else if(osName.startsWith("Mac")) {
                NativeUtils.loadLibraryFromJar("/native/osx/x64/libzbar.dylib");
            } else if(osName.startsWith("Windows")) {
                NativeUtils.loadLibraryFromJar("/native/windows/x64/iconv-2.dll");
                NativeUtils.loadLibraryFromJar("/native/windows/x64/zbar.dll");
            } else if(osArch.equals("aarch64")) {
                NativeUtils.loadLibraryFromJar("/native/linux/aarch64/libzbar.so");
            } else {
                NativeUtils.loadLibraryFromJar("/native/linux/x64/libzbar.so");
            }

            return true;
        } catch(Exception e) {
            log.warn("Could not load ZBar native libraries, disabling. " + e.getMessage());
        }

        return false;
    }

    private static byte[] getRawBytes(String str) {
        char[] chars = str.toCharArray();
        byte[] bytes = new byte[chars.length];
        for(int i = 0; i < chars.length; i++) {
            bytes[i] = (byte)(chars[i]);
        }

        return bytes;
    }

    public record Scan(byte[] rawData, String stringData) {}
}
