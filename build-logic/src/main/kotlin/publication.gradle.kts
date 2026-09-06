plugins {
    `maven-publish`
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(provider { artifactId })
            description.set(provider { project.description })
        }
    }

    repositories {
        maven {
            val realm = providers.environmentVariable("PUBLISH_REALM")
                .orElse(provider { if (version.toString().contains("dev", ignoreCase = true)) "development" else "release" })
                .get().lowercase()

            url = uri("https://maven.whereareiam.me/$realm")
            credentials {
                username = providers.environmentVariable("PUBLISH_USER").orNull.orEmpty()
                password = providers.environmentVariable("PUBLISH_TOKEN").orNull.orEmpty()
            }
        }
    }
}
