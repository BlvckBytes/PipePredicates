package me.blvckbytes.craft_book_pipe_predicates.config;

import at.blvckbytes.cm_mapper.mapper.section.ConfigSection;
import at.blvckbytes.cm_mapper.mapper.section.CSAlways;

@CSAlways
public class CommandsSection extends ConfigSection {

  public PipePredicateCommandSection pipePredicate;
  public PipeSearchCommandSection pipeSearch;

}
