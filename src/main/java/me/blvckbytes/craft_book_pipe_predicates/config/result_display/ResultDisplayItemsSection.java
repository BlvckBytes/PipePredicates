package me.blvckbytes.craft_book_pipe_predicates.config.result_display;

import at.blvckbytes.cm_mapper.mapper.section.CSAlways;
import at.blvckbytes.cm_mapper.mapper.section.ConfigSection;
import at.blvckbytes.cm_mapper.section.gui.GuiItemStackSection;
import at.blvckbytes.cm_mapper.section.item.ItemStackSection;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;

@CSAlways
public class ResultDisplayItemsSection extends ConfigSection {

  public GuiItemStackSection previousPage;
  public GuiItemStackSection nextPage;
  public GuiItemStackSection filler;
  public ItemStackSection representativePatch;

  public ResultDisplayItemsSection(InterpretationEnvironment baseEnvironment) {
    super(baseEnvironment);
  }
}
