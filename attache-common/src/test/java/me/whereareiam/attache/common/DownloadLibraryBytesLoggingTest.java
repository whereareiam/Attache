package me.whereareiam.attache.common;

import com.sun.net.httpserver.HttpServer;
import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadLibraryBytesLoggingTest {
	@TempDir
	Path tempDir;

	private HttpServer server;

	@AfterEach
	void tearDown() {
		if (server != null)
			server.stop(0);
	}

	@Test
	void logs404AtInfo() throws Exception {
		RecordingLoggingHelper loggingHelper = new RecordingLoggingHelper();
		ExposedDownloadLibraryManager libraryManager = new ExposedDownloadLibraryManager(tempDir, loggingHelper);

		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/missing", exchange -> {
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
		});
		server.start();

		byte[] bytes = libraryManager.invokeDownloadLibraryBytes(url("/missing"));

		assertNull(bytes);
		assertTrue(loggingHelper.contains(Level.INFO, "File not found (404"));
	}

	@Test
	void logs403AtWarn() throws Exception {
		RecordingLoggingHelper loggingHelper = new RecordingLoggingHelper();
		ExposedDownloadLibraryManager libraryManager = new ExposedDownloadLibraryManager(tempDir, loggingHelper);

		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/forbidden", exchange -> {
			exchange.sendResponseHeaders(403, -1);
			exchange.close();
		});
		server.start();

		byte[] bytes = libraryManager.invokeDownloadLibraryBytes(url("/forbidden"));

		assertNull(bytes);
		assertTrue(loggingHelper.contains(Level.WARN, "Download failed (403"));
	}

	@Test
	void logsTimeoutAtWarn() throws Exception {
		RecordingLoggingHelper loggingHelper = new RecordingLoggingHelper();
		ExposedDownloadLibraryManager libraryManager = new ExposedDownloadLibraryManager(tempDir, loggingHelper);

		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/timeout", exchange -> {
			try {
				Thread.sleep(6000);
				byte[] response = "late".getBytes();
				exchange.sendResponseHeaders(200, response.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(response);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				exchange.close();
			}
		});
		server.start();

		byte[] bytes = libraryManager.invokeDownloadLibraryBytes(url("/timeout"));

		assertNull(bytes);
		assertTrue(loggingHelper.contains(Level.WARN, "Download timed out:"));
	}

	@NotNull
	private String url(@NotNull String path) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + path;
	}

	private static final class ExposedDownloadLibraryManager extends BaseLibraryManager {
		private ExposedDownloadLibraryManager(@NotNull Path tempDir, @NotNull LoggingHelper loggingHelper) {
			super(loggingHelper, tempDir, "lib");
		}

		@Override
		protected void addToClasspath(@NotNull Path file) {
		}

		private byte[] invokeDownloadLibraryBytes(@NotNull String url) {
			return downloadLibraryBytes(url);
		}
	}

	private static final class RecordingLoggingHelper implements LoggingHelper {
		private final List<LogEntry> entries = new CopyOnWriteArrayList<>();

		@Override
		public void log(@NotNull Level level, @NotNull String message) {
			entries.add(new LogEntry(level, message));
		}

		@Override
		public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
			entries.add(new LogEntry(level, message));
		}

		private boolean contains(@NotNull Level level, @NotNull String fragment) {
			return entries.stream().anyMatch(entry -> entry.level == level && entry.message.contains(fragment));
		}

		private record LogEntry(@NotNull Level level, @NotNull String message) {
		}
	}
}
