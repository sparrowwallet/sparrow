package com.sparrowwallet.sparrow;

import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class AppearanceStylesTest {
    private static final String GENERAL_CSS = AppearanceStyles.class.getResource("general.css").toExternalForm();
    private static final String DIALOG_CSS = AppearanceStyles.class.getResource("dialog.css").toExternalForm();
    private static final String MODERN_CSS = AppearanceStyles.class.getResource("modern.css").toExternalForm();

    @Test
    public void testModernIsIdempotentAndClassicRestoresExistingStyles() {
        Pane root = new Pane();
        root.getStylesheets().addAll(GENERAL_CSS, DIALOG_CSS);
        root.getStyleClass().addAll("dialog-pane", "chunk-addresses");

        for(int repetition = 0; repetition < 10; repetition++) {
            AppearanceStyles.applyToRoot(root, Appearance.MODERN, Theme.LIGHT, 1070);
            Assertions.assertEquals(List.of(GENERAL_CSS, DIALOG_CSS, MODERN_CSS), root.getStylesheets());
            Assertions.assertEquals(List.of("dialog-pane", "chunk-addresses", "sparrow-modern"), root.getStyleClass());

            AppearanceStyles.applyToRoot(root, Appearance.MODERN, Theme.DARK, 1070);
            AppearanceStyles.applyToRoot(root, Appearance.MODERN, Theme.DARK, 1070);
            Assertions.assertEquals(List.of(GENERAL_CSS, DIALOG_CSS, MODERN_CSS), root.getStylesheets());
            Assertions.assertEquals(List.of("dialog-pane", "chunk-addresses", "sparrow-modern", "sparrow-modern-dark"), root.getStyleClass());
        }

        AppearanceStyles.applyToRoot(root, Appearance.CLASSIC, Theme.DARK, 1070);
        Assertions.assertEquals(List.of(GENERAL_CSS, DIALOG_CSS), root.getStylesheets());
        Assertions.assertEquals(List.of("dialog-pane", "chunk-addresses"), root.getStyleClass());
    }

    @Test
    public void testCompactLayoutFollowsValidSceneWidthAndModernAppearance() {
        Pane root = new Pane();
        AppearanceStyles.applyToRoot(root, Appearance.MODERN, Theme.LIGHT, -1);
        Assertions.assertFalse(root.getStyleClass().contains("sparrow-modern-compact"));

        AppearanceStyles.applyToRoot(root, Appearance.MODERN, Theme.LIGHT, 850);
        Assertions.assertTrue(root.getStyleClass().contains("sparrow-modern-compact"));

        AppearanceStyles.applyToRoot(root, Appearance.MODERN, Theme.LIGHT, 851);
        Assertions.assertFalse(root.getStyleClass().contains("sparrow-modern-compact"));

        AppearanceStyles.applyToRoot(root, Appearance.MODERN, Theme.DARK, 650);
        Assertions.assertTrue(root.getStyleClass().contains("sparrow-modern-compact"));

        AppearanceStyles.applyToRoot(root, Appearance.CLASSIC, Theme.DARK, 650);
        Assertions.assertFalse(root.getStyleClass().contains("sparrow-modern-compact"));
    }
}
