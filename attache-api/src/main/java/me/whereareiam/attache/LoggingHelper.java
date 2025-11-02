package me.whereareiam.attache;

import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Adapter interface for logging operations.
 * Implementations should bridge Attache logging to the platform's logger.
 */
public interface LoggingHelper {
	/**
	 * Logs a message at the specified log level.
	 *
	 * @param level   the log level
	 * @param message the message to log
	 */
	void log(@NotNull Level level, @NotNull String message);

	/**
	 * Logs a message with a throwable at the specified log level.
	 *
	 * @param level     the log level
	 * @param message   the message to log
	 * @param throwable the throwable to log
	 */
	void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable);
}

