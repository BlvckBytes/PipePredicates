package me.blvckbytes.craft_book_pipe_predicates.config.display.capacity;

import at.blvckbytes.cm_mapper.section.gui.PaginatedGuiSection;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import at.blvckbytes.component_markup.util.logging.InterpreterLogger;

public class CapacityDisplaySection extends PaginatedGuiSection<CapacityDisplayItemsSection> {

  public CapacityDisplaySection(InterpretationEnvironment baseEnvironment, InterpreterLogger interpreterLogger) {
    super(CapacityDisplayItemsSection.class, baseEnvironment, interpreterLogger);
  }
}
