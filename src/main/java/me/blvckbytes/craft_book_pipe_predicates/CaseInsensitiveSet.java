package me.blvckbytes.craft_book_pipe_predicates;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CaseInsensitiveSet implements Set<String> {

  private final List<String> items;
  private final Set<String> seenItemsLower;

  public CaseInsensitiveSet() {
    this.items = new ArrayList<>();
    this.seenItemsLower = new HashSet<>();
  }

  @Override
  public int size() {
    return items.size();
  }

  @Override
  public boolean isEmpty() {
    return items.isEmpty();
  }

  @Override
  public boolean contains(Object item) {
    if (!(item instanceof String string))
      return false;

    return seenItemsLower.contains(string.toLowerCase());
  }

  @Override
  public @NotNull Iterator<String> iterator() {
    return items.iterator();
  }

  @Override
  public @NotNull Object @NotNull [] toArray() {
    return items.toArray();
  }

  @Override
  public @NotNull <T> T @NotNull [] toArray(@NotNull T @NotNull [] array) {
    return items.toArray(array);
  }

  @Override
  public boolean add(String item) {
    if (!(seenItemsLower.add(item.toLowerCase())))
      return false;

    items.add(item);
    return true;
  }

  @Override
  public boolean remove(Object item) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends String> collection) {
    var addedItem = false;

    for (String item : collection)
      addedItem |= add(item);

    return addedItem;
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    items.clear();
    seenItemsLower.clear();
  }
}
