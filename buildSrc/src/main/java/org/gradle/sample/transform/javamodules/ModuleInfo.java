package org.gradle.sample.transform.javamodules;

import java.io.Serializable;
import java.util.*;

/**
 * Data class to hold the information that should be added as module-info.class to an existing Jar file.
 */
public class ModuleInfo implements Serializable {
    private String moduleName;
    private String moduleVersion;
    private List<String> exports = new ArrayList<>();
    private List<String> requires = new ArrayList<>();
    private List<String> requiresTransitive = new ArrayList<>();
    private List<String> uses = new ArrayList<>();
    private Map<String, List<String>> provides = new LinkedHashMap<>();

    ModuleInfo(String moduleName, String moduleVersion) {
        this.moduleName = moduleName;
        this.moduleVersion = moduleVersion;
    }

    public void exports(String exports) {
        this.exports.add(exports);
    }

    public void requires(String requires) {
        this.requires.add(requires);
    }

    public void requiresTransitive(String requiresTransitive) {
        this.requiresTransitive.add(requiresTransitive);
    }

    public void uses(String uses) {
        this.uses.add(uses);
    }

    public void provides(String service, String... providers) {
        this.provides.put(service, Arrays.asList(providers));
    }

    public String getModuleName() {
        return moduleName;
    }

    protected String getModuleVersion() {
        return moduleVersion;
    }

    protected List<String> getExports() {
        return exports;
    }

    protected List<String> getRequires() {
        return requires;
    }

    protected List<String> getRequiresTransitive() {
        return requiresTransitive;
    }

    protected List<String> getUses() {
        return uses;
    }

    public Map<String, List<String>> getProvides() {
        return provides;
    }
}
