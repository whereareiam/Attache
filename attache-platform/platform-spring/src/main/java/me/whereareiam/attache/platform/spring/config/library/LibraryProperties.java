package me.whereareiam.attache.platform.spring.config.library;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.attache.platform.spring.config.RelocationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration holder for a single library entry declared in Spring properties.
 */
@Setter
@Getter
public class LibraryProperties {
    private final List<String> urls = new ArrayList<>();
    private final List<String> repositories = new ArrayList<>();
    private final List<String> fallbackRepositories = new ArrayList<>();
    private final List<RelocationProperties> relocations = new ArrayList<>();
    private final List<ExcludedLibraryProperties> excludedTransitiveDependencies = new ArrayList<>();

    private String groupId;
    private String artifactId;
    private String version;
    private String classifier;
    private String checksum;
    private boolean isolated;
    private String loader;
    private boolean resolveTransitiveDependencies;

}
