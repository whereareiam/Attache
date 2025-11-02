package me.whereareiam.attache.common.logging.adapter;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * Logging adapter that logs to a JDK logger.
 */
public class JDKLoggingHelper implements LoggingHelper {
	/**
	 * JDK logger
	 */
	private final Logger logger;

	/**
	 * Creates a new JDK log adapter that logs to a {@link Logger}.
	 *
	 * @param logger the JDK logger to wrap
	 */
	public JDKLoggingHelper(@NotNull Logger logger) {
		this.logger = requireNonNull(logger, "logger");
	}

	/**
	 * Logs a message with the provided level to the JDK logger.
	 *
	 * @param level   message severity level
	 * @param message the message to log
	 */
	@Override
	public void log(@NotNull Level level, @NotNull String message) {
		switch (requireNonNull(level, "level")) {
			case DEBUG:
				logger.log(java.util.logging.Level.FINE, message);
				break;
			case INFO:
				logger.log(java.util.logging.Level.INFO, message);
				break;
			case WARN:
				logger.log(java.util.logging.Level.WARNING, message);
				break;
			case ERROR:
				logger.log(java.util.logging.Level.SEVERE, message);
				break;
		}
	}

	/**
	 * Logs a message and stack trace with the provided level to the JDK
	 * logger.
	 *
	 * @param level     message severity level
	 * @param message   the message to log
	 * @param throwable the throwable to print
	 */
	@Override
	public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
		switch (requireNonNull(level, "level")) {
			case DEBUG:
				logger.log(java.util.logging.Level.FINE, message, throwable);
				break;
			case INFO:
				logger.log(java.util.logging.Level.INFO, message, throwable);
				break;
			case WARN:
				logger.log(java.util.logging.Level.WARNING, message, throwable);
				break;
			case ERROR:
				logger.log(java.util.logging.Level.SEVERE, message, throwable);
				break;
		}
	}
}

