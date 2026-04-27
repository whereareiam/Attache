package me.whereareiam.attache.plugin.gradle.extension;

import me.whereareiam.attache.plugin.gradle.AttacheNotation;
import me.whereareiam.attache.plugin.gradle.model.AttacheLibraryMetadata;
import org.gradle.api.Action;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.api.provider.SetProperty;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Attache Gradle extension.
 */
public class AttacheMetadataExtension {
	private final ObjectFactory objects;
	private final Property<Boolean> addMavenCentral;
	private final SetProperty<String> repositories;
	private final Map<String, AttacheLibraryMetadata> libraries = new LinkedHashMap<>();

	@Inject
	public AttacheMetadataExtension(@NotNull ObjectFactory objects) {
		this.objects = Objects.requireNonNull(objects, "objects");
		this.addMavenCentral = objects.property(Boolean.class).convention(true);
		this.repositories = objects.setProperty(String.class).convention(Collections.emptySet());
	}

	@NotNull
	public Property<Boolean> getAddMavenCentral() {
		return addMavenCentral;
	}

	@NotNull
	public SetProperty<String> getRepositories() {
		return repositories;
	}

	public void repository(@NotNull String repository) {
		repositories.add(repository);
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull String notation, @NotNull Action<? super AttacheLibraryMetadata> action) {
		AttacheLibraryMetadata libraryMetadata = library(notation);
		action.execute(libraryMetadata);
		return libraryMetadata;
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull Provider<? extends MinimalExternalModuleDependency> dependencyProvider, @NotNull Action<? super AttacheLibraryMetadata> action) {
		AttacheLibraryMetadata libraryMetadata = library(dependencyProvider);
		action.execute(libraryMetadata);
		return libraryMetadata;
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull ProviderConvertible<? extends MinimalExternalModuleDependency> dependencyProvider, @NotNull Action<? super AttacheLibraryMetadata> action) {
		AttacheLibraryMetadata libraryMetadata = library(dependencyProvider);
		action.execute(libraryMetadata);
		return libraryMetadata;
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull String notation) {
		return libraries.computeIfAbsent(AttacheNotation.keyFromNotation(notation), key -> new AttacheLibraryMetadata(objects, key));
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull Provider<? extends MinimalExternalModuleDependency> dependencyProvider) {
		return libraries.computeIfAbsent(
				AttacheNotation.keyFromDependency(Objects.requireNonNull(dependencyProvider, "dependencyProvider").get()),
				key -> new AttacheLibraryMetadata(objects, key)
		);
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull ProviderConvertible<? extends MinimalExternalModuleDependency> dependencyProvider) {
		return library(Objects.requireNonNull(dependencyProvider, "dependencyProvider").asProvider());
	}

	@NotNull
	public Map<String, AttacheLibraryMetadata> getLibraryMetadata() {
		return Collections.unmodifiableMap(libraries);
	}
}
