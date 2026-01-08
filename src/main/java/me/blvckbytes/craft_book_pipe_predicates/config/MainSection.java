package me.blvckbytes.craft_book_pipe_predicates.config;

import at.blvckbytes.cm_mapper.mapper.section.ConfigSection;
import at.blvckbytes.cm_mapper.mapper.section.CSAlways;
import me.blvckbytes.craft_book_pipe_predicates.config.result_display.ResultDisplaySection;
import me.blvckbytes.item_predicate_parser.translation.TranslationLanguage;

import java.util.ArrayList;
import java.util.List;

@CSAlways
public class MainSection extends ConfigSection {

  public CommandsSection commands;
  public PlayerMessagesSection playerMessages;
  public ResultDisplaySection resultDisplay;

  public TranslationLanguage defaultPredicateLanguage;
  public List<String> fakeEventSkippedListeners;

  public MainSection() {
    this.defaultPredicateLanguage = TranslationLanguage.ENGLISH_US;
    this.fakeEventSkippedListeners = new ArrayList<>();
  }
}
