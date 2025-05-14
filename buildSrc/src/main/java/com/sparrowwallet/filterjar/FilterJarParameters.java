package com.sparrowwallet.filterjar;

import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;

public interface FilterJarParameters extends TransformParameters {
    @Input
    MapProperty<String, JarFilterConfig> getFilterConfigs();
}
