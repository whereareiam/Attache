package me.whereareiam.attache.platform.spring.logging;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * {@link LoggingHelper} implementation that bridges Attache logs to SLF4J.
 */
public class SpringLoggingHelper implements LoggingHelper {
    private final Logger logger;

    /**
     * Creates a logging helper that logs to a default "Attache" logger name.
     */
    public SpringLoggingHelper() {
        this(LoggerFactory.getLogger("Attache"));
    }

    /**
     * Creates a logging helper that logs to the provided SLF4J logger.
     *
     * @param logger target logger
     */
    public SpringLoggingHelper(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void log(Level level, @NotNull String message) {
        switch (level) {
            case DEBUG -> logger.debug(message);
            case INFO -> logger.info(message);
            case WARN -> logger.warn(message);
            case ERROR -> logger.error(message);
        }
    }

    @Override
    public void log(Level level, @NotNull String message, @NotNull Throwable throwable) {
        switch (level) {
            case DEBUG -> logger.debug(message, throwable);
            case INFO -> logger.info(message, throwable);
            case WARN -> logger.warn(message, throwable);
            case ERROR -> logger.error(message, throwable);
        }
    }
}
