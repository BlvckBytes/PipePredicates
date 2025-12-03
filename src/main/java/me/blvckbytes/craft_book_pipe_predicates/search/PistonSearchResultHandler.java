package me.blvckbytes.craft_book_pipe_predicates.search;

import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.List;

@FunctionalInterface
public interface PistonSearchResultHandler {

  void handle(List<Block> pistons, EnumSet<PistonSearchFlag> flags);

}
