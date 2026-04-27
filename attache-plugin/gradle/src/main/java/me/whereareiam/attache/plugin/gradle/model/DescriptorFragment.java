package me.whereareiam.attache.plugin.gradle.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JSON fragment emitted by the Gradle plugin.
 */
@Getter
@Setter
public class DescriptorFragment {
	private String projectPath;
	private String projectName;
	private boolean addMavenCentral = true;
	private final Set<String> repositories = new LinkedHashSet<>();
	private final List<DescriptorLibrary> libraries = new ArrayList<>();
}
