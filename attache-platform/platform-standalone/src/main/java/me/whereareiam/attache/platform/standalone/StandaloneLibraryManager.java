package me.whereareiam.attache.platform.standalone;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.common.classloader.SystemClassLoaderHelper;
import me.whereareiam.attache.common.classloader.URLClassLoaderHelper;
import me.whereareiam.attache.launcher.AttacheLauncher;
import me.whereareiam.attache.type.VerbosityMode;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A runtime dependency manager for standalone Java applications.
 */
@SuppressWarnings("unused")
public class StandaloneLibraryManager extends BaseLibraryManager {
	private final ClassLoader targetLoader;
	private final Method addPathMethod;
	private final Method addUrlMethod;
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
		this(loggingHelper, dataDirectory, directoryName, StandaloneLibraryManager.class.getClassLoader());
	}

	/**
	 * Creates a new Standalone library manager for a specific classloader.
	 *
	 * @param loggingHelper the log adapter to use
	 * @param dataDirectory data directory
	 * @param directoryName download directory name
	 * @param classLoader   the classloader to add libraries to
	 */
	public StandaloneLibraryManager(
			@NotNull LoggingHelper loggingHelper,
			@NotNull Path dataDirectory,
			@NotNull String directoryName,
			@NotNull ClassLoader classLoader
	) {
		this(loggingHelper, dataDirectory, directoryName, classLoader, VerbosityMode.VERBOSE);
	}

	public StandaloneLibraryManager(
			@NotNull LoggingHelper loggingHelper,
			@NotNull Path dataDirectory,
			@NotNull String directoryName,
			@NotNull ClassLoader classLoader,
			@NotNull VerbosityMode verbosityMode
	) {
		super(loggingHelper, dataDirectory, directoryName, new AttacheLauncher());
		this.targetLoader = Objects.requireNonNull(classLoader, "classLoader");
		setVerbosityMode(Objects.requireNonNull(verbosityMode, "verbosityMode"));
		this.addPathMethod = findPublicMethod(targetLoader, "addPath", Path.class);
		this.addUrlMethod = findPublicMethod(targetLoader, "addURL", URL.class);

		if (targetLoader == ClassLoader.getSystemClassLoader()) {
			this.urlClassLoaderHelper = null;
			this.systemClassLoaderHelper = new SystemClassLoaderHelper(targetLoader);
		} else if (addPathMethod != null || addUrlMethod != null) {
			this.urlClassLoaderHelper = null;
			this.systemClassLoaderHelper = null;
		} else if (targetLoader instanceof URLClassLoader) {
			this.urlClassLoaderHelper = new URLClassLoaderHelper((URLClassLoader) targetLoader);
			this.systemClassLoaderHelper = null;
		} else {
			throw new RuntimeException("Unsupported class loader: " + targetLoader.getClass().getName());
		}
	}

	@Override
	protected void addToClasspath(@NotNull Path file) {
		if (addPathMethod != null) {
			try {
				addPathMethod.invoke(targetLoader, file);
				return;
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException("Failed to add path to classpath", e);
			}
		}

		if (addUrlMethod != null) {
			try {
				addUrlMethod.invoke(targetLoader, file.toUri().toURL());
				return;
			} catch (Exception e) {
				throw new RuntimeException("Failed to add URL to classpath", e);
			}
		}

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

	private static Method findPublicMethod(ClassLoader classLoader, String name, Class<?>... types) {
		try {
			return classLoader.getClass().getMethod(name, types);
		} catch (NoSuchMethodException ignored) {
			return null;
		}
	}

	@Override
	protected @NotNull ClassLoader getDescriptorClassLoader() {
		return targetLoader;
	}
}
