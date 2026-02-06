package me.blvckbytes.craft_book_pipe_predicates.search.display.search;

import java.util.Arrays;
import java.util.List;

public enum CollectionAction {
  GET_ONE_STACK,
  GET_FOUR_STACKS,
  FILL_INVENTORY,
  SHOW_STACKS,
  ;

  public static final List<CollectionAction> values = Arrays.asList(values());

  public CollectionAction nextAction() {
    return values.get((ordinal() + 1) % values.size());
  }

  public static CollectionAction first() {
    return values.get(0);
  }
}
