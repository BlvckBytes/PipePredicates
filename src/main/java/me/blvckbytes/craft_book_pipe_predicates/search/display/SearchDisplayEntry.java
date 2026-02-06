package me.blvckbytes.craft_book_pipe_predicates.search.display;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import org.bukkit.inventory.ItemStack;

public interface SearchDisplayEntry {

  ItemStack makeRepresentative(InterpretationEnvironment baseEnvironment, ConfigKeeper<MainSection> config);

}
