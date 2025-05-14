package com.sparrowwallet.filterjar;


import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.nio.file.Files;

public abstract class FilterJarTransform implements TransformAction<FilterJarParameters> {
    @InputArtifact
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        File originalJar = getInputArtifact().get().getAsFile();
        String jarName = originalJar.getName();

        // Get filter configurations from parameters
        Map<String, JarFilterConfig> filterConfigs = getParameters().getFilterConfigs().get();

        //Inclusions are prioritised ahead of exclusions
        Set<String> inclusions = new HashSet<>();
        Set<String> exclusions = new HashSet<>();

        // Check if this JAR matches any configured filters (simplified matching based on artifact name)
        filterConfigs.forEach((key, config) -> {
            if(jarName.contains(config.getArtifact())) {
                inclusions.addAll(config.getInclusions());
                exclusions.addAll(config.getExclusions());
            }
        });

        try {
            if(!exclusions.isEmpty()) {
                filterJar(originalJar, getFilterJar(outputs, originalJar), inclusions, exclusions);
            } else {
                outputs.file(originalJar);
            }
        } catch(Exception e) {
            throw new RuntimeException("Failed to transform jar: " + jarName, e);
        }
    }

    private void filterJar(File inputFile, File outputFile, Set<String> inclusions, Set<String> exclusions) throws Exception {
        try(JarFile jarFile = new JarFile(inputFile); JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(outputFile.toPath()))) {
            jarFile.entries().asIterator().forEachRemaining(entry -> {
                String entryName = entry.getName();
                boolean shouldInclude = inclusions.stream().anyMatch(entryName::startsWith);
                boolean shouldExclude = exclusions.stream().anyMatch(entryName::startsWith);
                if(shouldInclude || !shouldExclude) {
                    try {
                        jarOut.putNextEntry(new JarEntry(entryName));
                        jarFile.getInputStream(entry).transferTo(jarOut);
                        jarOut.closeEntry();
                    } catch(Exception e) {
                        throw new RuntimeException("Error processing entry: " + entryName, e);
                    }
                }
            });
        }
    }

    private File getFilterJar(TransformOutputs outputs, File originalJar) {
        return outputs.file(originalJar.getName().substring(0, originalJar.getName().lastIndexOf('.')) + "-filtered.jar");
    }
}
