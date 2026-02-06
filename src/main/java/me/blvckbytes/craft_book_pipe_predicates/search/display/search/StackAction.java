package me.blvckbytes.craft_book_pipe_predicates.search.display.search;

import java.util.Arrays;
import java.util.List;

public enum StackAction {
  TELEPORT_TO_CONTAINER,
  MOVE_TO_INVENTORY,
  OPEN_CONTAINER,
  ;

  public static final List<StackAction> values = Arrays.asList(values());

  public StackAction nextAction() {
    return values.get((ordinal() + 1) % values.size());
  }

  public static StackAction first() {
    return values.get(0);
  }
}
