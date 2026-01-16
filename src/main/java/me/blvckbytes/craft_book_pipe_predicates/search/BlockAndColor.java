package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.mechanics.pipe.TubeColor;
import org.bukkit.block.Block;

public record BlockAndColor(Block block, TubeColor color) {
}
