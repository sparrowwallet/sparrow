package com.sparrowwallet.sparrow;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AppControllerTest {
    @Test
    public void subTabSizeFallsBackToMinWhenHeightUnknown() {
        Assertions.assertEquals(90.0, AppController.computeSubTabSize(0, 1), 0.001);
        Assertions.assertEquals(90.0, AppController.computeSubTabSize(-1, 5), 0.001);
    }

    @Test
    public void subTabSizeFallsBackToMinWhenManyTabs() {
        // 8 tabs in a 600px pane: (600-20)/8 = 72.5, below min of 90 → clamped to 90
        Assertions.assertEquals(90.0, AppController.computeSubTabSize(600, 8), 0.001);
    }

    @Test
    public void subTabSizeExpandsWhenFewTabs() {
        // 2 tabs in a 600px pane: (600-20)/2 = 290, above max of 180 → clamped to 180
        Assertions.assertEquals(180.0, AppController.computeSubTabSize(600, 2), 0.001);
    }

    @Test
    public void subTabSizeScalesProportionallyInRange() {
        // 4 tabs in a 600px pane: (600-20)/4 = 145, within [90,180] → 145
        Assertions.assertEquals(145.0, AppController.computeSubTabSize(600, 4), 0.001);
    }

    @Test
    public void subTabSizeHandlesZeroTabCount() {
        // Defensive: 0 tabs treated as 1
        Assertions.assertEquals(180.0, AppController.computeSubTabSize(600, 0), 0.001);
    }
}
