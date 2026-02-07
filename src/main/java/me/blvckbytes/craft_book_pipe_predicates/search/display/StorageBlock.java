package me.blvckbytes.craft_book_pipe_predicates.search.display;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.display.capacity.CapacityDisplayRenderable;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

public record StorageBlock(
  Block block,
  Material material,
  int occupiedSlotCount,
  int inventorySize
) implements CapacityDisplayRenderable {

  @Override
  public double getUsagePercentage() {
    return (occupiedSlotCount / (double) inventorySize) * 100;
  }

  @Override
  public ItemStack render(ConfigKeeper<MainSection> config, InterpretationEnvironment environment) {
    return config.rootSection.capacityDisplay.items.containerRepresentative.build(
      environment.copy()
        .withVariable("container_type", material.name())
        .withVariable("container_x", block.getX())
        .withVariable("container_y", block.getY())
        .withVariable("container_z", block.getZ())
        .withVariable("occupied_slot_count", occupiedSlotCount)
        .withVariable("total_slot_count", inventorySize)
        .withVariable("usage_percentage", getUsagePercentage())
    );
  }
}
