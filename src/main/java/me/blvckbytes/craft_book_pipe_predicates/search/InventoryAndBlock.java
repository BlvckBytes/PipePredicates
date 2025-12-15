package me.blvckbytes.craft_book_pipe_predicates.search;

import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;

public record InventoryAndBlock(
  Inventory inventory,
  Block block,
  // Double-chests are represented by two individual containers, with each side
  // having its own snapshot-inventory. When accessing the double-chest in a live
  // manner, we'll get a view called a DoubleChestInventory - thus, the slots of the
  // other half need to be offset by the number if slots in a single chest.
  int slotOffset
) {}
