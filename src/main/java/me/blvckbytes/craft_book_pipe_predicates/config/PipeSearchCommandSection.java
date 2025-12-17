package me.blvckbytes.craft_book_pipe_predicates.config;

import me.blvckbytes.bukkitevaluable.section.ACommandSection;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;

public class PipeSearchCommandSection extends ACommandSection {

  public static final String INITIAL_NAME = "pipesearch";

  public PipeSearchCommandSection(EvaluationEnvironmentBuilder baseEnvironment) {
    super(INITIAL_NAME, baseEnvironment);
  }
}
