package me.blvckbytes.pipe_predicates.search.display.search;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.pipe_predicates.config.MainSection;
import me.blvckbytes.pipe_predicates.search.ItemAndSlot;
import org.bukkit.inventory.ItemStack;

public class ItemStackEntry implements SearchDisplayEntry {

  public final ItemAndSlot itemAndSlot;

  public ItemStackEntry(ItemAndSlot itemAndSlot) {
    this.itemAndSlot = itemAndSlot;
  }

  @Override
  public ItemStack makeRepresentative(InterpretationEnvironment baseEnvironment, ConfigKeeper<MainSection> config) {
    var representativeItem = new ItemStack(itemAndSlot.item());

    config.rootSection.searchDisplay.items.stackRepresentativePatch.patch(
      representativeItem,
      baseEnvironment.copy()
        .withVariable("container_x", itemAndSlot.block().getX())
        .withVariable("container_y", itemAndSlot.block().getY())
        .withVariable("container_z", itemAndSlot.block().getZ())
        .withVariable("slot", itemAndSlot.slot() + 1)
    );

    return representativeItem;
  }
}
