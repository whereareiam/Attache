package me.whereareiam.attache.common.classloader;

import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * A reflection-based wrapper around {@link URLClassLoader} for adding URLs to
 * the classpath.
 */
public class URLClassLoaderHelper {
	private static final Unsafe theUnsafe;

	static {
		Unsafe unsafe = null;
		// Find Unsafe instance without hardcoding field name
		for (Field f : Unsafe.class.getDeclaredFields()) {
			try {
				if (f.getType() == Unsafe.class && Modifier.isStatic(f.getModifiers())) {
					f.setAccessible(true);
					unsafe = (Unsafe) f.get(null);
					break;
				}
			} catch (Exception ignored) {
			}
		}
		theUnsafe = unsafe;
	}

	private final MethodHandle addURLMethodHandle;

	/**
	 * Creates a new URL class loader helper.
	 *
	 * @param classLoader the class loader to manage
	 */
	public URLClassLoaderHelper(@NotNull URLClassLoader classLoader) {
		requireNonNull(classLoader, "classLoader");

		try {
			Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);

			// Try Libby's privileged MethodHandle approach
			MethodHandle handle = null;
			if (theUnsafe != null) {
				try {
					handle = getPrivilegedMethodHandle(addURLMethod);
				} catch (Exception ignored) {
					// Privileged MethodHandle failed, will fall back to regular approach
				}
			}

			// Fallback to regular unreflect
			if (handle == null) {
				addURLMethod.setAccessible(true);
				handle = MethodHandles.lookup().unreflect(addURLMethod);
			}

			this.addURLMethodHandle = handle.bindTo(classLoader);
		} catch (Exception e) {
			throw new RuntimeException("Couldn't initialize URLClassLoaderHelper", e);
		}
	}

	/**
	 * Gets a privileged MethodHandle for the given method using Unsafe.
	 * This bypasses Java 9+ module restrictions.
	 *
	 * @param method the method to get a handle for
	 * @return a MethodHandle for the method
	 * @throws RuntimeException if unable to get the privileged handle
	 */
	private MethodHandle getPrivilegedMethodHandle(Method method) {
		for (Field trustedLookup : MethodHandles.Lookup.class.getDeclaredFields()) {
			if (trustedLookup.getType() != MethodHandles.Lookup.class ||
					!Modifier.isStatic(trustedLookup.getModifiers()) ||
					trustedLookup.isSynthetic()) {
				continue;
			}

			try {
				MethodHandles.Lookup lookup = (MethodHandles.Lookup) theUnsafe.getObject(
						theUnsafe.staticFieldBase(trustedLookup),
						theUnsafe.staticFieldOffset(trustedLookup)
				);
				return lookup.unreflect(method);
			} catch (Exception ignored) {
				// Try next field
			}
		}

		throw new RuntimeException("Cannot get privileged method handle - no suitable Lookup field found");
	}

	/**
	 * Adds a URL to the classpath.
	 *
	 * @param url the URL to add
	 */
	public void addToClasspath(@NotNull URL url) {
		try {
			addURLMethodHandle.invokeWithArguments(requireNonNull(url, "url"));
		} catch (Throwable e) {
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

