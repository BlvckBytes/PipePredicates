package me.blvckbytes.craft_book_pipe_predicates.config.result_display;

import me.blvckbytes.craft_book_pipe_predicates.config.display_common.PaginatedGuiSection;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;

public class ResultDisplaySection extends PaginatedGuiSection<ResultDisplayItemsSection> {

  public ResultDisplaySection(EvaluationEnvironmentBuilder baseEnvironment) {
    super(ResultDisplayItemsSection.class, baseEnvironment);
  }
}
