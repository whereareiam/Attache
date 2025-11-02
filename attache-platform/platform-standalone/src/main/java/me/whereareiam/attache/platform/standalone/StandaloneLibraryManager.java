package me.whereareiam.attache.platform.standalone;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.common.classloader.SystemClassLoaderHelper;
import me.whereareiam.attache.common.classloader.URLClassLoaderHelper;
import org.jetbrains.annotations.NotNull;

import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * A runtime dependency manager for standalone Java applications.
 */
@SuppressWarnings("unused")
public class StandaloneLibraryManager extends BaseLibraryManager {
	/**
	 * URL class loader helper (if applicable)
	 */
	private final URLClassLoaderHelper urlClassLoaderHelper;

	/**
	 * System class loader helper (if applicable)
	 */
	private final SystemClassLoaderHelper systemClassLoaderHelper;

	/**
	 * Creates a new Standalone library manager using the classloader of the current class.
	 *
	 * @param loggingHelper the log adapter to use
	 * @param dataDirectory data directory
	 */
	public StandaloneLibraryManager(@NotNull LoggingHelper loggingHelper, @NotNull Path dataDirectory) {
		this(loggingHelper, dataDirectory, "lib");
	}

	/**
	 * Creates a new Standalone library manager using the classloader of the current class.
	 *
	 * @param loggingHelper the log adapter to use
	 * @param dataDirectory data directory
	 * @param directoryName download directory name
	 */
	public StandaloneLibraryManager(@NotNull LoggingHelper loggingHelper, @NotNull Path dataDirectory, @NotNull String directoryName) {
		super(loggingHelper, dataDirectory, directoryName);
		ClassLoader classLoader = getClass().getClassLoader();
		if (classLoader instanceof URLClassLoader) {
			this.urlClassLoaderHelper = new URLClassLoaderHelper((URLClassLoader) classLoader);
			this.systemClassLoaderHelper = null;
			return;
		}

		if (classLoader == ClassLoader.getSystemClassLoader()) {
			this.urlClassLoaderHelper = null;
			this.systemClassLoaderHelper = new SystemClassLoaderHelper(classLoader);
			return;
		}

		throw new RuntimeException("Unsupported class loader: " + classLoader.getClass().getName());
	}

	@Override
	protected void addToClasspath(@NotNull Path file) {
		if (urlClassLoaderHelper != null) {
			urlClassLoaderHelper.addToClasspath(file);
			return;
		}

		if (systemClassLoaderHelper != null) {
			systemClassLoaderHelper.addToClasspath(file);
			return;
		}

		throw new IllegalStateException("No class loader helper available");
	}
}

