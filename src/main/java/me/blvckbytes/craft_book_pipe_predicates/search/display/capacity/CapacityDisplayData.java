package me.blvckbytes.craft_book_pipe_predicates.search.display.capacity;

import me.blvckbytes.craft_book_pipe_predicates.search.StorageCapacity;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CapacityDisplayData {

  public final List<StorageCapacity> capacities;
  public final @Nullable ItemPredicate containedPredicate;
  public final @Nullable StorageCapacity selectedCapacity;
  public final @Nullable CapacityDisplay previousDisplay;

  public CapacityDisplayData(List<StorageCapacity> capacities, @Nullable ItemPredicate containedPredicate) {
    this.capacities = capacities;
    this.containedPredicate = containedPredicate;
    this.selectedCapacity = null;
    this.previousDisplay = null;
  }

  public CapacityDisplayData(CapacityDisplayData data, @NotNull StorageCapacity selectedCapacity, @NotNull CapacityDisplay previousDisplay) {
    this.capacities = data.capacities;
    this.containedPredicate = data.containedPredicate;
    this.selectedCapacity = selectedCapacity;
    this.previousDisplay = previousDisplay;
  }
}
