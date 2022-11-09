package com.sparrowwallet.sparrow.terminal.wallet;

import com.google.zxing.common.BitMatrix;
import com.googlecode.lanterna.*;
import com.googlecode.lanterna.graphics.AbstractTextGraphics;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.graphics.TextImage;

import java.util.EnumSet;

public class QRTextImage implements TextImage {
    private final BitMatrix bitMatrix;
    private final TerminalSize size;

    private static final TextCharacter REVERSE_CHARACTER = TextCharacter.fromString(" ", TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT, EnumSet.of(SGR.REVERSE))[0];

    public QRTextImage(BitMatrix bitMatrix) {
        this.bitMatrix = bitMatrix;
        this.size = new TerminalSize(bitMatrix.getWidth() * 2, bitMatrix.getHeight());
    }

    @Override
    public TerminalSize getSize() {
        return size;
    }

    @Override
    public TextCharacter getCharacterAt(TerminalPosition position) {
        return getCharacterAt(position.getColumn(), position.getRow());
    }

    @Override
    public TextCharacter getCharacterAt(int column, int row) {
        boolean filled = bitMatrix.get(column / 2, row);
        return filled ? TextCharacter.DEFAULT_CHARACTER : REVERSE_CHARACTER;
    }

    @Override
    public void setCharacterAt(TerminalPosition position, TextCharacter character) {
        throw new UnsupportedOperationException("Cannot set character in QR Code");
    }

    @Override
    public void setCharacterAt(int column, int row, TextCharacter character) {
        throw new UnsupportedOperationException("Cannot set character in QR Code");
    }

    @Override
    public void setAll(TextCharacter character) {
        throw new UnsupportedOperationException("Cannot set character in QR Code");
    }

    @Override
    public TextGraphics newTextGraphics() {
        return new AbstractTextGraphics() {
            @Override
            public TextGraphics setCharacter(int columnIndex, int rowIndex, TextCharacter textCharacter) {
                QRTextImage.this.setCharacterAt(columnIndex, rowIndex, textCharacter);
                return this;
            }

            @Override
            public TextCharacter getCharacter(int column, int row) {
                return QRTextImage.this.getCharacterAt(column, row);
            }

            @Override
            public TerminalSize getSize() {
                return size;
            }
        };
    }

    @Override
    public TextImage resize(TerminalSize newSize, TextCharacter filler) {
        throw new UnsupportedOperationException("Cannot resize QR Code");
    }

    @Override
    public void copyTo(TextImage destination) {
        throw new UnsupportedOperationException("Cannot copy QR Code");
    }

    @Override
    public void copyTo(TextImage destination, int startRowIndex, int rows, int startColumnIndex, int columns, int destinationRowOffset, int destinationColumnOffset) {
        throw new UnsupportedOperationException("Cannot copy QR Code");
    }

    @Override
    public void scrollLines(int firstLine, int lastLine, int distance) {
        throw new UnsupportedOperationException("Cannot scroll QR Code");
    }
}
