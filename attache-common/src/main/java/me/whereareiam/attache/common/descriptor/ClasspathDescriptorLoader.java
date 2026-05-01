package me.whereareiam.attache.common.descriptor;

import me.whereareiam.attache.descriptor.AttacheDescriptorCodec;
import me.whereareiam.attache.descriptor.AttacheDescriptorFragment;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Discovers Attache descriptor fragments on the classpath.
 */
public final class ClasspathDescriptorLoader {
	private static final String ROOT = "META-INF/attache";
	private static final String FILE_NAME = "attache.xml";

	@NotNull
	public List<LoadedDescriptorFragment> load(@NotNull ClassLoader classLoader, @NotNull Class<?> anchorClass) {
		LinkedHashMap<String, LoadedDescriptorFragment> descriptors = new LinkedHashMap<>();

		scanResourceRoots(classLoader, descriptors);
		scanClassLoaderUrls(classLoader, descriptors);
		scanCodeSource(anchorClass, descriptors);

		return Collections.unmodifiableList(new ArrayList<>(descriptors.values()));
	}

	private void scanResourceRoots(
			@NotNull ClassLoader classLoader,
			@NotNull LinkedHashMap<String, LoadedDescriptorFragment> descriptors
	) {
		scanResourceRoot(classLoader, ROOT, descriptors);
		scanResourceRoot(classLoader, ROOT + '/', descriptors);
	}

	private void scanResourceRoot(
			@NotNull ClassLoader classLoader,
			@NotNull String resourceName,
			@NotNull LinkedHashMap<String, LoadedDescriptorFragment> descriptors
	) {
		try {
			Enumeration<URL> resources = classLoader.getResources(resourceName);
			while (resources.hasMoreElements()) {
				URL resource = resources.nextElement();
				scanResourceUrl(resource, descriptors);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to scan Attache descriptor resources", e);
		}
	}

	private void scanClassLoaderUrls(
			@NotNull ClassLoader classLoader,
			@NotNull LinkedHashMap<String, LoadedDescriptorFragment> descriptors
	) {
		Queue<ClassLoader> queue = new ArrayDeque<>();
		queue.add(classLoader);

		ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
		if (contextLoader != null && contextLoader != classLoader) {
			queue.add(contextLoader);
		}

		List<String> visited = new ArrayList<>();
		while (!queue.isEmpty()) {
			ClassLoader current = queue.remove();

            String id = current.getClass().getName() + '@' + System.identityHashCode(current);
			if (visited.contains(id)) {
				continue;
			}
			visited.add(id);

			URL[] urls = extractUrls(current);
			for (URL url : urls) {
				scanClasspathUrl(url, descriptors);
			}

			ClassLoader parent = current.getParent();
			if (parent != null) {
				queue.add(parent);
			}
		}
	}

	private void scanCodeSource(
			@NotNull Class<?> anchorClass,
			@NotNull LinkedHashMap<String, LoadedDescriptorFragment> descriptors
	) {
		CodeSource codeSource = anchorClass.getProtectionDomain().getCodeSource();
		if (codeSource == null || codeSource.getLocation() == null) {
			return;
		}

		scanClasspathUrl(codeSource.getLocation(), descriptors);
	}

	private void scanResourceUrl(
			@NotNull URL resource,
			@NotNull LinkedHashMap<String, LoadedDescriptorFragment> descriptors
	) {
		String protocol = resource.getProtocol();
		if ("file".equals(protocol)) {
			try {
				scanDirectory(Path.of(resource.toURI()), descriptors);
			} catch (IOException | URISyntaxException e) {
				throw new IllegalStateException("Failed to scan descriptor directory " + resource, e);
			}
			return;
		}

		if ("jar".equals(protocol)) {
			try {
				JarURLConnection connection = (JarURLConnection) resource.openConnection();
				try (JarFile jarFile = connection.getJarFile()) {
					String prefix = normalizePrefix(connection.getEntryName());
					scanJar(jarFile, prefix, jarFile.getName(), descriptors);
				}
			} catch (IOException e) {
				throw new IllegalStateException("Failed to scan descriptor jar " + resource, e);
			}
		}
	}

	private void scanClasspathUrl(
			@NotNull URL url,
			@NotNull LinkedHashMap<String, LoadedDescriptorFragment> descriptors
	) {
		String protocol = url.getProtocol();
		if ("file".equals(protocol)) {
			try {
				Path path = Path.of(url.toURI());
				if (Files.isDirectory(path)) {
					scanDirectory(path.resolve(ROOT), descriptors);
					return;
				}

				String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
				if (fileName.endsWith(".jar") || fileName.endsWith(".zip")) {
					try (JarFile jarFile = new JarFile(path.toFile())) {
						scanJar(jarFile, ROOT + '/', jarFile.getName(), descriptors);
					}
				}
			} catch (IOException | URISyntaxException e) {
				throw new IllegalStateException("Failed to inspect classpath entry " + url, e);
			}
			return;
		}

		if ("jar".equals(protocol)) {
			scanResourceUrl(url, descriptors);
		}
	}

	private void scanDirectory(
			@NotNull Path descriptorRoot,
			@NotNull LinkedHashMap<String, LoadedDescriptorFragment> descriptors
	) throws IOException {
		if (!Files.isDirectory(descriptorRoot)) {
			return;
		}

		try (var paths = Files.walk(descriptorRoot)) {
			paths.filter(Files::isRegularFile)
					.filter(path -> FILE_NAME.equals(path.getFileName().toString()))
					.forEach(path -> readDescriptor(path.toAbsolutePath().toString(), () -> Files.newInputStream(path), descriptors));
		}
	}

	private void scanJar(
			@NotNull JarFile jarFile,
			@NotNull String prefix,
			@NotNull String jarName,
			@NotNull LinkedHashMap<String, LoadedDescriptorFragment> descriptors
	) throws IOException {
		Enumeration<JarEntry> entries = jarFile.entries();
		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			if (entry.isDirectory()) {
				continue;
			}

			String entryName = entry.getName();
			if (!entryName.startsWith(prefix) || !entryName.endsWith('/' + FILE_NAME) && !entryName.endsWith(FILE_NAME)) {
				continue;
			}

			String location = jarName + "!/" + entryName;
			readDescriptor(location, () -> jarFile.getInputStream(entry), descriptors);
		}
	}

