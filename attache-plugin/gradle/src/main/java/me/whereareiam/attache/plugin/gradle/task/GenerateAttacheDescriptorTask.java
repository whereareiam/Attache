package me.whereareiam.attache.plugin.gradle.task;

import me.whereareiam.attache.descriptor.AttacheDescriptorCodec;
import me.whereareiam.attache.descriptor.AttacheDescriptorFragment;
import me.whereareiam.attache.descriptor.AttacheDescriptorLibrary;
import me.whereareiam.attache.model.ExcludedDependency;
import me.whereareiam.attache.model.RelocationRule;
import me.whereareiam.attache.plugin.gradle.AttachePlugin;
import me.whereareiam.attache.plugin.gradle.AttacheNotation;
import me.whereareiam.attache.plugin.gradle.extension.AttacheExtension;
import me.whereareiam.attache.plugin.gradle.model.AttacheLibraryMetadata;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
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
import org.gradle.work.DisableCachingByDefault;
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
@DisableCachingByDefault(because = "Descriptor generation is inexpensive; local up-to-date checks suffice")
public abstract class GenerateAttacheDescriptorTask extends DefaultTask {
	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@Input
	public abstract Property<String> getDescriptorPath();

	@Input
	public abstract Property<String> getDescriptorContent();

	@TaskAction
	public void generate() throws IOException {
		Path outputDirectory = getOutputDirectory().get().getAsFile().toPath();
		Path descriptorFile = outputDirectory.resolve(getDescriptorPath().get());
		Files.createDirectories(descriptorFile.getParent());
		Files.writeString(descriptorFile, getDescriptorContent().get(), StandardCharsets.UTF_8);
	}

	@NotNull
	public static String descriptorPathFor(@NotNull Project project) {
		Objects.requireNonNull(project, "project");

		Path rootDirectory = project.getRootProject().getProjectDir().toPath().toAbsolutePath().normalize();
		Path projectDirectory = project.getProjectDir().toPath().toAbsolutePath().normalize();
		String normalized;

		if (projectDirectory.equals(rootDirectory)) {
			normalized = project.getName();
		} else if (projectDirectory.startsWith(rootDirectory)) {
			normalized = rootDirectory.relativize(projectDirectory)
					.toString()
					.replace(projectDirectory.getFileSystem().getSeparator(), "/");
		} else {
			normalized = descriptorKeyFromProjectPath(project.getPath(), project.getName());
		}

		return "META-INF/attache/" + normalized + "/attache.xml";
	}

	@NotNull
	public static String descriptorPathFor(@NotNull String projectPath, @NotNull String projectName) {
		return "META-INF/attache/" + descriptorKeyFromProjectPath(projectPath, projectName) + "/attache.xml";
	}

	@NotNull
	private static String descriptorKeyFromProjectPath(@NotNull String projectPath, @NotNull String projectName) {
		String normalized = Objects.requireNonNull(projectPath, "projectPath");
		if (":".equals(normalized)) {
			normalized = Objects.requireNonNull(projectName, "projectName");
		} else {
			normalized = normalized.replaceFirst("^:", "").replace(':', '/');
		}
		return normalized;
	}

	@NotNull
	public static String renderDescriptor(@NotNull org.gradle.api.Project project) {
		return AttacheDescriptorCodec.encode(buildDescriptorFragment(project));
	}

	@NotNull
	public static AttacheDescriptorFragment buildDescriptorFragment(@NotNull org.gradle.api.Project project) {
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

		AttacheDescriptorFragment fragment = new AttacheDescriptorFragment();
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

			AttacheDescriptorLibrary library = new AttacheDescriptorLibrary();
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
				spec.getRelocations().getOrElse(java.util.List.of()).stream()
						.map(relocation -> RelocationRule.builder()
								.pattern(relocation.getPattern())
								.relocatedPattern(relocation.getRelocatedPattern())
								.build())
						.forEach(library.getRelocations()::add);
				spec.getExcludedTransitiveDependencies().getOrElse(java.util.List.of()).stream()
						.map(excluded -> new ExcludedDependency(excluded.getGroupId(), excluded.getArtifactId()))
						.forEach(library.getExcludedTransitiveDependencies()::add);
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
