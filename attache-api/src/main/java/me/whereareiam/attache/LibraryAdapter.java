package me.whereareiam.attache;

import me.whereareiam.attache.model.LibraryRequest;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface LibraryAdapter<T> {
	@NotNull
	LibraryRequest adapt(@NotNull T library);
}
