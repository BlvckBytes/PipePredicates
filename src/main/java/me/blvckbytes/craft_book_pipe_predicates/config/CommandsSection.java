package me.blvckbytes.craft_book_pipe_predicates.config;

import at.blvckbytes.cm_mapper.mapper.section.ConfigSection;
import at.blvckbytes.cm_mapper.mapper.section.CSAlways;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;

@CSAlways
public class CommandsSection extends ConfigSection {

  public PipePredicateCommandSection pipePredicate;
  public PipeSearchCommandSection pipeSearch;

  public CommandsSection(InterpretationEnvironment baseEnvironment) {
    super(baseEnvironment);
  }
}
