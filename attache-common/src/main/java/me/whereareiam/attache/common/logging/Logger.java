package me.whereareiam.attache.common.logging;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A logging wrapper that logs to a log adapter and can be configured to filter
 * log messages by severity.
 */
public class Logger {
	/**
	 * Log adapter for the current platform
	 */
	private final LoggingHelper loggingHelper;

	/**
	 * Log level controlling which messages are logged
	 */
	private Level level = Level.INFO;

	/**
	 * Creates a new logger with the provided adapter.
	 *
	 * @param loggingHelper the adapter to wrap
	 */
	public Logger(@NotNull LoggingHelper loggingHelper) {
		this.loggingHelper = requireNonNull(loggingHelper, "adapter");
	}

	/**
	 * Gets the current log level.
	 *
	 * @return current log level
	 */
	@NotNull
	public Level getLevel() {
		return level;
	}

	/**
	 * Sets a new log level.
	 *
	 * @param level new log level
	 */
	public void setLevel(@NotNull Level level) {
		this.level = requireNonNull(level, "level");
	}

	/**
	 * Gets whether messages matching the provided level can be logged under
	 * the current log level setting.
	 * <p>
	 * Returns true if provided log level is equal to or more severe than the
	 * logger's configured log level.
	 *
	 * @param level the level to check
	 * @return true if message can be logged, or false
	 */
	private boolean canLog(@NotNull Level level) {
		return requireNonNull(level, "level").compareTo(this.level) >= 0;
	}

	/**
	 * Logs a message with the provided level.
	 * <p>
	 * If the provided log level is less severe than the logger's
	 * configured log level, this message won't be logged.
	 *
	 * @param level   message severity level
	 * @param message the message to log
	 */
	public void log(@NotNull Level level, @Nullable String message) {
		if (canLog(level)) {
			loggingHelper.log(level, message);
		}
	}

	/**
	 * Logs a message and stack trace with the provided level.
	 * <p>
	 * If the provided log level is less severe than the logger's
	 * configured log level, this message won't be logged.
	 *
	 * @param level     message severity level
	 * @param message   the message to log
	 * @param throwable the throwable to print
	 */
	public void log(@NotNull Level level, @Nullable String message, @Nullable Throwable throwable) {
		if (canLog(level)) {
			loggingHelper.log(level, message, throwable);
		}
	}

	/**
	 * Logs a debug message.
	 *
	 * @param message the message to log
	 */
	public void debug(String message) {
		log(Level.DEBUG, message);
	}

	/**
	 * Logs a debug message with a stack trace.
	 *
	 * @param message   the message to log
	 * @param throwable the throwable to print
	 */
	public void debug(@Nullable String message, @Nullable Throwable throwable) {
		log(Level.DEBUG, message, throwable);
	}

	/**
	 * Logs an informational message.
	 *
	 * @param message the message to log
	 */
	public void info(@Nullable String message) {
		log(Level.INFO, message);
	}

	/**
	 * Logs an informational message with a stack trace.
	 *
	 * @param message   the message to log
	 * @param throwable the throwable to print
	 */
	public void info(@Nullable String message, @Nullable Throwable throwable) {
		log(Level.INFO, message, throwable);
	}

	/**
	 * Logs a warning message.
	 *
	 * @param message the message to log
	 */
	public void warn(@Nullable String message) {
		log(Level.WARN, message);
	}

	/**
	 * Logs a warning message with a stack trace.
	 *
	 * @param message   the message to log
	 * @param throwable the throwable to print
	 */
	public void warn(@Nullable String message, @Nullable Throwable throwable) {
		log(Level.WARN, message, throwable);
	}

	/**
	 * Logs an error message.
	 *
	 * @param message the message to log
	 */
	public void error(@Nullable String message) {
		log(Level.ERROR, message);
	}

	/**
	 * Logs an error message with a stack trace.
	 *
	 * @param message   message to log
	 * @param throwable the throwable to print
	 */
	public void error(@Nullable String message, @Nullable Throwable throwable) {
		log(Level.ERROR, message, throwable);
	}
}

