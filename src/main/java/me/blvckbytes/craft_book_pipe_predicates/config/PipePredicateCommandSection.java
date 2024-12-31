package me.blvckbytes.craft_book_pipe_predicates.config;

import me.blvckbytes.bukkitevaluable.section.ACommandSection;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;

public class PipePredicateCommandSection extends ACommandSection {

  public static final String INITIAL_NAME = "pipepredicate";

  public PipePredicateCommandSection(EvaluationEnvironmentBuilder baseEnvironment) {
    super(INITIAL_NAME, baseEnvironment);
  }
}
