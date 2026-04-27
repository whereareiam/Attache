package me.whereareiam.attache.plugin.gradle.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Serialized transitive exclusion entry for the generated descriptor.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DescriptorExcludedDependency {
	private String groupId;
	private String artifactId;
}
