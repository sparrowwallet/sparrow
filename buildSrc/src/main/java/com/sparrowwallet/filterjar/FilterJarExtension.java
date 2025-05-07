package com.sparrowwallet.filterjar;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;

public abstract class FilterJarExtension {
    static Attribute<Boolean> FILTERED_ATTRIBUTE = Attribute.of("filtered", Boolean.class);

    public abstract MapProperty<String, JarFilterConfigImpl> getFilterConfigs();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract ConfigurationContainer getConfigurations();

    public void filter(String group, String artifact, Action<? super JarFilterConfigImpl> action) {
        String name = group + ":" + artifact;
        JarFilterConfigImpl config = new JarFilterConfigImpl(name, getObjects());
        config.setGroup(group);
        config.setArtifact(artifact);
        action.execute(config);
        getFilterConfigs().put(name, config);
    }

    /**
     * Activate the plugin's functionality for dependencies of all scopes of the given source set
     * (runtimeClasspath, compileClasspath, annotationProcessor).
     * Note that the plugin activates the functionality for all source sets by default.
     * Therefore, this method only has an effect for source sets for which a {@link #deactivate(Configuration)}
     * has been performed.
     *
     * @param sourceSet the Source Set to activate (e.g. sourceSets.test)
     */
    public void activate(SourceSet sourceSet) {
        Configuration runtimeClasspath = getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());
        Configuration compileClasspath = getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());
        Configuration annotationProcessor = getConfigurations().getByName(sourceSet.getAnnotationProcessorConfigurationName());

        activate(runtimeClasspath);
        activate(compileClasspath);
        activate(annotationProcessor);
    }

    /**
     * Activate the plugin's functionality for a single resolvable Configuration.
     *
     * @param resolvable a resolvable Configuration (e.g. configurations["customClasspath"])
     */
    public void activate(Configuration resolvable) {
        resolvable.getAttributes().attribute(FILTERED_ATTRIBUTE, true);
    }

    /**
     * Deactivate the plugin's functionality for a single resolvable Configuration.
     *
     * @param resolvable a resolvable Configuration (e.g. configurations.annotationProcessor)
     */
    public void deactivate(Configuration resolvable) {
        resolvable.getAttributes().attribute(FILTERED_ATTRIBUTE, false);
    }
}
