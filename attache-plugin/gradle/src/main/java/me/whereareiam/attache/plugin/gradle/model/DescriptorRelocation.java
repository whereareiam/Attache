package me.whereareiam.attache.plugin.gradle.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Serialized relocation entry for the generated descriptor.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DescriptorRelocation {
	private String pattern;
	private String relocatedPattern;
}
