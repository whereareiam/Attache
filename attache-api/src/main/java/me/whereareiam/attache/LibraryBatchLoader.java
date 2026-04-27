package me.whereareiam.attache;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface LibraryBatchLoader {
	<T> void loadLibraries(@NotNull Collection<? extends T> libraries);
}
