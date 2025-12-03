package me.blvckbytes.craft_book_pipe_predicates.search;

import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;

public record InventoryAndBlock(
  Inventory inventory,
  Block block
) {}
