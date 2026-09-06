package me.whereareiam.attache.launcher;

import com.sun.net.httpserver.HttpServer;
import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.model.ExcludedDependency;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryIdentityTest {
	@TempDir Path temporary;

	@Test
	void reusesSharedDependenciesAcrossIndependentRoots() throws Exception {
		try (Repository repository = new Repository("shared"); Manager manager = manager(repository)) {
			manager.loadLibrary(library("first"));
			int before = repository.requests("shared-1.0.pom");
			assertTrue(before > 0);
			manager.loadLibrary(library("second"));
			assertEquals(before, repository.requests("shared-1.0.pom"), "The same repository must retain its cache identity");
		}
	}

	@Test
	void reusesCachedReleasesAfterRestartWithReversedRootOrder() throws Exception {
		try (Repository repository = new Repository("shared")) {
			try (Manager first = manager(repository)) {
				first.loadLibrary(library("first"));
				first.loadLibrary(library("second"));
			}
			repository.requests.clear();
			try (Manager restarted = manager(repository)) {
				restarted.loadLibrary(library("second"));
				restarted.loadLibrary(library("first"));
			}
			assertEquals(0, repository.requests.values().stream().mapToInt(AtomicInteger::get).sum(),
					"Cached releases must not be revalidated because loading order changed");
		}
	}

	@Test
	void validatesCachedArtifactsAgainstANewRepository() throws Exception {
		try (Repository first = new Repository("shared"); Repository second = new Repository("shared")) {
			try (Manager manager = manager(first)) { manager.loadLibrary(library("first")); }
			try (Manager manager = manager(second)) { manager.loadLibrary(library("first")); }
			assertTrue(second.requests("first-1.0.pom") > 0, "A different repository must validate its own POM");
		}
	}

	@Test
	void loadsTimestampedSnapshotWithClassifierFromResolvedLocalFile() throws Exception {
		try (Repository repository = new Repository("shared", true); Manager manager = manager(repository)) {
			manager.loadLibrary(library("first"));
			assertEquals(1, repository.requests("shared-1.0-20260906.120000-1-tests.jar"));
			assertEquals(0, repository.requests("shared-1.0-SNAPSHOT-tests.jar"));
		}
	}

	@Test
	void appliesNormalizedExclusionsBeforeDownloadingArtifacts() throws Exception {
		try (Repository repository = new Repository("shared"); Manager manager = manager(repository)) {
			manager.loadLibrary(library("first").toBuilder()
					.excludedTransitiveDependency(new ExcludedDependency("test{}cache", "shared")).build());
			assertEquals(0, repository.requests("shared-1.0.jar"));
		}
	}

	@Test
	void bootsThroughTheRealDownloadPathWithoutTestOverrides() throws Exception {
		try (Repository repository = new Repository("shared")) {
			String loaded = ShadedResolverProbe.run(temporary, repository.url());
			assertTrue(loaded.contains("shared-1.0.jar"));
			assertTrue(loaded.contains("first-1.0.jar"));
		}
	}

	@Test
	void supportsRelocatedAttacheWithoutLeakingMavenToTheConsumer() throws Exception {
		Path fixture = Path.of(System.getProperty("attache.test.shadedJar"));
		try (Repository repository = new Repository("shared");
		     URLClassLoader consumer = new URLClassLoader(new URL[]{fixture.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
			Class<?> probe = consumer.loadClass("test.shaded.attache.launcher.ShadedResolverProbe");
			String loaded = (String) probe.getMethod("run", Path.class, String.class).invoke(null, temporary, repository.url());
			assertTrue(loaded.contains("shared-1.0.jar"));
			assertTrue(loaded.contains("first-1.0.jar"));
			assertThrows(ClassNotFoundException.class,
					() -> consumer.loadClass("org.eclipse.aether.RepositorySystem"));
		}
	}

	private Manager manager(Repository repository) {
		Manager manager = new Manager(temporary);
		manager.addRepository(repository.url());
		return manager;
	}

	private LibraryRequest library(String artifact) {
		return LibraryRequest.builder().groupId("test.cache").artifactId(artifact).version("1.0")
				.skipIfPresent(false).resolveTransitiveDependencies(true).build();
	}

	private static final class Manager extends BaseLibraryManager {
		Manager(Path directory) {
			super(new LoggingHelper() {
				@Override public void log(@NotNull Level level, @NotNull String message) { }
				@Override public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) { }
			}, directory, "libraries", new AttacheLauncher());
		}

		@Override protected void addToClasspath(@NotNull Path file) { }

	}

	private static final class Repository implements AutoCloseable {
		private final HttpServer server;
		private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();

		Repository(String dependency) throws Exception {
			this(dependency, false);
		}

		Repository(String dependency, boolean snapshot) throws Exception {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (JarOutputStream ignored = new JarOutputStream(bytes)) { }
			byte[] jar = bytes.toByteArray();
			server.createContext("/", exchange -> {
				String resource = exchange.getRequestURI().getPath();
				requests.computeIfAbsent(resource, ignored -> new AtomicInteger()).incrementAndGet();
				byte[] body = null;
				if (snapshot && resource.endsWith("maven-metadata.xml")) {
					body = ("<metadata><groupId>test.cache</groupId><artifactId>shared</artifactId>"
							+ "<version>1.0-SNAPSHOT</version><versioning><snapshot><timestamp>20260906.120000</timestamp>"
							+ "<buildNumber>1</buildNumber></snapshot><lastUpdated>20260906120000</lastUpdated>"
							+ "<snapshotVersions><snapshotVersion><extension>pom</extension><value>1.0-20260906.120000-1</value>"
							+ "<updated>20260906120000</updated></snapshotVersion><snapshotVersion><extension>jar</extension>"
							+ "<classifier>tests</classifier><value>1.0-20260906.120000-1</value><updated>20260906120000</updated>"
							+ "</snapshotVersion></snapshotVersions></versioning></metadata>").getBytes(StandardCharsets.UTF_8);
				} else if (resource.endsWith(".pom")) {
					String artifact = resource.substring(resource.lastIndexOf('/') + 1).replace("-1.0.pom", "").replace("-1.0-20260906.120000-1.pom", "");
					String dependencies = artifact.equals("first") || artifact.equals("second")
							? "<dependencies><dependency><groupId>test.cache</groupId><artifactId>" + dependency
							+ "</artifactId><version>" + (snapshot ? "1.0-SNAPSHOT</version><classifier>tests</classifier>" : "1.0</version>")
							+ "</dependency></dependencies>" : "";
					body = ("<project><modelVersion>4.0.0</modelVersion><groupId>test.cache</groupId><artifactId>"
							+ artifact + "</artifactId><version>" + (snapshot && artifact.equals("shared") ? "1.0-SNAPSHOT" : "1.0") + "</version>" + dependencies + "</project>").getBytes(StandardCharsets.UTF_8);
				} else if (resource.endsWith(".jar")) body = jar;
				if (body == null) exchange.sendResponseHeaders(404, -1);
				else if (exchange.getRequestMethod().equals("HEAD")) exchange.sendResponseHeaders(200, -1);
				else {
					exchange.sendResponseHeaders(200, body.length);
					exchange.getResponseBody().write(body);
				}
				exchange.close();
			});
			server.start();
		}

		String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/"; }
		int requests(String file) {
			return requests.entrySet().stream().filter(entry -> entry.getKey().endsWith('/' + file))
					.mapToInt(entry -> entry.getValue().get()).sum();
		}
		@Override public void close() { server.stop(0); }
	}
}
