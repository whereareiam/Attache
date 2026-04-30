package me.whereareiam.attache.plugin.gradle.task;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.whereareiam.attache.plugin.gradle.AttachePlugin;
import me.whereareiam.attache.plugin.gradle.AttacheNotation;
import me.whereareiam.attache.plugin.gradle.extension.AttacheExtension;
import me.whereareiam.attache.plugin.gradle.model.AttacheLibraryMetadata;
import me.whereareiam.attache.plugin.gradle.model.DescriptorFragment;
import me.whereareiam.attache.plugin.gradle.model.DescriptorLibrary;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generates a module-scoped Attache descriptor fragment.
 */
public abstract class GenerateAttacheDescriptorTask extends DefaultTask {
	private static final Gson GSON = new GsonBuilder()
			.disableHtmlEscaping()
			.setPrettyPrinting()
			.create();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@Input
	public abstract Property<String> getDescriptorPath();

	@Input
	public abstract Property<String> getDescriptorJson();

	@TaskAction
	public void generate() throws IOException {
		Path outputDirectory = getOutputDirectory().get().getAsFile().toPath();
		Path descriptorFile = outputDirectory.resolve(getDescriptorPath().get());
		Files.createDirectories(descriptorFile.getParent());
		Files.writeString(descriptorFile, getDescriptorJson().get(), StandardCharsets.UTF_8);
	}

	@NotNull
	public static String descriptorPathFor(@NotNull String projectPath, @NotNull String projectName) {
		String normalized = Objects.requireNonNull(projectPath, "projectPath");
		if (":".equals(normalized)) {
			normalized = Objects.requireNonNull(projectName, "projectName");
		} else {
			normalized = normalized.replaceFirst("^:", "").replace(':', '/');
		}

		return "META-INF/attache/" + normalized + "/attache.json";
	}

	@NotNull
	public static String renderDescriptor(@NotNull org.gradle.api.Project project) {
		return GSON.toJson(buildDescriptorFragment(project));
	}

	@NotNull
	public static DescriptorFragment buildDescriptorFragment(@NotNull org.gradle.api.Project project) {
		AttacheExtension extension = project.getExtensions().getByType(AttacheExtension.class);
		Configuration attache = project.getConfigurations().getByName(AttachePlugin.ATTACHE_CONFIGURATION);
		Configuration attacheOnly = project.getConfigurations().getByName(AttachePlugin.ATTACHE_ONLY_CONFIGURATION);
		Configuration manifest = project.getConfigurations().getByName(AttachePlugin.ATTACHE_MANIFEST_CONFIGURATION);

		validateDependencies(attache);
		validateDependencies(attacheOnly);

		LinkedHashSet<String> dependencyKeys = new LinkedHashSet<>();
		collectDependencyKeys(attache, dependencyKeys);
		collectDependencyKeys(attacheOnly, dependencyKeys);

		Map<String, ResolvedArtifact> resolvedArtifacts = resolveArtifacts(manifest);

		DescriptorFragment fragment = new DescriptorFragment();
		fragment.setProjectPath(project.getPath());
		fragment.setProjectName(project.getName());
		fragment.setAddMavenCentral(extension.getAddMavenCentral().getOrElse(true));
		fragment.getRepositories().addAll(extension.getRepositories().getOrElse(Set.of()));
		boolean defaultTransitive = extension.getTransitive().getOrElse(false);

		for (String key : dependencyKeys) {
			ResolvedArtifact artifact = resolvedArtifacts.get(key);
			if (artifact == null) {
				throw new GradleException("No resolved artifact found for Attache dependency " + key + " in project " + project.getPath());
			}

			rejectNonJarArtifact(artifact);

			DescriptorLibrary library = new DescriptorLibrary();
			library.setGroupId(artifact.getModuleVersion().getId().getGroup());
			library.setArtifactId(artifact.getName());
			library.setVersion(artifact.getModuleVersion().getId().getVersion());

			String classifier = artifact.getClassifier();
			if (classifier != null && !classifier.isBlank()) {
				library.setClassifier(classifier);
			}

			AttacheLibraryMetadata spec = extension.getLibraryMetadata().get(key);
			if (spec != null) {
				library.setResolveTransitiveDependencies(spec.getTransitive().getOrElse(false));
				library.setSkipIfPresent(spec.getSkipIfPresent().getOrElse(true));
				library.setIsolated(spec.getIsolated().getOrElse(false));
				library.setLoader(spec.getLoader().getOrNull());
				library.getRepositories().addAll(spec.getRepositories().getOrElse(Set.of()));
				library.getFallbackRepositories().addAll(spec.getFallbackRepositories().getOrElse(Set.of()));
				library.getRelocations().addAll(spec.getRelocations().getOrElse(java.util.List.of()));
				library.getExcludedTransitiveDependencies().addAll(spec.getExcludedTransitiveDependencies().getOrElse(java.util.List.of()));
			} else {
				library.setResolveTransitiveDependencies(defaultTransitive);
			}

			fragment.getLibraries().add(library);
		}

		return fragment;
	}

	private static void validateDependencies(@NotNull Configuration configuration) {
		for (Dependency dependency : configuration.getAllDependencies()) {
			if (dependency instanceof ProjectDependency) {
				throw new GradleException("Attache configuration does not support project dependencies: " + dependency);
			}
			if (!(dependency instanceof ExternalModuleDependency)) {
				throw new GradleException("Attache configuration only supports external module dependencies: " + dependency);
			}
		}
	}

	private static void collectDependencyKeys(@NotNull Configuration configuration, @NotNull Set<String> dependencyKeys) {
		for (Dependency dependency : configuration.getAllDependencies()) {
			dependencyKeys.add(AttacheNotation.keyFromNotation(dependency.getGroup() + ':' + dependency.getName()));
		}
	}

	@NotNull
	private static Map<String, ResolvedArtifact> resolveArtifacts(@NotNull Configuration configuration) {
		LinkedHashMap<String, ResolvedArtifact> artifacts = new LinkedHashMap<>();
		for (ResolvedArtifact artifact : configuration.getResolvedConfiguration().getResolvedArtifacts()) {
			String key = artifact.getModuleVersion().getId().getGroup() + ':' + artifact.getName();
			artifacts.putIfAbsent(key, artifact);
		}
		return artifacts;
	}

	private static void rejectNonJarArtifact(@NotNull ResolvedArtifact artifact) {
		String extension = artifact.getExtension();
		String type = artifact.getType();
		if ("jar".equalsIgnoreCase(extension) || "jar".equalsIgnoreCase(type)) {
			return;
		}

		throw new GradleException("Attache dependency must resolve to a jar artifact, but "
				+ artifact.getModuleVersion().getId() + " resolved to type '" + type + "' and extension '" + extension + '\'');
	}
}
