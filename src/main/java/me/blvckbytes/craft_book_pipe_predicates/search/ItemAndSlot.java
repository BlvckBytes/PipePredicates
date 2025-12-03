package me.blvckbytes.craft_book_pipe_predicates.search;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

public record ItemAndSlot(ItemStack item, Block block, int slot) {}
