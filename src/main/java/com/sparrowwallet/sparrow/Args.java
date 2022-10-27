package com.sparrowwallet.sparrow;

import com.beust.jcommander.Parameter;
import com.sparrowwallet.drongo.Network;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;

public class Args {
    @Parameter(names = { "--dir", "-d" }, description = "Path to Sparrow home folder")
    public String dir;

    @Parameter(names = { "--network", "-n" }, description = "Network to use")
    public Network network;

    @Parameter(names = { "--level", "-l" }, description = "Set log level")
    public Level level;

    @Parameter(names = { "--terminal", "-t" }, description = "Terminal mode", arity = 0)
    public boolean terminal;

    @Parameter(names = { "--version", "-v" }, description = "Show version", arity = 0)
    public boolean version;

    @Parameter(names = { "--help", "-h" }, description = "Show usage", help = true)
    public boolean help;

    public List<String> toParams() {
        List<String> params = new ArrayList<>();

        if(dir != null) {
            params.add("-d");
            params.add(dir);
        }
        if(network != null) {
            params.add("-n");
            params.add(network.toString());
        }
        if(level != null) {
            params.add("-l");
            params.add(level.toString());
        }

        return params;
    }
}
