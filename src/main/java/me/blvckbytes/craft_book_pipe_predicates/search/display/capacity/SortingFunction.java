package me.blvckbytes.craft_book_pipe_predicates.search.display.capacity;

@FunctionalInterface
public interface SortingFunction {

  int compare(CapacityDisplayRenderable a, CapacityDisplayRenderable b, boolean descending);

}
