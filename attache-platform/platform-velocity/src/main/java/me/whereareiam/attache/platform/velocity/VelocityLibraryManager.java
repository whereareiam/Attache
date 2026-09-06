package me.whereareiam.attache.platform.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.launcher.AttacheLauncher;
import me.whereareiam.attache.type.Level;
import me.whereareiam.attache.type.VerbosityMode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;

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
		this(proxyServer, pluginContainer, logger, dataDirectory, directoryName, VerbosityMode.VERBOSE);
	}

	public VelocityLibraryManager(
			@NotNull ProxyServer proxyServer,
			@NotNull PluginContainer pluginContainer,
			@NotNull Logger logger,
			@NotNull Path dataDirectory,
			@NotNull String directoryName,
			@NotNull VerbosityMode verbosityMode
	) {
		this(
				proxyServer,
				pluginContainer,
				createLoggingHelper(requireNonNull(logger, "logger")),
				dataDirectory,
				directoryName,
				verbosityMode
		);
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
		this(proxyServer, pluginContainer, loggingHelper, dataDirectory, directoryName, VerbosityMode.VERBOSE);
	}

	public VelocityLibraryManager(
			@NotNull ProxyServer proxyServer,
			@NotNull PluginContainer pluginContainer,
			@NotNull LoggingHelper loggingHelper,
			@NotNull Path dataDirectory,
			@NotNull String directoryName,
			@NotNull VerbosityMode verbosityMode
	) {
		super(loggingHelper, dataDirectory, directoryName, new AttacheLauncher());
		this.proxyServer = requireNonNull(proxyServer, "proxyServer");
		this.pluginContainer = requireNonNull(pluginContainer, "pluginContainer");
		this.descriptorClassLoader = this.pluginContainer.getInstance()
				.map(instance -> instance.getClass().getClassLoader())
				.orElseGet(() -> getClass().getClassLoader());
		setVerbosityMode(requireNonNull(verbosityMode, "verbosityMode"));
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
	 * Adapts an SLF4J logger to Attache's logging abstraction directly.
	 *
	 * @param slf4jLogger the SLF4J logger
	 * @return adapted logging helper
	 */
	@NotNull
	private static LoggingHelper createLoggingHelper(@NotNull Logger slf4jLogger) {
		return new LoggingHelper() {
			@Override
			public void log(@NotNull Level level, @NotNull String message) {
				switch (level) {
					case DEBUG -> slf4jLogger.debug(message);
					case INFO -> slf4jLogger.info(message);
					case WARN -> slf4jLogger.warn(message);
					case ERROR -> slf4jLogger.error(message);
				}
			}

			@Override
			public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
				switch (level) {
					case DEBUG -> slf4jLogger.debug(message, throwable);
					case INFO -> slf4jLogger.info(message, throwable);
					case WARN -> slf4jLogger.warn(message, throwable);
					case ERROR -> slf4jLogger.error(message, throwable);
				}
			}
		};
	}

	@Override
	protected @NotNull ClassLoader getDescriptorClassLoader() {
		return descriptorClassLoader;
	}
}
