# AGENTS.md

Attache downloads, verifies, relocates and loads Maven dependencies at runtime for Minecraft server plugins and
standalone Java applications, and ships a Gradle plugin that generates the descriptors it reads.

## Modules

- `attache-api`: public API: library and repository models, descriptors, resolution contracts.
- `attache-common`: shared runtime: library loading, class loaders, relocation, descriptor reading, logging.
- `attache-resolver`: the isolated Maven resolver, shaded with Maven Resolver into one jar (`bundle`).
- `attache-launcher`: downloads and starts the resolver and initializes Attache.
- `attache-platform/platform-*`: `bukkit`, `paper`, `bungeecord`, `velocity`, `standalone` and `spring`; each is
  published as `me.whereareiam:attache-<platform>`.
- `attache-plugin/gradle`: the Gradle plugin `me.whereareiam.attache`, published as
  `me.whereareiam:attache-gradle-plugin` with its plugin marker.
- `build-logic/`: conventions: `library`, `bundle`, `platform`, `gradle-plugin`, `publication`, `toolchain`
  (Java 17 bytecode), `testing`.

The version comes from the `VERSION` environment variable and is `dev` without it. Every module is published to
`https://maven.whereareiam.me/<realm>` by `./gradlew publish`, including the Gradle plugin; there is no Gradle Plugin
Portal publication.

## Build and test

- `./gradlew build`: compiles, tests and packages every module; this is what pull request checks run.
- `./gradlew :attache-resolver:test` and similar for one module.
- Gradle runs on Java 25; the Gradle plugin compiles with a Java 17 toolchain.

## Pull requests and releases

Build each change in its own `feature/...` branch and merge it into `dev` through a pull request.
Publishing a release draft tags `dev`, publishes the Maven artifacts and the Gradle plugin, and attaches the jars.
A development build is started by hand on any branch and publishes `<branch>-<short commit>`.

### Release notes

A merged pull request lands in the release draft as `* <title> ([#<number>](<url>))`, under the
category of its label. Readers are plugin and application developers.

- Titles use `Area: Description`, with a past-tense description of what changed for a user:
  `Introduced`, `Changed`, `Fixed`, `Removed`.
- Areas: `Resolver`, `Loading`, `Relocation`, `Repositories`, `Descriptors`, `Gradle`, `Bukkit`, `Paper`,
  `BungeeCord`, `Velocity`, `Standalone`, `Spring`. Add an area only for a new platform, plugin or capability users
  recognise by that name, and add it to this list in the same pull request.

| Label | Use for |
| --- | --- |
| `feature` | something new for users |
| `change` | changed behavior or API |
| `bug` | a fix for a bug that shipped in a release |
| `dependencies` | a library update |
| `skip-changelog` | refactors, build, CI, tests and documentation, and fixes of bugs that never shipped |

Add `major` next to the category when users must change their code or build on upgrade.

Text written by hand, the subtitle, the summary and upgrade notes, lives in `.github/release-notes.md`;
it replaces the draft's placeholders while its `version` matches the drafted version. The release
workflow stops while a placeholder is left.
