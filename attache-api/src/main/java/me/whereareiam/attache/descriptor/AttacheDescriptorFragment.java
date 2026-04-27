package me.whereareiam.attache.descriptor;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Descriptor fragment contributed by a single build module.
 */
@Getter
@Setter
@EqualsAndHashCode
public class AttacheDescriptorFragment {
	private String projectPath;
	private String projectName;
	private boolean addMavenCentral = true;
	private final Set<String> repositories = new LinkedHashSet<>();
	private final List<AttacheDescriptorLibrary> libraries = new ArrayList<>();
}
