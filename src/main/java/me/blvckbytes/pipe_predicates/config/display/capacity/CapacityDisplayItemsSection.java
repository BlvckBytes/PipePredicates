package me.blvckbytes.pipe_predicates.config.display.capacity;

import at.blvckbytes.cm_mapper.mapper.section.ConfigSection;
import at.blvckbytes.cm_mapper.section.gui.GuiItemStackSection;
import at.blvckbytes.cm_mapper.section.item.ItemStackSection;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import at.blvckbytes.component_markup.util.logging.InterpreterLogger;

public class CapacityDisplayItemsSection extends ConfigSection {

  public GuiItemStackSection previousPage;
  public GuiItemStackSection nextPage;
  public GuiItemStackSection filler;
  public ItemStackSection predicateRepresentative;
  public ItemStackSection containerRepresentative;
  public GuiItemStackSection searchDetails;
  public GuiItemStackSection sorting;
  public GuiItemStackSection backToPredicatesButton;

  public CapacityDisplayItemsSection(InterpretationEnvironment baseEnvironment, InterpreterLogger interpreterLogger) {
    super(baseEnvironment, interpreterLogger);
  }
}
