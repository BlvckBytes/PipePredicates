package me.blvckbytes.craft_book_pipe_predicates.search.display;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import org.bukkit.inventory.ItemStack;

public interface ResultDisplayEntry {

  ItemStack makeRepresentative(ConfigKeeper<MainSection> config);

}
