---
version: 1.1.0
---

# Attache 1.1.0 - Dependency groups

Libraries can now be declared in named groups, so an application downloads only the ones its configuration needs, such as the driver of the database it connects to. Declare a group with `group("name") { library(...) }` in the Gradle plugin and load it at runtime with `loadGroups("name")`.
