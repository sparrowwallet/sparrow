package com.sparrowwallet.sparrow;

import com.beust.jcommander.Parameter;
import com.sparrowwallet.drongo.Network;
import org.slf4j.event.Level;

public class Args {
    @Parameter(names = { "--dir", "-d" }, description = "Path to Sparrow home folder")
    public String dir;

    @Parameter(names = { "--network", "-n" }, description = "Network to use")
    public Network network;

    @Parameter(names = { "--level", "-l" }, description = "Set log level")
    public Level level;

    @Parameter(names = { "--help", "-h" }, description = "Show usage", help = true)
    public boolean help;
}
