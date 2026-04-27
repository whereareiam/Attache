package me.whereareiam.attache.plugin.gradle.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JSON library entry emitted by the Gradle plugin.
 */
@Getter
@Setter
public class DescriptorLibrary {
	private final Set<String> urls = new LinkedHashSet<>();
	private final Set<String> repositories = new LinkedHashSet<>();
	private final Set<String> fallbackRepositories = new LinkedHashSet<>();
	private final List<DescriptorRelocation> relocations = new ArrayList<>();
	private final List<DescriptorExcludedDependency> excludedTransitiveDependencies = new ArrayList<>();

	private String groupId;
	private String artifactId;
	private String version;
	private String classifier;
	private boolean skipIfPresent = true;
	private boolean isolated;
	private String loader;
	private boolean resolveTransitiveDependencies;
}
