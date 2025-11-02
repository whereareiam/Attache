package me.whereareiam.attache.type;

/**
 * Logging verbosity modes for library loading operations.
 */
public enum VerbosityMode {
	/**
	 * Log everything including debug information.
	 */
	VERBOSE,

	/**
	 * Log important operations (default behavior).
	 */
	NORMAL,

	/**
	 * Only log warnings and errors, plus a final summary.
	 */
	SUMMARY,

	/**
	 * Only log errors (silent mode).
	 */
	QUIET
}

