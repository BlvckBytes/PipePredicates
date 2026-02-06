package me.blvckbytes.craft_book_pipe_predicates.search;

import at.blvckbytes.component_markup.markup.interpreter.DirectFieldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class StorageCapacity implements DirectFieldAccess {

  public List<SearchedInventory> mainBlockInventories = new ArrayList<>();

  public int vacantSlots;
  public int occupiedSlots;

  private final String predicateString;

  public StorageCapacity(String predicateString) {
    this.predicateString = predicateString;
  }

  public int getUsagePercent() {
    return (int) Math.round(occupiedSlots / (double) (occupiedSlots + vacantSlots));
  }

  @Override
  public @Nullable Object accessField(String rawIdentifier) {
    return switch (rawIdentifier) {
      case "vacant_slot_count" -> vacantSlots;
      case "occupied_slot_count" -> occupiedSlots;
      case "usage_percent" -> getUsagePercent();
      case "predicate" -> predicateString;
      default -> DirectFieldAccess.UNKNOWN_FIELD_SENTINEL;
    };
  }

  @Override
  public @Nullable Set<String> getAvailableFields() {
    return Set.of("vacant_slot_count", "occupied_slot_count", "usage_percent", "predicate");
  }
}
