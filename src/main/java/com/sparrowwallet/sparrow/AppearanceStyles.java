package com.sparrowwallet.sparrow;

import com.sparrowwallet.sparrow.control.CoinTreeTable;
import com.sparrowwallet.sparrow.io.Config;
import javafx.beans.property.ReadOnlyProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;

import java.util.List;

final class AppearanceStyles {
    private static final String MODERN_CSS = AppearanceStyles.class.getResource("modern.css").toExternalForm();
    private static final String MODERN_ROOT = "sparrow-modern";
    private static final String MODERN_DARK_ROOT = "sparrow-modern-dark";
    private static final String MODERN_COMPACT_ROOT = "sparrow-modern-compact";
    private static final double COMPACT_MAX_WIDTH = 850;

    private static final ChangeListener<Parent> ROOT_CHANGED = (_, _, root) -> applyTo(root.getScene());
    private static final ChangeListener<Number> WIDTH_CHANGED = (observable, _, width) -> {
        Scene scene = (Scene)((ReadOnlyProperty<?>)observable).getBean();
        Parent root = scene.getRoot();
        boolean modern = root.getStyleClass().contains(MODERN_ROOT);
        updateEntry(root.getStyleClass(), MODERN_COMPACT_ROOT, modern && isCompact(width.doubleValue()));
    };
    private static final ChangeListener<Scene> SCENE_CHANGED = (_, previous, current) -> {
        stopObservingScene(previous);
        observeScene(current);
    };

    private static boolean observingWindows;

    private AppearanceStyles() {
    }

    static void observeWindows() {
        if(observingWindows) {
            return;
        }
        observingWindows = true;

        ObservableList<Window> windows = Window.getWindows();
        windows.addListener((ListChangeListener<Window>)change -> {
            while(change.next()) {
                for(Window window : change.getRemoved()) {
                    window.sceneProperty().removeListener(SCENE_CHANGED);
                    stopObservingScene(window.getScene());
                }
                change.getAddedSubList().forEach(AppearanceStyles::observeWindow);
            }
        });
        List.copyOf(windows).forEach(AppearanceStyles::observeWindow);
    }

    private static void observeWindow(Window window) {
        window.sceneProperty().addListener(SCENE_CHANGED);
        observeScene(window.getScene());
    }

    private static void observeScene(Scene scene) {
        if(scene != null) {
            scene.rootProperty().addListener(ROOT_CHANGED);
            scene.widthProperty().addListener(WIDTH_CHANGED);
            applyTo(scene);
        }
    }

    private static void stopObservingScene(Scene scene) {
        if(scene != null) {
            scene.rootProperty().removeListener(ROOT_CHANGED);
            scene.widthProperty().removeListener(WIDTH_CHANGED);
        }
    }

    static void refreshWindows() {
        if(observingWindows) {
            List.copyOf(Window.getWindows()).forEach(window -> applyTo(window.getScene()));
        }
    }

    static void applyTo(Scene scene) {
        if(scene != null) {
            applyToRoot(scene.getRoot(), Config.get().getAppearance(), AppServices.getActiveTheme(), scene.getWidth());
        }
    }

    static void applyToRoot(Parent root, Appearance appearance, Theme activeTheme, double sceneWidth) {
        boolean modern = appearance == Appearance.MODERN;

        // The root's author stylesheets preserve JavaFX's owner bindings for dialog
        // Scene stylesheets and leave the existing author stylesheet order intact.
        if(!modern) {
            root.getStylesheets().remove(MODERN_CSS);
        } else if(!root.getStyleClass().contains(MODERN_ROOT)) {
            // Read the Classic widths before the author CSS can change column bounds.
            root.lookupAll(".tree-table-view").forEach(node -> {
                if(node instanceof CoinTreeTable table) {
                    table.captureClassicColumnLayout();
                }
            });
        }
        updateEntry(root.getStyleClass(), MODERN_ROOT, modern);
        updateEntry(root.getStyleClass(), MODERN_DARK_ROOT, modern && activeTheme == Theme.DARK);
        updateEntry(root.getStyleClass(), MODERN_COMPACT_ROOT, modern && isCompact(sceneWidth));
        if(modern) {
            updateEntry(root.getStylesheets(), MODERN_CSS, true);
        }
    }

    private static boolean isCompact(double sceneWidth) {
        return sceneWidth > 0 && sceneWidth <= COMPACT_MAX_WIDTH;
    }

    private static void updateEntry(ObservableList<String> entries, String entry, boolean enabled) {
        if(enabled) {
            if(!entries.contains(entry)) {
                entries.add(entry);
            }
        } else {
            entries.remove(entry);
        }
    }
}
