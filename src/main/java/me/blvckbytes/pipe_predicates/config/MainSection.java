package me.blvckbytes.pipe_predicates.config;

import at.blvckbytes.cm_mapper.mapper.section.ConfigSection;
import at.blvckbytes.cm_mapper.mapper.section.CSAlways;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import at.blvckbytes.component_markup.util.logging.InterpreterLogger;
import me.blvckbytes.pipe_predicates.config.display.capacity.CapacityDisplaySection;
import me.blvckbytes.pipe_predicates.config.display.search.SearchDisplaySection;
import me.blvckbytes.item_predicate_parser.translation.TranslationLanguage;

@CSAlways
public class MainSection extends ConfigSection {

  public CommandsSection commands;
  public PlayerMessagesSection playerMessages;
  public SearchDisplaySection searchDisplay;
  public CapacityDisplaySection capacityDisplay;

  public TranslationLanguage defaultPredicateLanguage;

  public MainSection(InterpretationEnvironment baseEnvironment, InterpreterLogger interpreterLogger) {
    super(baseEnvironment, interpreterLogger);

    this.defaultPredicateLanguage = TranslationLanguage.ENGLISH_US;
  }
}
