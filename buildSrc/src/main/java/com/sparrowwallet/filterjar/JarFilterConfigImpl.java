package com.sparrowwallet.filterjar;

import org.gradle.api.Named;
import org.gradle.api.model.ObjectFactory;
import javax.inject.Inject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class JarFilterConfigImpl implements Named, JarFilterConfig, Serializable {
    private final String name;
    private String group;
    private String artifact;
    private final List<String> inclusions;
    private final List<String> exclusions;

    @Inject
    public JarFilterConfigImpl(String name, ObjectFactory objectFactory) {
        this.name = name;
        this.inclusions = new ArrayList<>();
        this.exclusions = new ArrayList<>();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    @Override
    public String getArtifact() {
        return artifact;
    }

    public void setArtifact(String artifact) {
        this.artifact = artifact;
    }

    @Override
    public List<String> getInclusions() {
        return inclusions;
    }

    public void include(String path) {
        inclusions.add(path);
    }

    @Override
    public List<String> getExclusions() {
        return exclusions;
    }

    public void exclude(String path) {
        exclusions.add(path);
    }
}
