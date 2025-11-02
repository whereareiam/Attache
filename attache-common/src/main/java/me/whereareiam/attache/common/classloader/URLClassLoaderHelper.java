package me.whereareiam.attache.common.classloader;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * A reflection-based wrapper around {@link URLClassLoader} for adding URLs to
 * the classpath.
 */
public class URLClassLoaderHelper {
    private final URLClassLoader classLoader;
    private final Method addURLMethod;

    /**
     * Creates a new URL class loader helper.
     *
     * @param classLoader the class loader to manage
     */
    public URLClassLoaderHelper(@NotNull URLClassLoader classLoader) {
        this.classLoader = requireNonNull(classLoader, "classLoader");

        try {
            addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURLMethod.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Couldn't initialize URLClassLoaderHelper", e);
        }
    }

    /**
     * Adds a URL to the classpath.
     *
     * @param url the URL to add
     */
    public void addToClasspath(@NotNull URL url) {
        try {
            addURLMethod.invoke(classLoader, requireNonNull(url, "url"));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to add URL to classpath", e);
        }
    }

    /**
     * Adds a path to the classpath.
     *
     * @param path the path to add
     */
    public void addToClasspath(@NotNull Path path) {
        try {
            addToClasspath(requireNonNull(path, "path").toUri().toURL());
        } catch (Exception e) {
            throw new RuntimeException("Failed to add path to classpath", e);
        }
    }
}

