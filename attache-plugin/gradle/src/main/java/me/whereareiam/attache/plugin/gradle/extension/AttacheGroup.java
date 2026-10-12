package me.whereareiam.attache.plugin.gradle.extension;

import me.whereareiam.attache.plugin.gradle.model.AttacheLibraryMetadata;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Named;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A named group of Attache libraries that the runtime loads only when asked to.
 * <p>
 * Libraries declared in a group are left out of {@code loadDescriptors()} and loaded with
 * {@code loadGroups("<name>")}:
 * <pre>{@code
 * attache {
 *     group("sqlite") {
 *         library(libs.sqlite.jdbc)
 *     }
 * }
 * }</pre>
 * Declaring a library in a group also adds it as a dependency, so it needs no separate {@code attache(...)} line.
 * Its metadata, such as relocations, is shared with the library of the same coordinates elsewhere in the project.
 */
public class AttacheGroup implements Named {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

	private final String name;
	private final AttacheExtension extension;
	private final ListProperty<Object> dependencies;

	public AttacheGroup(@NotNull ObjectFactory objects, @NotNull String name, @NotNull AttacheExtension extension) {
		Objects.requireNonNull(objects, "objects");
		if (!NAME.matcher(Objects.requireNonNull(name, "name")).matches()) {
			throw new GradleException("Attache group name must start with a letter or digit and contain only letters, digits, '.', '_' or '-': " + name);
		}

		this.name = name;
		this.extension = Objects.requireNonNull(extension, "extension");
		this.dependencies = objects.listProperty(Object.class);
	}

	@NotNull
	@Override
	public String getName() {
		return name;
	}

	/**
	 * Gets the dependency notations declared in this group, as strings or version catalog dependencies.
	 *
	 * @return the declared notations
	 */
	@NotNull
	public ListProperty<Object> getDependencies() {
		return dependencies;
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull String notation) {
		dependencies.add(Objects.requireNonNull(notation, "notation"));
		return extension.library(notation);
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull String notation, @NotNull Action<? super AttacheLibraryMetadata> action) {
		AttacheLibraryMetadata libraryMetadata = library(notation);
		action.execute(libraryMetadata);
		return libraryMetadata;
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull Provider<? extends MinimalExternalModuleDependency> dependencyProvider) {
		dependencies.add(Objects.requireNonNull(dependencyProvider, "dependencyProvider"));
		return extension.library(dependencyProvider);
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull Provider<? extends MinimalExternalModuleDependency> dependencyProvider, @NotNull Action<? super AttacheLibraryMetadata> action) {
		AttacheLibraryMetadata libraryMetadata = library(dependencyProvider);
		action.execute(libraryMetadata);
		return libraryMetadata;
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull ProviderConvertible<? extends MinimalExternalModuleDependency> dependencyProvider) {
		return library(Objects.requireNonNull(dependencyProvider, "dependencyProvider").asProvider());
	}

	@NotNull
	public AttacheLibraryMetadata library(@NotNull ProviderConvertible<? extends MinimalExternalModuleDependency> dependencyProvider, @NotNull Action<? super AttacheLibraryMetadata> action) {
		AttacheLibraryMetadata libraryMetadata = library(dependencyProvider);
		action.execute(libraryMetadata);
		return libraryMetadata;
	}
}
