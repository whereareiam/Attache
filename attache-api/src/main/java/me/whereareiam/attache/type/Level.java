package me.whereareiam.attache.type;

/**
 * Represents the severity of a log message.
 */
public enum Level {
	/**
	 * Stuff that isn't useful to end-users
	 */
	DEBUG,

	/**
	 * Stuff that might be useful to know
	 */
	INFO,

	/**
	 * Non-fatal, often recoverable errors or notices
	 */
	WARN,

	/**
	 * Probably an unrecoverable error
	 */
	ERROR
}

