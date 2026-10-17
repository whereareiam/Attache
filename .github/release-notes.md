---
version: 1.1.1
---

# Attache 1.1.1 - Exclusions

An excluded transitive dependency now takes its own dependencies with it, as a Maven exclusion does, so excluding an artifact your application ships in another version no longer downloads the old version's dependencies next to yours. A dependency that another path also needs is still downloaded.
