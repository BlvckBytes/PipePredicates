package me.blvckbytes.craft_book_pipe_predicates.config;

import at.blvckbytes.cm_mapper.section.command.CommandSection;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import at.blvckbytes.component_markup.util.logging.InterpreterLogger;

public class PipePredicateCommandSection extends CommandSection {

  public static final String INITIAL_NAME = "pipepredicate";

  public PipePredicateCommandSection(InterpretationEnvironment baseEnvironment, InterpreterLogger interpreterLogger) {
    super(INITIAL_NAME, baseEnvironment, interpreterLogger);
  }
}
