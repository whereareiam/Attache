package me.whereareiam.attache.platform.bukkit;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.common.classloader.URLClassLoaderHelper;
import me.whereareiam.attache.common.logging.adapter.JDKLoggingHelper;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.net.URLClassLoader;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * A runtime dependency manager for Bukkit plugins.
 */
@SuppressWarnings("unused")
public class BukkitLibraryManager extends BaseLibraryManager {
	/**
	 * Plugin classpath helper
	 */
	@NotNull
	private final URLClassLoaderHelper classLoaderHelper;

	/**
	 * Creates a new Bukkit library manager.
	 *
	 * @param plugin the plugin to manage
	 */
	public BukkitLibraryManager(@NotNull Plugin plugin) {
		this(plugin, "lib");
	}

	/**
	 * Creates a new Bukkit library manager.
	 *
	 * @param plugin        the plugin to manage
	 * @param directoryName download directory name
	 */
	public BukkitLibraryManager(@NotNull Plugin plugin, @NotNull String directoryName) {
		this(plugin, directoryName, new JDKLoggingHelper(requireNonNull(plugin, "plugin").getLogger()));
	}

	/**
	 * Creates a new Bukkit library manager.
	 *
	 * @param plugin        the plugin to manage
	 * @param directoryName download directory name
	 * @param loggingHelper the log adapter to use
	 */
	public BukkitLibraryManager(@NotNull Plugin plugin, @NotNull String directoryName, @NotNull LoggingHelper loggingHelper) {
		super(loggingHelper, plugin.getDataFolder().toPath(), directoryName);

		ClassLoader classLoader = plugin.getClass().getClassLoader();
		if (!(classLoader instanceof URLClassLoader))
			throw new RuntimeException("Plugin classloader is not a URLClassLoader");

		this.classLoaderHelper = new URLClassLoaderHelper((URLClassLoader) classLoader);
	}

	/**
	 * Adds a file to the Bukkit plugin's classpath.
	 *
	 * @param file the file to add
	 */
	@Override
	protected void addToClasspath(@NotNull Path file) {
		classLoaderHelper.addToClasspath(file);
	}
}

