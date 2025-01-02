package me.blvckbytes.craft_book_pipe_predicates.config;

import me.blvckbytes.bbconfigmapper.sections.AConfigSection;
import me.blvckbytes.bbconfigmapper.sections.CSAlways;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import me.blvckbytes.item_predicate_parser.translation.TranslationLanguage;

import java.util.ArrayList;
import java.util.List;

@CSAlways
public class MainSection extends AConfigSection {

  public CommandsSection commands;
  public PlayerMessagesSection playerMessages;

  public TranslationLanguage defaultPredicateLanguage;
  public List<String> fakeEventSkippedListeners;

  public MainSection(EvaluationEnvironmentBuilder baseEnvironment) {
    super(baseEnvironment);

    this.defaultPredicateLanguage = TranslationLanguage.ENGLISH_US;
    this.fakeEventSkippedListeners = new ArrayList<>();
  }
}
