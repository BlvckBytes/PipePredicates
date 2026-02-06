package me.blvckbytes.craft_book_pipe_predicates.config.search_display;

import at.blvckbytes.cm_mapper.section.gui.PaginatedGuiSection;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import at.blvckbytes.component_markup.util.logging.InterpreterLogger;

public class SearchDisplaySection extends PaginatedGuiSection<SearchDisplayItemsSection> {

  public SearchDisplaySection(InterpretationEnvironment baseEnvironment, InterpreterLogger interpreterLogger) {
    super(SearchDisplayItemsSection.class, baseEnvironment, interpreterLogger);
  }
}
