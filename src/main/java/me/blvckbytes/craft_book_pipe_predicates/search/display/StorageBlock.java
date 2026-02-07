package me.blvckbytes.craft_book_pipe_predicates.search.display;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.SearchedInventory;
import me.blvckbytes.craft_book_pipe_predicates.search.display.capacity.CapacityDisplayRenderable;
import me.blvckbytes.craft_book_pipe_predicates.search.display.capacity.UsageLevel;
import org.bukkit.inventory.ItemStack;

public class StorageBlock implements CapacityDisplayRenderable {

  public final SearchedInventory searchedInventory;
  public final int occupiedSlotCount;
  public final int inventorySize;

  private final double usagePercentage;
  private final UsageLevel usageLevel;

  public StorageBlock(SearchedInventory searchedInventory, int occupiedSlotCount, int inventorySize) {
    this.searchedInventory = searchedInventory;
    this.occupiedSlotCount = occupiedSlotCount;
    this.inventorySize = inventorySize;

    this.usagePercentage = (occupiedSlotCount / (double) inventorySize) * 100;
    this.usageLevel = UsageLevel.fromUsagePercentage(usagePercentage);
  }

  @Override
  public double getUsagePercentage() {
    return usagePercentage;
  }

  @Override
  public UsageLevel getUsageLevel() {
    return usageLevel;
  }

  @Override
  public ItemStack render(ConfigKeeper<MainSection> config, InterpretationEnvironment environment) {
    return config.rootSection.capacityDisplay.items.containerRepresentative.build(
      environment.copy()
        .withVariable("labels", searchedInventory.getLabelValues())
        .withVariable("container_type", searchedInventory.material.name())
        .withVariable("container_x", searchedInventory.block.getX())
        .withVariable("container_y", searchedInventory.block.getY())
        .withVariable("container_z", searchedInventory.block.getZ())
        .withVariable("occupied_slot_count", occupiedSlotCount)
        .withVariable("total_slot_count", inventorySize)
        .withVariable("usage_percentage", usagePercentage)
        .withVariable("usage_level", usageLevel.name())
    );
  }

  @Override
  public int getTotalCapacity() {
    return inventorySize;
  }
}
