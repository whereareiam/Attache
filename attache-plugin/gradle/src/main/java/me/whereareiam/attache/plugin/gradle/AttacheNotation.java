package me.whereareiam.attache.plugin.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class AttacheNotation {
	@NotNull
	public static String keyFromNotation(@NotNull String notation) {
		String[] parts = Objects.requireNonNull(notation, "notation").split(":");
		if (parts.length < 2 || parts.length > 3) {
			throw new GradleException("Attache library notation must be 'group:artifact' or 'group:artifact:version': " + notation);
		}

		return parts[0] + ':' + parts[1];
	}

	@NotNull
	public static String keyFromDependency(@NotNull MinimalExternalModuleDependency dependency) {
		return Objects.requireNonNull(dependency, "dependency").getModule().getGroup()
				+ ':'
				+ dependency.getModule().getName();
	}
}
