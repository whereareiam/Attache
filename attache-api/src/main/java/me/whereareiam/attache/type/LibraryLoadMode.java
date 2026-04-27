package me.whereareiam.attache.type;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum LibraryLoadMode {
	PARALLEL,
	SEQUENTIAL;

	public static final String SYSTEM_PROPERTY = "attache.load.mode";
	public static final String ENV_VARIABLE = "ATTACHE_LOAD_MODE";

	@NotNull
	public static LibraryLoadMode resolve(@Nullable String configuredValue) {
		if (configuredValue == null || configuredValue.isBlank())
			return PARALLEL;

		return switch (configuredValue.trim().toUpperCase()) {
			case "PARALLEL" -> PARALLEL;
			case "SEQUENTIAL" -> SEQUENTIAL;
			default -> throw new IllegalArgumentException("Unsupported load mode: " + configuredValue);
		};
	}
}
