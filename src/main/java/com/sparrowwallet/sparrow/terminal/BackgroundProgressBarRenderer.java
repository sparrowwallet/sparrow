package com.sparrowwallet.sparrow.terminal;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.ProgressBar;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

public class BackgroundProgressBarRenderer implements ComponentRenderer<ProgressBar> {
    @Override
    public TerminalSize getPreferredSize(ProgressBar component) {
        int preferredWidth = component.getPreferredWidth();
        if(preferredWidth > 0) {
            return new TerminalSize(preferredWidth, 1);
        } else {
            return new TerminalSize(10, 1);
        }
    }

    @Override
    public void drawComponent(TextGUIGraphics graphics, ProgressBar component) {
        TerminalSize size = graphics.getSize();
        if(size.getRows() == 0 || size.getColumns() == 0) {
            return;
        }

        ThemeDefinition themeDefinition = component.getThemeDefinition();
        int columnOfProgress = (int)(component.getProgress() * size.getColumns());
        for(int row = 0; row < size.getRows(); row++) {
            graphics.applyThemeStyle(themeDefinition.getActive());
            for(int column = 0; column < size.getColumns(); column++) {
                if(column < columnOfProgress) {
                    graphics.setCharacter(column, row, themeDefinition.getCharacter("FILLER", ' '));
                }
            }
        }
    }
}
