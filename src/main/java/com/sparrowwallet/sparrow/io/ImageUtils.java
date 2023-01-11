package com.sparrowwallet.sparrow.io;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import net.coobird.thumbnailator.Thumbnails;

import java.awt.image.BufferedImage;
import java.io.*;

public class ImageUtils {
    public static byte[] resize(Image image, int width, int height) {
        return resize(SwingFXUtils.fromFXImage(image, null), width, height);
    }

    public static byte[] resize(BufferedImage image, int width, int height) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resize(image, baos, width, height);
            return baos.toByteArray();
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void resize(BufferedImage image, OutputStream outputStream, int width, int height) throws IOException {
        resize(Thumbnails.of(image), outputStream, width, height);
    }

    public static BufferedImage resizeToImage(BufferedImage image, int width, int height) {
        try {
            return Thumbnails.of(image).size(width, height).outputQuality(1).asBufferedImage();
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] resize(File file, int width, int height) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resize(file, baos, width, height);
        return baos.toByteArray();
    }

    public static void resize(File file, OutputStream outputStream, int width, int height) throws IOException {
        resize(Thumbnails.of(file), outputStream, width, height);
    }

    public static InputStream resize(InputStream inputStream, int width, int height) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            resize(inputStream, baos, width, height);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void resize(InputStream inputStream, OutputStream outputStream, int width, int height) throws IOException {
        resize(Thumbnails.of(inputStream), outputStream, width, height);
    }

    private static void resize(Thumbnails.Builder<?> builder, OutputStream outputStream, int width, int height) throws IOException {
        builder.size(width, height).outputFormat("png").outputQuality(1).toOutputStream(outputStream);
    }
}
