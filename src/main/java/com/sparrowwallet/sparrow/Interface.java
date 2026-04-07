package com.sparrowwallet.sparrow;

public enum Interface {
    DESKTOP, TERMINAL, SERVER;

    private static Interface currentInterface;

    public static Interface get() {
        if(currentInterface == null) {
            boolean headless = java.awt.GraphicsEnvironment.isHeadless();
            boolean headlessPlatform = "Headless".equalsIgnoreCase(System.getProperty("glass.platform"));

            if(headless || headlessPlatform) {
                currentInterface = TERMINAL;

                if(headless && !headlessPlatform) {
                    throw new UnsupportedOperationException("Headless environment detected but headless glass platform not found");
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
