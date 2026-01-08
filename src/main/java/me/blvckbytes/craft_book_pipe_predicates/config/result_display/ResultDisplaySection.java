package me.blvckbytes.craft_book_pipe_predicates.config.result_display;

import at.blvckbytes.cm_mapper.section.gui.PaginatedGuiSection;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;

public class ResultDisplaySection extends PaginatedGuiSection<ResultDisplayItemsSection> {

  public ResultDisplaySection(InterpretationEnvironment baseEnvironment) {
    super(ResultDisplayItemsSection.class, baseEnvironment);
  }
}
