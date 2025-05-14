package com.sparrowwallet.filterjar;

import org.gradle.api.tasks.Input;

import java.util.List;

public interface JarFilterConfig {
    @Input
    String getGroup();

    @Input
    String getArtifact();

    @Input
    List<String> getInclusions();

    @Input
    List<String> getExclusions();
}
