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
import org.jetbrains.annotations.Nullable;

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
	private final Property<Boolean> transitive;
	private final Property<Boolean> addMavenCentral;
	private final SetProperty<String> repositories;
	private final Map<String, AttacheLibraryMetadata> libraries = new LinkedHashMap<>();

	@Inject
	public AttacheExtension(@NotNull ObjectFactory objects) {
		this(objects, null);
	}

	public AttacheExtension(@NotNull ObjectFactory objects, @Nullable AttacheExtension inheritedDefaults) {
		this.objects = Objects.requireNonNull(objects, "objects");
		this.transitive = objects.property(Boolean.class);
		this.addMavenCentral = objects.property(Boolean.class);
		this.repositories = objects.setProperty(String.class);

		if (inheritedDefaults != null) {
			this.transitive.convention(inheritedDefaults.getTransitive());
			this.addMavenCentral.convention(inheritedDefaults.getAddMavenCentral());
			this.repositories.convention(Collections.emptySet());
			this.repositories.addAll(inheritedDefaults.getRepositories());
		} else {
			this.transitive.convention(false);
			this.addMavenCentral.convention(true);
			this.repositories.convention(Collections.emptySet());
		}
	}

	@NotNull
	public Property<Boolean> getTransitive() {
		return transitive;
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
		return libraries.computeIfAbsent(AttacheNotation.keyFromNotation(notation), key -> new AttacheLibraryMetadata(objects, key, transitive));
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull Provider<? extends MinimalExternalModuleDependency> dependencyProvider) {
		return libraries.computeIfAbsent(
				AttacheNotation.keyFromDependency(Objects.requireNonNull(dependencyProvider, "dependencyProvider").get()),
				key -> new AttacheLibraryMetadata(objects, key, transitive)
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
