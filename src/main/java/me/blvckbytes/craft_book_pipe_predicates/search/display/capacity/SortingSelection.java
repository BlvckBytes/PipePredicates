package me.blvckbytes.craft_book_pipe_predicates.search.display.capacity;

import java.util.Arrays;
import java.util.List;

public enum SortingSelection {
  ASCENDING,
  DESCENDING,
  INACTIVE
  ;

  public static final List<SortingSelection> values = Arrays.stream(values()).toList();

  public SortingSelection next() {
    return values.get((ordinal() + 1) % values.size());
  }

  public static SortingSelection byOrdinalOrFirst(int ordinal) {
    if (ordinal < 0 || ordinal >= values.size())
      return values.get(0);

    return values.get(ordinal);
  }
}
