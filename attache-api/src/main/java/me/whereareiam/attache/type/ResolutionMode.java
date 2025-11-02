package me.whereareiam.attache.type;

/**
 * Enum representing the resolution mode of repositories, determining the order in which repositories are searched when resolving dependencies.
 */
public enum ResolutionMode {
	/**
	 * The default resolution mode searches for library repositories first, followed by global repositories, and then library fallback repositories if necessary.
	 */
	DEFAULT,
	/**
	 * With GLOBAL_FIRST, global repositories are searched first, followed by library repositories, and then library fallback repositories if necessary.
	 */
	GLOBAL_FIRST,
	/**
	 * With LIBRARY_FIRST, library repositories are searched first, followed by library fallback repositories, and then global repositories if necessary.
	 */
	LIBRARY_FIRST
}

