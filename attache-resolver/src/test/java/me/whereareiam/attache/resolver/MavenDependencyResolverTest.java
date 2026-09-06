package me.whereareiam.attache.resolver;

import com.sun.net.httpserver.HttpServer;
import me.whereareiam.attache.model.ExcludedDependency;
import me.whereareiam.attache.model.LibraryRequest;
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
					String artifact = path.substring(path.lastIndexOf('/') + 1).replace("-1.pom", "");
					String dependencies = artifact.equals("root") ? "<dependencies>"
							+ dependency("compile", "compile") + dependency("runtime", "runtime")
							+ dependency("excluded", "runtime") + dependency("test", "test")
							+ dependency("provided", "provided") + "</dependencies>" : "";
					bytes = ("<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>"
							+ artifact + "</artifactId><version>1</version>" + dependencies + "</project>").getBytes(StandardCharsets.UTF_8);
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
			return "<dependency><groupId>test</groupId><artifactId>" + artifact + "</artifactId><version>1</version><scope>" + scope + "</scope></dependency>";
		}
		String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/"; }
		int requests(String file) { return requests.entrySet().stream().filter(entry -> entry.getKey().endsWith('/' + file)).mapToInt(entry -> entry.getValue().get()).sum(); }
		int total() { return requests.values().stream().mapToInt(AtomicInteger::get).sum(); }
		@Override public void close() { server.stop(0); }
	}
}
