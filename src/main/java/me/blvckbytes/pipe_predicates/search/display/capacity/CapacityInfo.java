package me.blvckbytes.pipe_predicates.search.display.capacity;

import me.blvckbytes.pipe_predicates.search.PredicateAndLabels;
import me.blvckbytes.pipe_predicates.search.StorageCapacity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public record CapacityInfo(
  @Nullable PredicateAndLabels containedPredicate,
  List<StorageCapacity> capacities,
  Collection<String> encounteredLabelValues
) {}
