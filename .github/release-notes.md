---
version: 1.3.0
---

# Attache 1.3.0 - Own descriptors

`LibraryManager#setDescriptorSources` names the jars or directories a library manager reads descriptors from. A part of an application with a class loader of its own, such as an add-on loaded in a child class loader, gives its manager its own jar, so it loads only the libraries it declares instead of reading the application's descriptors and loading those libraries again. By default a manager reads descriptors as before.
