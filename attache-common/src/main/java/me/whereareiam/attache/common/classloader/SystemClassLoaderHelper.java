package me.whereareiam.attache.common.classloader;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * A reflection-based wrapper around SystemClassLoader for adding URLs to
 * the classpath.
 */
public class SystemClassLoaderHelper {
    private final ClassLoader classLoader;
    private final Method appendMethod;

    /**
     * Creates a new SystemClassLoader helper.
     *
     * @param classLoader the class loader to manage
     */
    public SystemClassLoaderHelper(@NotNull ClassLoader classLoader) {
        this.classLoader = requireNonNull(classLoader, "classLoader");

        try {
            appendMethod = classLoader.getClass().getDeclaredMethod("appendToClassPathForInstrumentation", String.class);
            appendMethod.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Couldn't initialize SystemClassLoaderHelper", e);
        }
    }

    /**
     * Adds a URL to the classpath.
     *
     * @param url the URL to add
     */
    public void addToClasspath(@NotNull URL url) {
        try {
            appendMethod.invoke(classLoader, url.toURI().getPath());
        } catch (Exception e) {
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

