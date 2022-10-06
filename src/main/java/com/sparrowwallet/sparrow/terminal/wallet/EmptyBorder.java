package com.sparrowwallet.sparrow.terminal.wallet;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.gui2.*;

public class EmptyBorder extends AbstractBorder {
    private final String title;

    protected EmptyBorder(String title) {
        if (title == null) {
            throw new IllegalArgumentException("Cannot create a border with null title");
        }
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + title + "}";
    }

    @Override
    protected BorderRenderer createDefaultRenderer() {
        return new EmptyBorderRenderer();
    }

    private static class EmptyBorderRenderer implements Border.BorderRenderer {
        @Override
        public TerminalSize getPreferredSize(Border component) {
            EmptyBorder border = (EmptyBorder)component;
            Component wrappedComponent = border.getComponent();
            TerminalSize preferredSize;
            if (wrappedComponent == null) {
                preferredSize = TerminalSize.ZERO;
            } else {
                preferredSize = wrappedComponent.getPreferredSize();
            }
            preferredSize = preferredSize.withRelativeColumns(2).withRelativeRows(2);
            String borderTitle = border.getTitle();
            return preferredSize.max(new TerminalSize((borderTitle.isEmpty() ? 2 : TerminalTextUtils.getColumnWidth(borderTitle) + 4), 2));
        }

        @Override
        public TerminalPosition getWrappedComponentTopLeftOffset() {
            return TerminalPosition.OFFSET_1x1;
        }

        @Override
        public TerminalSize getWrappedComponentSize(TerminalSize borderSize) {
            return borderSize
                    .withRelativeColumns(-Math.min(2, borderSize.getColumns()))
                    .withRelativeRows(-Math.min(2, borderSize.getRows()));
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, Border component) {
            EmptyBorder border = (EmptyBorder)component;
            Component wrappedComponent = border.getComponent();
            if(wrappedComponent == null) {
                return;
            }
            TerminalSize drawableArea = graphics.getSize();

            char horizontalLine = ' ';
            char verticalLine = ' ';
            char bottomLeftCorner = ' ';
            char topLeftCorner = ' ';
            char bottomRightCorner = ' ';
            char topRightCorner = ' ';
            char titleLeft = ' ';
            char titleRight = ' ';

            ThemeDefinition themeDefinition = component.getTheme().getDefinition(AbstractBorder.class);
            graphics.applyThemeStyle(themeDefinition.getNormal());
            graphics.setCharacter(0, drawableArea.getRows() - 1, bottomLeftCorner);
            if(drawableArea.getRows() > 2) {
                graphics.drawLine(new TerminalPosition(0, drawableArea.getRows() - 2), new TerminalPosition(0, 1), verticalLine);
            }
            graphics.setCharacter(0, 0, topLeftCorner);
            if(drawableArea.getColumns() > 2) {
                graphics.drawLine(new TerminalPosition(1, 0), new TerminalPosition(drawableArea.getColumns() - 2, 0), horizontalLine);
            }

            graphics.applyThemeStyle(themeDefinition.getNormal());
            graphics.setCharacter(drawableArea.getColumns() - 1, 0, topRightCorner);
            if(drawableArea.getRows() > 2) {
                graphics.drawLine(new TerminalPosition(drawableArea.getColumns() - 1, 1),
                        new TerminalPosition(drawableArea.getColumns() - 1, drawableArea.getRows() - 2),
                        verticalLine);
            }
            graphics.setCharacter(drawableArea.getColumns() - 1, drawableArea.getRows() - 1, bottomRightCorner);
            if(drawableArea.getColumns() > 2) {
                graphics.drawLine(new TerminalPosition(1, drawableArea.getRows() - 1),
                        new TerminalPosition(drawableArea.getColumns() - 2, drawableArea.getRows() - 1),
                        horizontalLine);
            }


            if(border.getTitle() != null && !border.getTitle().isEmpty() &&
                    drawableArea.getColumns() >= TerminalTextUtils.getColumnWidth(border.getTitle()) + 4) {
                graphics.applyThemeStyle(themeDefinition.getActive());
                graphics.putString(2, 0, border.getTitle());

                graphics.applyThemeStyle(themeDefinition.getNormal());
                graphics.setCharacter(1, 0, titleLeft);
                graphics.setCharacter(2 + TerminalTextUtils.getColumnWidth(border.getTitle()), 0, titleRight);
            }

            wrappedComponent.draw(graphics.newTextGraphics(getWrappedComponentTopLeftOffset(), getWrappedComponentSize(drawableArea)));
        }
    }
}
