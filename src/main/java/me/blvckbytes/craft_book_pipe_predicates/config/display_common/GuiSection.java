package me.blvckbytes.craft_book_pipe_predicates.config.display_common;

import at.blvckbytes.component_markup.constructor.SlotType;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.bbconfigmapper.MappingError;
import me.blvckbytes.bbconfigmapper.sections.AConfigSection;
import me.blvckbytes.bbconfigmapper.sections.CSAlways;
import me.blvckbytes.bbconfigmapper.sections.CSDecide;
import me.blvckbytes.bbconfigmapper.sections.CSIgnore;
import me.blvckbytes.craft_book_pipe_predicates.config.CMValue;
import me.blvckbytes.craft_book_pipe_predicates.config.ExpressionValue;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

public class GuiSection<T extends AConfigSection> extends AConfigSection {

  private static final int DEFAULT_ROWS = 3;

  protected @Nullable CMValue title;
  protected @Nullable ExpressionValue rows;

  @CSAlways
  @CSDecide
  public T items;

  @CSIgnore
  protected final Class<T> itemsSectionClass;

  @CSIgnore
  protected int _rows, lastSlot;

  @CSIgnore
  public InterpretationEnvironment inventoryEnvironment;

  public GuiSection(Class<T> itemsSectionClass, EvaluationEnvironmentBuilder baseEnvironment) {
    super(baseEnvironment);
    this.itemsSectionClass = itemsSectionClass;
  }

  @Override
  public @Nullable Class<?> runtimeDecide(String field) {
    if (field.equals("items"))
      return itemsSectionClass;

    return super.runtimeDecide(field);
  }

  @Override
  public void afterParsing(List<Field> fields) throws Exception {
    super.afterParsing(fields);

    _rows = DEFAULT_ROWS;

    if (rows != null) {
      var rowsValue = rows.interpret(null);

      if (rowsValue != null)
        _rows = (int) InterpretationEnvironment.DEFAULT_INTERPRETER.asLong(rowsValue);
    }

    if (_rows < 1 || _rows > 6)
      throw new MappingError("Rows out of range [1;6]");

    lastSlot = _rows * 9 - 1;

    inventoryEnvironment = new InterpretationEnvironment()
      .withVariable("number_of_rows", _rows)
      .withVariable("last_slot", lastSlot);

    for (var field : itemsSectionClass.getDeclaredFields()) {
      if (!GuiItemStackSection.class.isAssignableFrom(field.getType()))
        continue;

      field.setAccessible(true);
      ((GuiItemStackSection) field.get(items)).initializeDisplaySlots(inventoryEnvironment);
    }
  }

  public Inventory createInventory(InterpretationEnvironment environment) {
    if (title == null)
      return Bukkit.createInventory(null, _rows * 9);

    var titleComponent = title.interpret(SlotType.SINGLE_LINE_CHAT, environment).get(0);

    return Bukkit.createInventory(null, _rows * 9, titleComponent);
  }

  public int getRows() {
    return _rows;
  }
}
