package me.whereareiam.attache;

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

	private Repositories() {
		throw new UnsupportedOperationException("Private constructor");
	}
}

