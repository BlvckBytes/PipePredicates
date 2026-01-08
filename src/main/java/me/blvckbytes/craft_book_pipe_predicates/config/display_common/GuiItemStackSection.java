package me.blvckbytes.craft_book_pipe_predicates.config.display_common;

import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.bbconfigmapper.sections.CSIgnore;
import me.blvckbytes.craft_book_pipe_predicates.config.ExpressionValue;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

public class GuiItemStackSection extends ItemStackSection {

  private @Nullable ExpressionValue slots;

  @CSIgnore
  private @Nullable Set<Integer> displaySlots;

  public GuiItemStackSection(EvaluationEnvironmentBuilder baseEnvironment) {
    super(baseEnvironment);
  }

  @Override
  public void afterParsing(List<Field> fields) throws Exception {
    super.afterParsing(fields);
  }

  public void initializeDisplaySlots(InterpretationEnvironment inventoryEnvironment) {
    displaySlots = ExpressionValue.asIntSet(slots, inventoryEnvironment);
  }

  public Set<Integer> getDisplaySlots() {
    return displaySlots == null ? Set.of() : displaySlots;
  }

  public void renderInto(Inventory inventory, InterpretationEnvironment environment) {
    if (displaySlots == null)
      return;

    var item = build(environment);
    var inventorySize = inventory.getSize();

    for (var slot : displaySlots) {
      if (slot < inventorySize)
        inventory.setItem(slot, item);
    }
  }
}
