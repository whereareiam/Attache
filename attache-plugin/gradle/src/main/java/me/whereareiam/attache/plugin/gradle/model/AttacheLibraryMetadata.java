package me.whereareiam.attache.plugin.gradle.model;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Objects;

/**
 * Per-library Attache metadata.
 */
public class AttacheLibraryMetadata {
	private final String key;
	private final Property<Boolean> transitive;
	private final Property<Boolean> skipIfPresent;
	private final Property<Boolean> isolated;
	private final Property<String> loader;
	private final SetProperty<String> repositories;
	private final SetProperty<String> fallbackRepositories;
	private final ListProperty<DescriptorRelocation> relocations;
	private final ListProperty<DescriptorExcludedDependency> excludedTransitiveDependencies;

	public AttacheLibraryMetadata(@NotNull ObjectFactory objects, @NotNull String key) {
		Objects.requireNonNull(objects, "objects");
		this.key = Objects.requireNonNull(key, "key");
		this.transitive = objects.property(Boolean.class).convention(false);
		this.skipIfPresent = objects.property(Boolean.class).convention(true);
		this.isolated = objects.property(Boolean.class).convention(false);
		this.loader = objects.property(String.class);
		this.repositories = objects.setProperty(String.class).convention(Collections.emptySet());
		this.fallbackRepositories = objects.setProperty(String.class).convention(Collections.emptySet());
		this.relocations = objects.listProperty(DescriptorRelocation.class).convention(Collections.emptyList());
		this.excludedTransitiveDependencies = objects.listProperty(DescriptorExcludedDependency.class).convention(Collections.emptyList());
	}

	@NotNull
	public String getKey() {
		return key;
	}

	@NotNull
	public Property<Boolean> getTransitive() {
		return transitive;
	}

	@NotNull
	public Property<Boolean> getSkipIfPresent() {
		return skipIfPresent;
	}

	@NotNull
	public Property<Boolean> getIsolated() {
		return isolated;
	}

	@NotNull
	public Property<String> getLoader() {
		return loader;
	}

	@NotNull
	public SetProperty<String> getRepositories() {
		return repositories;
	}

	@NotNull
	public SetProperty<String> getFallbackRepositories() {
		return fallbackRepositories;
	}

	@NotNull
	public ListProperty<DescriptorRelocation> getRelocations() {
		return relocations;
	}

	@NotNull
	public ListProperty<DescriptorExcludedDependency> getExcludedTransitiveDependencies() {
		return excludedTransitiveDependencies;
	}

	public void repository(@NotNull String repository) {
		repositories.add(repository);
	}

	public void fallbackRepository(@NotNull String repository) {
		fallbackRepositories.add(repository);
	}

	public void relocate(@NotNull String pattern, @NotNull String relocatedPattern) {
		relocations.add(new DescriptorRelocation(pattern, relocatedPattern));
	}

	public void excludeTransitive(@NotNull String groupId, @NotNull String artifactId) {
		excludedTransitiveDependencies.add(new DescriptorExcludedDependency(groupId, artifactId));
	}
}
