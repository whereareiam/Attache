package me.whereareiam.attache.plugin.gradle.extension;

import me.whereareiam.attache.plugin.gradle.AttacheNotation;
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
public class AttacheExtension {
	private final ObjectFactory objects;
	private final Property<Boolean> addMavenCentral;
	private final SetProperty<String> repositories;
	private final Map<String, AttacheLibrarySpec> libraries = new LinkedHashMap<>();

	@Inject
	public AttacheExtension(@NotNull ObjectFactory objects) {
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
	public AttacheLibrarySpec library(@NotNull String notation, @NotNull Action<? super AttacheLibrarySpec> action) {
		AttacheLibrarySpec librarySpec = library(notation);
		action.execute(librarySpec);
		return librarySpec;
	}

	@NotNull
	public AttacheLibrarySpec library(@NotNull Provider<? extends MinimalExternalModuleDependency> dependencyProvider, @NotNull Action<? super AttacheLibrarySpec> action) {
		AttacheLibrarySpec librarySpec = library(dependencyProvider);
		action.execute(librarySpec);
		return librarySpec;
	}

	@NotNull
	public AttacheLibrarySpec library(@NotNull ProviderConvertible<? extends MinimalExternalModuleDependency> dependencyProvider, @NotNull Action<? super AttacheLibrarySpec> action) {
		AttacheLibrarySpec librarySpec = library(dependencyProvider);
		action.execute(librarySpec);
		return librarySpec;
	}

	@NotNull
	public AttacheLibrarySpec library(@NotNull String notation) {
		return libraries.computeIfAbsent(AttacheNotation.keyFromNotation(notation), key -> new AttacheLibrarySpec(objects, key));
	}

	@NotNull
	public AttacheLibrarySpec library(@NotNull Provider<? extends MinimalExternalModuleDependency> dependencyProvider) {
		return libraries.computeIfAbsent(
				AttacheNotation.keyFromDependency(Objects.requireNonNull(dependencyProvider, "dependencyProvider").get()),
				key -> new AttacheLibrarySpec(objects, key)
		);
	}

	@NotNull
	public AttacheLibrarySpec library(@NotNull ProviderConvertible<? extends MinimalExternalModuleDependency> dependencyProvider) {
		return library(Objects.requireNonNull(dependencyProvider, "dependencyProvider").asProvider());
	}

	@NotNull
	public Map<String, AttacheLibrarySpec> getLibrarySpecs() {
		return Collections.unmodifiableMap(libraries);
	}
}
