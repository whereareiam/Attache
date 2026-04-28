package me.whereareiam.attache.common.loader;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.type.Level;
import me.whereareiam.attache.type.LibraryLoadMode;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class LibraryLoaderTestSupport {
	@NotNull
	protected static LibraryRequest library(@NotNull String artifactId) {
		return LibraryRequest.builder()
				.groupId("test.group")
				.artifactId(artifactId)
				.version("1.0.0")
				.url("https://example.test/" + artifactId + "-1.0.0.jar")
				.build();
	}

	protected static void setLoadModeProperty(String value) {
		if (value == null)
			System.clearProperty(LibraryLoadMode.SYSTEM_PROPERTY);
		else
			System.setProperty(LibraryLoadMode.SYSTEM_PROPERTY, value);
	}

	protected static void restoreLoadModeProperty(String previousValue) {
		setLoadModeProperty(previousValue);
	}

	public static final class BlockingTestLibraryManager extends BaseLibraryManager {
		private final AtomicInteger downloadCount = new AtomicInteger();
		private final AtomicInteger activeDownloads = new AtomicInteger();
		private final AtomicInteger maxConcurrentDownloads = new AtomicInteger();
		private final List<String> loadedFiles = new CopyOnWriteArrayList<>();

		private volatile CountDownLatch downloadStarts = new CountDownLatch(0);
		private volatile CountDownLatch releaseDownloads = new CountDownLatch(0);

		public BlockingTestLibraryManager(@NotNull Path tempDir) {
			super(new TestLoggingHelper(), tempDir, "lib");
		}

		public void blockDownloads(int expectedDownloads) {
			downloadStarts = new CountDownLatch(expectedDownloads);
			releaseDownloads = new CountDownLatch(1);
		}

		public boolean awaitDownloadStarts(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
			return downloadStarts.await(timeout, unit);
		}

		public void releaseDownloads() {
			releaseDownloads.countDown();
		}

		public int getDownloadCount() {
			return downloadCount.get();
		}

		public int getMaxConcurrentDownloads() {
			return maxConcurrentDownloads.get();
		}

		@NotNull
		public List<String> getLoadedFiles() {
			return new ArrayList<>(loadedFiles);
		}

		@Override
		protected void addToClasspath(@NotNull Path file) {
			loadedFiles.add(file.getFileName().toString());
		}

		@Override
		protected DownloadAttempt downloadLibraryAttempt(@NotNull String url) {
			downloadCount.incrementAndGet();
			int concurrentDownloads = activeDownloads.incrementAndGet();
			maxConcurrentDownloads.accumulateAndGet(concurrentDownloads, Math::max);
			downloadStarts.countDown();

			try {
				if (releaseDownloads.getCount() > 0 && !releaseDownloads.await(2, TimeUnit.SECONDS))
					throw new RuntimeException("Timed out waiting to release test downloads");

				return DownloadAttempt.success(url.getBytes(StandardCharsets.UTF_8));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Interrupted while simulating download", e);
			} finally {
				activeDownloads.decrementAndGet();
			}
		}
	}

	private static final class TestLoggingHelper implements LoggingHelper {
		@Override
		public void log(@NotNull Level level, @NotNull String message) {
		}

		@Override
		public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
		}
	}
}
