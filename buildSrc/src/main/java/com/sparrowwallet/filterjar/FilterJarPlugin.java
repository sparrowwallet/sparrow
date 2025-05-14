package com.sparrowwallet.filterjar;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;

import static com.sparrowwallet.filterjar.FilterJarExtension.FILTERED_ATTRIBUTE;

public class FilterJarPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Register the extension
        FilterJarExtension extension = project.getExtensions().create("filterInfo", FilterJarExtension.class);

        project.getPlugins().withType(JavaPlugin.class).configureEach(_ -> {
            // By default, activate plugin for all source sets
            project.getExtensions().getByType(SourceSetContainer.class).all(extension::activate);

            // All jars have a filtered=false attribute by default
            project.getDependencies().getArtifactTypes().maybeCreate("jar").getAttributes().attribute(FILTERED_ATTRIBUTE, false);

            // Register the transform
            project.getDependencies().registerTransform(FilterJarTransform.class, transform -> {
                transform.getFrom().attribute(FILTERED_ATTRIBUTE, false);
                transform.getTo().attribute(FILTERED_ATTRIBUTE, true);
                transform.parameters(params -> {
                    params.getFilterConfigs().putAll(extension.getFilterConfigs());
                });
            });
        });
    }
}
