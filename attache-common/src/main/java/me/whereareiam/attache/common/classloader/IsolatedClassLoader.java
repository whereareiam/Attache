package me.whereareiam.attache.common.classloader;

import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * This class loader is a simple child of {@code URLClassLoader} that uses
 * the JVM's Extensions Class Loader as the parent instead of the system class
 * loader to provide an unpolluted classpath.
 */
public class IsolatedClassLoader extends URLClassLoader {
	static {
		ClassLoader.registerAsParallelCapable();
	}

	/**
	 * Creates a new isolated class loader for the given URLs.
	 *
	 * @param urls the URLs to add to the classpath
	 */
	public IsolatedClassLoader(@NotNull URL... urls) {
		super(requireNonNull(urls, "urls"), ClassLoader.getSystemClassLoader().getParent());
	}

	/**
	 * Adds a URL to the classpath.
	 *
	 * @param url the URL to add
	 */
	@Override
	public void addURL(@NotNull URL url) {
		super.addURL(url);
	}

	/**
	 * Adds a path to the classpath.
	 *
	 * @param path the path to add
	 */
	public void addPath(@NotNull Path path) {
		try {
			addURL(requireNonNull(path, "path").toUri().toURL());
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
	}
}

