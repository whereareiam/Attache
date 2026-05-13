package me.whereareiam.attache.platform.bungeecord;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.common.classloader.URLClassLoaderHelper;
import me.whereareiam.attache.common.logging.adapter.JDKLoggingHelper;
import me.whereareiam.attache.type.VerbosityMode;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.net.URLClassLoader;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * A runtime dependency manager for BungeeCord plugins.
 */
@SuppressWarnings("unused")
public class BungeeCordLibraryManager extends BaseLibraryManager {
	/**
	 * Plugin classpath helper
	 */
	@NotNull
	private final URLClassLoaderHelper classLoaderHelper;
	@NotNull
	private final ClassLoader descriptorClassLoader;

	/**
	 * Creates a new BungeeCord library manager.
	 *
	 * @param plugin the plugin to manage
	 */
	public BungeeCordLibraryManager(@NotNull Plugin plugin) {
		this(plugin, "lib");
	}

	/**
	 * Creates a new BungeeCord library manager.
	 *
	 * @param plugin        the plugin to manage
	 * @param directoryName download directory name
	 */
	public BungeeCordLibraryManager(@NotNull Plugin plugin, @NotNull String directoryName) {
		this(plugin, directoryName, new JDKLoggingHelper(requireNonNull(plugin, "plugin").getLogger()));
	}

	/**
	 * Creates a new BungeeCord library manager.
	 *
	 * @param plugin        the plugin to manage
	 * @param directoryName download directory name
	 * @param loggingHelper the log adapter to use
	 */
	public BungeeCordLibraryManager(@NotNull Plugin plugin, @NotNull String directoryName, @NotNull LoggingHelper loggingHelper) {
		this(plugin, directoryName, loggingHelper, VerbosityMode.VERBOSE);
	}

	/**
	 * Creates a new BungeeCord library manager.
	 *
	 * @param plugin        the plugin to manage
	 * @param directoryName download directory name
	 * @param loggingHelper the log adapter to use
	 * @param verbosityMode logging verbosity
	 */
	public BungeeCordLibraryManager(
			@NotNull Plugin plugin,
			@NotNull String directoryName,
			@NotNull LoggingHelper loggingHelper,
			@NotNull VerbosityMode verbosityMode
	) {
		super(loggingHelper, plugin.getDataFolder().toPath(), directoryName);
		setVerbosityMode(requireNonNull(verbosityMode, "verbosityMode"));

		ClassLoader classLoader = plugin.getClass().getClassLoader();
		if (!(classLoader instanceof URLClassLoader))
			throw new RuntimeException("Plugin classloader is not a URLClassLoader");

		this.descriptorClassLoader = classLoader;
		this.classLoaderHelper = new URLClassLoaderHelper((URLClassLoader) classLoader);
	}

	/**
	 * Adds a file to the BungeeCord plugin's classpath.
	 *
	 * @param file the file to add
	 */
	@Override
	protected void addToClasspath(@NotNull Path file) {
		classLoaderHelper.addToClasspath(file);
	}

	@Override
	protected @NonNull ClassLoader getDescriptorClassLoader() {
		return descriptorClassLoader;
	}
}
