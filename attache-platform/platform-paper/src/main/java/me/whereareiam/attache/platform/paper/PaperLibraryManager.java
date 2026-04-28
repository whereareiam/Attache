package me.whereareiam.attache.platform.paper;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.common.classloader.URLClassLoaderHelper;
import me.whereareiam.attache.common.logging.adapter.JDKLoggingHelper;
import me.whereareiam.attache.type.VerbosityMode;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * A runtime dependency manager for Paper Plugins (Not to be confused with bukkit plugins loaded on paper).
 * See: <a href="https://docs.papermc.io/paper/dev/getting-started/paper-plugins">Paper docs</a>
 */
@SuppressWarnings("unused")
public class PaperLibraryManager extends BaseLibraryManager {
	/**
	 * Plugin classpath helper
	 */
	@NotNull
	private final URLClassLoaderHelper classLoaderHelper;
	@NotNull
	private final ClassLoader descriptorClassLoader;

	/**
	 * Creates a new Paper library manager.
	 *
	 * @param plugin the plugin to manage
	 */
	public PaperLibraryManager(@NotNull Plugin plugin) {
		this(plugin, "lib");
	}

	/**
	 * Creates a new Paper library manager.
	 *
	 * @param plugin        the plugin to manage
	 * @param directoryName download directory name
	 */
	public PaperLibraryManager(@NotNull Plugin plugin, @NotNull String directoryName) {
		this(plugin, directoryName, new JDKLoggingHelper(requireNonNull(plugin, "plugin").getLogger()));
	}

	/**
	 * Creates a new Paper library manager.
	 *
	 * @param plugin        the plugin to manage
	 * @param directoryName download directory name
	 * @param loggingHelper the log adapter to use
	 */
	public PaperLibraryManager(@NotNull Plugin plugin, @NotNull String directoryName, @NotNull LoggingHelper loggingHelper) {
		this(plugin, directoryName, loggingHelper, VerbosityMode.VERBOSE);
	}

	public PaperLibraryManager(
			@NotNull Plugin plugin,
			@NotNull String directoryName,
			@NotNull LoggingHelper loggingHelper,
			@NotNull VerbosityMode verbosityMode
	) {
		super(loggingHelper, plugin.getDataFolder().toPath(), directoryName);
		setVerbosityMode(requireNonNull(verbosityMode, "verbosityMode"));

		ClassLoader cl = plugin.getClass().getClassLoader();
		Class<?> paperClClazz;

		try {
			paperClClazz = Class.forName("io.papermc.paper.plugin.entrypoint.classloader.PaperPluginClassLoader");
		} catch (ClassNotFoundException e) {
			getLogger().error("PaperPluginClassLoader not found, are you using Paper 1.19.3+?");
			throw new RuntimeException(e);
		}

		if (!paperClClazz.isAssignableFrom(cl.getClass())) {
			throw new RuntimeException("Plugin classloader is not a PaperPluginClassLoader, are you using paper-plugin.yml?");
		}

		Field libraryLoaderField;

		try {
			libraryLoaderField = paperClClazz.getDeclaredField("libraryLoader");
		} catch (NoSuchFieldException e) {
			getLogger().error("Cannot find libraryLoader field in PaperPluginClassLoader, please open a bug report.");
			throw new RuntimeException(e);
		}

		libraryLoaderField.setAccessible(true);

		URLClassLoader libraryLoader;
		try {
			libraryLoader = (URLClassLoader) libraryLoaderField.get(cl);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e); // Should never happen
		}

		this.descriptorClassLoader = cl;
		classLoaderHelper = new URLClassLoaderHelper(libraryLoader);
	}

	/**
	 * Adds a file to the Paper plugin's classpath.
	 *
	 * @param file the file to add
	 */
	@Override
	protected void addToClasspath(@NotNull Path file) {
		classLoaderHelper.addToClasspath(file);
	}

	@Override
	protected @NotNull ClassLoader getDescriptorClassLoader() {
		return descriptorClassLoader;
	}
}
