---
version: 1.2.0
---

# Attache 1.2.0 - One graph

`loadDescriptors()` and `loadGroups()` now resolve the transitive dependencies of the declared libraries as one dependency graph, so every artifact loads in one version instead of one per library that needs it; the version nearest to a declared library wins, as in Maven. A dependency still loads with the relocations of each library that needs it.

A relocation declared directly in the `attache {}` block now applies to every library the application's descriptors declare, including those of other projects, and to their dependencies, for a package the application shades and relocates itself.

## Upgrading

* Two libraries that resolve transitive dependencies may no longer declare different versions of one artifact in the same descriptors; loading fails and names both.
