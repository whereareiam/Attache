package me.whereareiam.attache;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Class containing URLs of public Maven repositories.
 */
public class Repositories {
	/**
	 * Maven Central repository URL.
	 */
	public static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

	/**
	 * Sonatype OSS repository URL.
	 */
	public static final String SONATYPE = "https://oss.sonatype.org/content/groups/public/";

	/**
	 * JitPack repository URL.
	 */
	public static final String JITPACK = "https://jitpack.io/";

	/**
	 * Resolves the current Maven local repository URL.
	 * <p>
	 * This method honors the {@code maven.repo.local} system property first and
	 * otherwise falls back to {@code ~/.m2/repository}.
	 *
	 * @return the Maven local repository URL
	 */
	@NotNull
	public static String mavenLocal() {
		String configuredPath = System.getProperty("maven.repo.local");
		Path repositoryPath = configuredPath == null || configuredPath.isBlank()
				? Path.of(System.getProperty("user.home"), ".m2", "repository")
				: Path.of(configuredPath);

		return mavenLocal(repositoryPath);
	}

	/**
	 * Converts the given local repository directory to a Maven repository URL.
	 *
	 * @param repositoryPath the repository directory
	 * @return the repository URL
	 */
	@NotNull
	public static String mavenLocal(@NotNull Path repositoryPath) {
		String repository = repositoryPath.toAbsolutePath().normalize().toUri().toString();
		return repository.endsWith("/") ? repository : repository + '/';
	}
}