	private void readDescriptor(
			@NotNull String location,
			@NotNull InputStreamSupplier inputStreamSupplier,
			@NotNull LinkedHashMap<String, LoadedDescriptorFragment> descriptors
	) {
		if (descriptors.containsKey(location)) {
			return;
		}

		try (InputStream inputStream = inputStreamSupplier.open()) {
			AttacheDescriptorFragment fragment = AttacheDescriptorCodec.decode(inputStream);
			if (fragment == null) {
				throw new IllegalStateException("Descriptor " + location + " is empty");
			}
			descriptors.put(location, new LoadedDescriptorFragment(location, fragment));
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read descriptor " + location, e);
		}
	}

	@NotNull
	private URL[] extractUrls(@NotNull ClassLoader classLoader) {
		if (classLoader instanceof URLClassLoader urlClassLoader) {
			return urlClassLoader.getURLs();
		}

		try {
			var method = classLoader.getClass().getMethod("getURLs");
			Object result = method.invoke(classLoader);
			if (result instanceof URL[] urls) {
				return urls;
			}
		} catch (ReflectiveOperationException ignored) {
		}

		return new URL[0];
	}

	@NotNull
	private String normalizePrefix(String prefix) {
		if (prefix == null || prefix.isBlank()) {
			return ROOT + '/';
		}
		return prefix.endsWith("/") ? prefix : prefix + '/';
	}

	@FunctionalInterface
	private interface InputStreamSupplier {
		@NotNull
		InputStream open() throws IOException;
	}

	public record LoadedDescriptorFragment(@NotNull String location, @NotNull AttacheDescriptorFragment fragment) {
		public LoadedDescriptorFragment {
			Objects.requireNonNull(location, "location");
			Objects.requireNonNull(fragment, "fragment");
		}
	}
}
