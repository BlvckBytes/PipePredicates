package me.blvckbytes.pipe_predicates.search.display.capacity;

import me.blvckbytes.pipe_predicates.search.StorageCapacity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CapacityDisplayData {

  public final CapacityInfo capacityInfo;
  public final @Nullable StorageCapacity selectedCapacity;
  public final @Nullable CapacityDisplay previousDisplay;

  public CapacityDisplayData(CapacityInfo capacityInfo) {
    this.capacityInfo = capacityInfo;
    this.selectedCapacity = null;
    this.previousDisplay = null;
  }

  public CapacityDisplayData(CapacityDisplayData data, @NotNull StorageCapacity selectedCapacity, @NotNull CapacityDisplay previousDisplay) {
    this.capacityInfo = data.capacityInfo;
    this.selectedCapacity = selectedCapacity;
    this.previousDisplay = previousDisplay;
  }
}
