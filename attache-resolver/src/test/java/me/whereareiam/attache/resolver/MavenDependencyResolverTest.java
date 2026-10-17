package me.whereareiam.attache.resolver;

import com.sun.net.httpserver.HttpServer;
import me.whereareiam.attache.model.ExcludedDependency;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.resolution.model.ResolvedDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MavenDependencyResolverTest {
	@TempDir Path directory;

	@Test
	void resolvesRuntimeJarsWithoutDownloadingTheRootOrExcludedArtifacts() throws Exception {
		try (Repository repository = new Repository(); var resolver = new MavenDependencyResolver(directory)) {
			var request = LibraryRequest.builder().groupId("test").artifactId("root").version("1")
					.excludedTransitiveDependency(new ExcludedDependency("test", "excluded")).build();
			var artifacts = resolver.resolve(request, List.of(repository.url()));
			assertEquals(List.of("compile", "runtime"), artifacts.stream().map(artifact -> artifact.getArtifactId()).sorted().toList());
			for (var artifact : artifacts) assertTrue(Files.isRegularFile(artifact.getFile()));
			assertEquals(0, repository.requests("root-1.jar"));
			assertEquals(0, repository.requests("excluded-1.jar"));
			assertEquals(0, repository.requests("test-1.jar"));
			assertEquals(0, repository.requests("provided-1.jar"));
			assertEquals(1, repository.requests("compile-1.jar"));
			assertEquals(1, repository.requests("runtime-1.jar"));
			int requests = repository.total();
			resolver.resolve(request, List.of(repository.url()));
			assertEquals(requests, repository.total());
		}
	}

	@Test
	void excludesTheDependenciesOfAnExcludedArtifactUnlessAnotherPathReachesThem() throws Exception {
		try (Repository repository = new Repository(); var resolver = new MavenDependencyResolver(directory)) {
			var request = LibraryRequest.builder().groupId("test").artifactId("nested").version("1")
					.excludedTransitiveDependency(new ExcludedDependency("test", "excluded")).build();
			var artifacts = resolver.resolve(request, List.of(repository.url()));
			assertEquals(List.of("other", "shared"), artifacts.stream().map(artifact -> artifact.getArtifactId()).sorted().toList());
			assertEquals(0, repository.requests("excluded-1.pom"));
			assertEquals(0, repository.requests("hidden-1.pom"));
			assertEquals(0, repository.requests("hidden-1.jar"));
		}
	}

	@Test
	void resolvesSeveralLibrariesAsOneGraphWithOneVersionOfEachArtifact() throws Exception {
		try (Repository repository = new Repository(); var resolver = new MavenDependencyResolver(directory)) {
			var near = LibraryRequest.builder().groupId("test").artifactId("near").version("1").resolveTransitiveDependencies(true).build();
			var far = LibraryRequest.builder().groupId("test").artifactId("far").version("1").resolveTransitiveDependencies(true).build();
			var graph = resolver.resolve(List.of(near, far), List.of(repository.url()));

			assertEquals(List.of("far:1", "middle:1", "near:1", "shared:1"), graph.stream()
					.map(dependency -> dependency.getArtifact().getArtifactId() + ':' + dependency.getArtifact().getVersion())
					.sorted().toList());
			assertEquals(List.of(near, far), dependents(graph, "shared"));
			assertEquals(List.of(far), dependents(graph, "middle"));
			assertEquals(List.of(near), dependents(graph, "near"));
			assertEquals(0, repository.requests("shared-2.jar"));
			assertEquals(1, repository.requests("shared-1.jar"));
		}
	}

	@Test
	void givesALibraryTheDependenciesOfTheVersionThatWon() throws Exception {
		try (Repository repository = new Repository(); var resolver = new MavenDependencyResolver(directory)) {
			var pinned = LibraryRequest.builder().groupId("test").artifactId("shared").version("2").resolveTransitiveDependencies(true).build();
			var far = LibraryRequest.builder().groupId("test").artifactId("far").version("1").resolveTransitiveDependencies(true).build();
			var graph = resolver.resolve(List.of(pinned, far), List.of(repository.url()));

			assertEquals(List.of("extra:1", "far:1", "middle:1", "shared:2"), graph.stream()
					.map(dependency -> dependency.getArtifact().getArtifactId() + ':' + dependency.getArtifact().getVersion())
					.sorted().toList());
			assertEquals(List.of(pinned, far), dependents(graph, "shared"));
			assertEquals(List.of(pinned, far), dependents(graph, "extra"));
		}
	}

	@Test
	void leavesTheDependenciesOfANonTransitiveLibraryOut() throws Exception {
		try (Repository repository = new Repository(); var resolver = new MavenDependencyResolver(directory)) {
			var near = LibraryRequest.builder().groupId("test").artifactId("near").version("1").build();
			var graph = resolver.resolve(List.of(near), List.of(repository.url()));

			assertEquals(List.of("near"), graph.stream().map(dependency -> dependency.getArtifact().getArtifactId()).toList());
		}
	}

	private static List<LibraryRequest> dependents(List<ResolvedDependency> graph, String artifactId) {
		return graph.stream()
				.filter(dependency -> dependency.getArtifact().getArtifactId().equals(artifactId))
				.findFirst().orElseThrow()
				.getDependents();
	}

	@Test
	void rejectsResolutionAfterClose() {
		var resolver = new MavenDependencyResolver(directory);
		resolver.close();
		resolver.close();
		assertThrows(IllegalStateException.class, () -> resolver.resolve(LibraryRequest.builder()
				.groupId("test").artifactId("root").version("1").build(), List.of("https://example.invalid/")));
	}

	private static final class Repository implements AutoCloseable {
		private final HttpServer server;
		private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();

		Repository() throws Exception {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			var buffer = new ByteArrayOutputStream();
			try (var ignored = new JarOutputStream(buffer)) { }
			byte[] jar = buffer.toByteArray();
			server.createContext("/", exchange -> {
				String path = exchange.getRequestURI().getPath();
				requests.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
				byte[] bytes = null;
				if (path.endsWith(".pom")) {
					String file = path.substring(path.lastIndexOf('/') + 1);
					String version = path.substring(0, path.lastIndexOf('/'));
					version = version.substring(version.lastIndexOf('/') + 1);
					String artifact = file.substring(0, file.length() - ("-" + version + ".pom").length());
					String dependencies = "<dependencies>" + switch (artifact + ':' + version) {
						case "root:1" -> dependency("compile", "compile") + dependency("runtime", "runtime")
								+ dependency("excluded", "runtime") + dependency("test", "test")
								+ dependency("provided", "provided");
						case "nested:1" -> dependency("excluded", "compile") + dependency("other", "compile");
						case "excluded:1" -> dependency("hidden", "compile") + dependency("shared", "compile");
						case "other:1" -> dependency("shared", "compile");
						case "near:1" -> dependency("shared", "compile");
						case "far:1" -> dependency("middle", "compile");
						case "middle:1" -> dependency("shared", "2", "compile");
						case "shared:2" -> dependency("extra", "compile");
						default -> "";
					} + "</dependencies>";
					bytes = ("<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>"
							+ artifact + "</artifactId><version>" + version + "</version>" + dependencies + "</project>").getBytes(StandardCharsets.UTF_8);
				} else if (path.endsWith(".jar") && !path.endsWith("excluded-1.jar")) bytes = jar;
				if (bytes == null) exchange.sendResponseHeaders(404, -1);
				else if (exchange.getRequestMethod().equals("HEAD")) exchange.sendResponseHeaders(200, -1);
				else {
					exchange.sendResponseHeaders(200, bytes.length);
					exchange.getResponseBody().write(bytes);
				}
				exchange.close();
			});
			server.start();
		}

		private String dependency(String artifact, String scope) {
			return dependency(artifact, "1", scope);
		}

		private String dependency(String artifact, String version, String scope) {
			return "<dependency><groupId>test</groupId><artifactId>" + artifact + "</artifactId><version>" + version + "</version><scope>" + scope + "</scope></dependency>";
		}
		String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/"; }
		int requests(String file) { return requests.entrySet().stream().filter(entry -> entry.getKey().endsWith('/' + file)).mapToInt(entry -> entry.getValue().get()).sum(); }
		int total() { return requests.values().stream().mapToInt(AtomicInteger::get).sum(); }
		@Override public void close() { server.stop(0); }
	}
}
