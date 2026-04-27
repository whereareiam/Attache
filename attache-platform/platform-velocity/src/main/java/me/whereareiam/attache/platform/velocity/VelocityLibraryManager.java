package me.whereareiam.attache.platform.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.common.logging.adapter.JDKLoggingHelper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static java.util.Objects.requireNonNull;

/**
 * A runtime dependency manager for Velocity plugins.
 */
@SuppressWarnings("unused")
public class VelocityLibraryManager extends BaseLibraryManager {
	/**
	 * Velocity proxy server instance
	 */
	@NotNull
	private final ProxyServer proxyServer;

	/**
	 * The plugin container
	 */
	@NotNull
	private final PluginContainer pluginContainer;
	@NotNull
	private final ClassLoader descriptorClassLoader;

	/**
	 * Creates a new Velocity library manager.
	 *
	 * @param proxyServer     the proxy server instance
	 * @param pluginContainer the plugin container
	 * @param logger          the plugin logger
	 * @param dataDirectory   plugin's data directory
	 */
	public VelocityLibraryManager(@NotNull ProxyServer proxyServer, @NotNull PluginContainer pluginContainer, @NotNull Logger logger, @NotNull Path dataDirectory) {
		this(proxyServer, pluginContainer, logger, dataDirectory, "lib");
	}

	/**
	 * Creates a new Velocity library manager.
	 *
	 * @param proxyServer     the proxy server instance
	 * @param pluginContainer the plugin container
	 * @param logger          the plugin logger
	 * @param dataDirectory   plugin's data directory
	 * @param directoryName   download directory name
	 */
	public VelocityLibraryManager(@NotNull ProxyServer proxyServer, @NotNull PluginContainer pluginContainer, @NotNull Logger logger, @NotNull Path dataDirectory, @NotNull String directoryName) {
		this(proxyServer, pluginContainer, new JDKLoggingHelper(adaptSlf4jLogger(requireNonNull(logger, "logger"))), dataDirectory, directoryName);
	}

	/**
	 * Creates a new Velocity library manager.
	 *
	 * @param proxyServer     the proxy server instance
	 * @param pluginContainer the plugin container
	 * @param loggingHelper   the log adapter to use
	 * @param dataDirectory   plugin's data directory
	 * @param directoryName   download directory name
	 */
	public VelocityLibraryManager(@NotNull ProxyServer proxyServer, @NotNull PluginContainer pluginContainer, @NotNull LoggingHelper loggingHelper, @NotNull Path dataDirectory, @NotNull String directoryName) {
		this(proxyServer, pluginContainer, loggingHelper, dataDirectory, directoryName, true);
	}

	public VelocityLibraryManager(
			@NotNull ProxyServer proxyServer,
			@NotNull PluginContainer pluginContainer,
			@NotNull LoggingHelper loggingHelper,
			@NotNull Path dataDirectory,
			@NotNull String directoryName,
			boolean autoLoadDescriptors
	) {
		super(loggingHelper, dataDirectory, directoryName);
		this.proxyServer = requireNonNull(proxyServer, "proxyServer");
		this.pluginContainer = requireNonNull(pluginContainer, "pluginContainer");
		this.descriptorClassLoader = this.pluginContainer.getInstance()
				.map(instance -> instance.getClass().getClassLoader())
				.orElseGet(() -> getClass().getClassLoader());
		if (autoLoadDescriptors) {
			loadClasspathDescriptors();
		}
	}

	/**
	 * Adds a file to the Velocity plugin's classpath.
	 *
	 * @param file the file to add
	 */
	@Override
	protected void addToClasspath(@NotNull Path file) {
		proxyServer.getPluginManager().addToClasspath(pluginContainer.getInstance().get(), file);
	}

	/**
	 * Adapts an SLF4J logger to a JUL logger for compatibility with JDKLoggingHelper.
	 *
	 * @param slf4jLogger the SLF4J logger
	 * @return adapted JUL logger
	 */
	private static java.util.logging.Logger adaptSlf4jLogger(@NotNull Logger slf4jLogger) {
		java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(slf4jLogger.getName());
		julLogger.setUseParentHandlers(false);
		
		julLogger.addHandler(new Handler() {
			@Override
			public void publish(LogRecord record) {
				String message = record.getMessage();
				Level level = record.getLevel();
				
				if (level.intValue() >= Level.SEVERE.intValue()) {
					slf4jLogger.error(message, record.getThrown());
					return;
				}

				if (level.intValue() >= Level.WARNING.intValue()) {
					slf4jLogger.warn(message, record.getThrown());
					return;
				}

				if (level.intValue() >= Level.INFO.intValue()) {
					slf4jLogger.info(message, record.getThrown());
					return;
				}

				slf4jLogger.debug(message, record.getThrown());
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		});
		
		return julLogger;
	}

	@Override
	protected @NotNull ClassLoader getDescriptorClassLoader() {
		return descriptorClassLoader;
	}
}
