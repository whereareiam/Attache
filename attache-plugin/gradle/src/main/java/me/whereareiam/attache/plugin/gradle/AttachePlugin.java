package me.whereareiam.attache.plugin.gradle;

import me.whereareiam.attache.plugin.gradle.extension.AttacheMetadataExtension;
import me.whereareiam.attache.plugin.gradle.task.GenerateAttacheDescriptorTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Gradle plugin entry point for Attache descriptor generation.
 */
public class AttachePlugin implements Plugin<Project> {
	public static final String ATTACHE_CONFIGURATION = "attache";
	public static final String ATTACHE_ONLY_CONFIGURATION = "attacheOnly";
	public static final String ATTACHE_MANIFEST_CONFIGURATION = "attacheManifest";
	public static final String ATTACHE_EXTENSION = "attacheMetadata";
	public static final String GENERATE_TASK = "generateAttacheDescriptor";

	@Override
	public void apply(Project project) {
		project.getExtensions().create(
				ATTACHE_EXTENSION,
				AttacheMetadataExtension.class,
				project.getObjects()
		);

		Configuration attache = project.getConfigurations().create(ATTACHE_CONFIGURATION, configuration -> {
			configuration.setCanBeConsumed(false);
			configuration.setCanBeResolved(false);
			configuration.setTransitive(false);
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
			});

			SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
			sourceSets.named("main", sourceSet -> sourceSet.getResources().srcDir(descriptorTask.map(GenerateAttacheDescriptorTask::getOutputDirectory)));
			project.getTasks().named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, task -> task.dependsOn(descriptorTask));
		});
	}
}
