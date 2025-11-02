package me.whereareiam.attache.model;

import lombok.NonNull;
import lombok.Value;

/**
 * Represents a dependency that should be excluded during transitive dependency resolution.
 */
@Value
public class ExcludedDependency {
	/**
	 * Maven group ID
	 */
	@NonNull
	String groupId;

	/**
	 * Maven artifact ID
	 */
	@NonNull
	String artifactId;

	/**
	 * Custom toString to format as Maven coordinates.
	 *
	 * @return formatted string "groupId:artifactId"
	 */
	@Override
	public String toString() {
		return groupId + ':' + artifactId;
	}
}

