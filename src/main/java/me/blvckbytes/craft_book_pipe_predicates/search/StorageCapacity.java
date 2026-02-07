package me.blvckbytes.craft_book_pipe_predicates.search;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.display.StorageBlock;
import me.blvckbytes.craft_book_pipe_predicates.search.display.capacity.CapacityDisplayRenderable;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class StorageCapacity implements CapacityDisplayRenderable {

  public List<StorageBlock> storageBlocks = new ArrayList<>();

  public int vacantSlotCount;
  public int occupiedSlotCount;

  private final String predicateString;

  public StorageCapacity(String predicateString) {
    this.predicateString = predicateString;
  }

  @Override
  public double getUsagePercentage() {
    return (occupiedSlotCount / (double) (occupiedSlotCount + vacantSlotCount)) * 100;
  }

  @Override
  public ItemStack render(ConfigKeeper<MainSection> config, InterpretationEnvironment environment) {
    return config.rootSection.capacityDisplay.items.predicateRepresentative.build(
      environment.copy()
        .withVariable("vacant_slot_count", vacantSlotCount)
        .withVariable("occupied_slot_count", occupiedSlotCount)
        .withVariable("total_slot_count", occupiedSlotCount + vacantSlotCount)
        .withVariable("usage_percentage", getUsagePercentage())
        .withVariable("predicate", predicateString)
    );
  }
}
