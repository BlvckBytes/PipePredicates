package me.blvckbytes.craft_book_pipe_predicates.search.display.capacity;

import me.blvckbytes.craft_book_pipe_predicates.search.StorageCapacity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CapacityDisplayData {

  public final List<StorageCapacity> capacities;
  public final @Nullable String predicateString;
  public final @Nullable StorageCapacity selectedCapacity;
  public final @Nullable CapacityDisplay previousDisplay;

  public CapacityDisplayData(List<StorageCapacity> capacities, @Nullable String predicateString) {
    this.capacities = capacities;
    this.predicateString = predicateString;
    this.selectedCapacity = null;
    this.previousDisplay = null;
  }

  public CapacityDisplayData(CapacityDisplayData data, @NotNull StorageCapacity selectedCapacity, @NotNull CapacityDisplay previousDisplay) {
    this.capacities = data.capacities;
    this.predicateString = data.predicateString;
    this.selectedCapacity = selectedCapacity;
    this.previousDisplay = previousDisplay;
  }
}
