package me.blvckbytes.craft_book_pipe_predicates.search.display.capacity;

import java.util.Arrays;
import java.util.List;

public enum SortingCriteria implements SortingFunction {
  TOTAL_CAPACITY((a, b, d) -> Integer.compare(a.getTotalCapacity(), b.getTotalCapacity()) * (d ? -1 : 1)),
  USAGE_LEVEL((a, b, d) -> a.getUsageLevel().compareTo(b.getUsageLevel()) * (d ? -1 : 1)),
  USAGE_PERCENTAGE((a, b, d) -> Double.compare(a.getUsagePercentage(), b.getUsagePercentage()) * (d ? -1 : 1)),
  ;

  public static final List<SortingCriteria> values = Arrays.asList(values());

  private final SortingFunction sortingFunction;

  SortingCriteria(SortingFunction sortingFunction) {
    this.sortingFunction = sortingFunction;
  }

  @Override
  public int compare(CapacityDisplayRenderable a, CapacityDisplayRenderable b, boolean descending) {
    return sortingFunction.compare(a, b, descending);
  }

  public static SortingCriteria byOrdinalOrFirst(int ordinal) {
    if (ordinal < 0 || ordinal >= values.size())
      return values.get(0);

    return values.get(ordinal);
  }
}
