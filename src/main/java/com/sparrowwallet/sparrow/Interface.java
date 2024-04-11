package com.sparrowwallet.sparrow;

public enum Interface {
    DESKTOP, TERMINAL, SERVER;

    private static Interface currentInterface;

    public static Interface get() {
        if(currentInterface == null) {
            boolean headless = java.awt.GraphicsEnvironment.isHeadless();
            boolean glassHeadless = "Headless".equalsIgnoreCase(System.getProperty("glass.platform"));

            if(headless || glassHeadless) {
                currentInterface = TERMINAL;

                if(headless && !glassHeadless) {
                    throw new UnsupportedOperationException("Headless environment detected but Headless platform not found");
                }
            } else {
                currentInterface = DESKTOP;
            }
        }

        return currentInterface;
    }

    public static void set(Interface interf) {
        if(currentInterface != null && interf != currentInterface) {
            throw new IllegalStateException("Interface already set to " + currentInterface);
        }

        currentInterface = interf;
    }
}
