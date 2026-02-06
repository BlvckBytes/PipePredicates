package me.blvckbytes.craft_book_pipe_predicates.config.search_display;

import at.blvckbytes.cm_mapper.mapper.section.CSAlways;
import at.blvckbytes.cm_mapper.mapper.section.ConfigSection;
import at.blvckbytes.cm_mapper.section.gui.GuiItemStackSection;
import at.blvckbytes.cm_mapper.section.item.ItemStackSection;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import at.blvckbytes.component_markup.util.logging.InterpreterLogger;

@CSAlways
public class SearchDisplayItemsSection extends ConfigSection {

  public GuiItemStackSection previousPage;
  public GuiItemStackSection backToCollectionsButton;
  public GuiItemStackSection searchDetails;
  public GuiItemStackSection nextPage;
  public GuiItemStackSection filler;
  public ItemStackSection stackRepresentativePatch;
  public ItemStackSection collectionRepresentativePatch;

  public SearchDisplayItemsSection(InterpretationEnvironment baseEnvironment, InterpreterLogger interpreterLogger) {
    super(baseEnvironment, interpreterLogger);
  }
}
