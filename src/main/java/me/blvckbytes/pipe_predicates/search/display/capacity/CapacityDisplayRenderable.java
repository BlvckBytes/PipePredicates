package me.blvckbytes.pipe_predicates.search.display.capacity;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.pipe_predicates.config.MainSection;
import org.bukkit.inventory.ItemStack;

public interface CapacityDisplayRenderable {

  ItemStack render(ConfigKeeper<MainSection> config, InterpretationEnvironment environment);

  int getTotalCapacity();

  double getUsagePercentage();

  UsageLevel getUsageLevel();

}
