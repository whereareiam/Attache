package me.whereareiam.attache.plugin.gradle;

import me.whereareiam.attache.plugin.gradle.extension.AttacheExtension;
import me.whereareiam.attache.plugin.gradle.task.GenerateAttacheDescriptorTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Gradle plugin entry point for Attache descriptor generation.
 */
public class AttachePlugin implements Plugin<Project> {
	public static final String ATTACHE_EXTENSION = "attache";
	public static final String ATTACHE_CONFIGURATION = "attache";
	public static final String ATTACHE_API_CONFIGURATION = "attacheApi";
	public static final String ATTACHE_ONLY_CONFIGURATION = "attacheOnly";
	public static final String ATTACHE_MANIFEST_CONFIGURATION = "attacheManifest";
	public static final String GENERATE_TASK = "generateAttacheDescriptor";

	@Override
	public void apply(Project project) {
		AttacheExtension rootDefaults = rootDefaults(project);
		attacheExtension(project, rootDefaults);

		Configuration attacheApi = project.getConfigurations().create(ATTACHE_API_CONFIGURATION, configuration -> {
			configuration.setCanBeConsumed(false);
			configuration.setCanBeResolved(false);
			configuration.setTransitive(false);
		});
		Configuration attache = project.getConfigurations().create(ATTACHE_CONFIGURATION, configuration -> {
			configuration.setCanBeConsumed(false);
			configuration.setCanBeResolved(false);
			configuration.setTransitive(false);
			configuration.extendsFrom(attacheApi);
		});
		Configuration attacheOnly = project.getConfigurations().create(ATTACHE_ONLY_CONFIGURATION, configuration -> {
			configuration.setCanBeConsumed(false);
			configuration.setCanBeResolved(false);
			configuration.setTransitive(false);
		});
		project.getConfigurations().create(ATTACHE_MANIFEST_CONFIGURATION, configuration -> {
			configuration.setCanBeConsumed(false);
			configuration.setCanBeResolved(true);
			configuration.setTransitive(false);
			configuration.extendsFrom(attache, attacheOnly);
		});

		project.getPluginManager().withPlugin("java-base", unused -> {
			project.getConfigurations().named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, configuration -> configuration.extendsFrom(attache));

			var descriptorTask = project.getTasks().register(GENERATE_TASK, GenerateAttacheDescriptorTask.class, task -> {
				task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/resources/attache"));
				task.getDescriptorPath().convention(project.provider(() -> GenerateAttacheDescriptorTask.descriptorPathFor(project)));
				task.getDescriptorContent().convention(project.provider(() ->
						GenerateAttacheDescriptorTask.renderDescriptor(project)
				));
			});

			SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
			sourceSets.named("main", sourceSet -> sourceSet.getResources().srcDir(descriptorTask.map(GenerateAttacheDescriptorTask::getOutputDirectory)));
			project.getTasks().named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, task -> task.dependsOn(descriptorTask));
		});

		project.getPluginManager().withPlugin("java-library", unused ->
				project.getConfigurations().named("compileOnlyApi", configuration -> configuration.extendsFrom(attacheApi))
		);
	}

	@NotNull
	private static AttacheExtension attacheExtension(@NotNull Project project, @Nullable AttacheExtension inheritedDefaults) {
		AttacheExtension existing = project.getExtensions().findByType(AttacheExtension.class);
		if (existing != null) {
			return existing;
		}

		if (inheritedDefaults != null && project != project.getRootProject()) {
			return project.getExtensions().create(
					ATTACHE_EXTENSION,
					AttacheExtension.class,
					project.getObjects(),
					inheritedDefaults
			);
		}

		return project.getExtensions().create(
				ATTACHE_EXTENSION,
				AttacheExtension.class,
				project.getObjects()
		);
	}

	@NotNull
	private static AttacheExtension rootDefaults(@NotNull Project project) {
		Project rootProject = project.getRootProject();
		if (project == rootProject) {
			return attacheExtension(rootProject, null);
		}

		return attacheExtension(rootProject, null);
	}
}
