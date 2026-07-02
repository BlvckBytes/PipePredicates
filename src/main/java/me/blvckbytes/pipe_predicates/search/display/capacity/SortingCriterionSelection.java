package me.blvckbytes.pipe_predicates.search.display.capacity;

import at.blvckbytes.component_markup.markup.interpreter.DirectFieldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class SortingCriterionSelection implements DirectFieldAccess {

  public final SortingCriteria criterion;
  public SortingSelection selection;
  public boolean active;

  public SortingCriterionSelection(SortingCriteria criterion, SortingSelection selection) {
    this.criterion = criterion;
    this.selection = selection;
  }

  @Override
  public @Nullable Object accessField(String rawIdentifier) {
    return switch (rawIdentifier) {
      case "criterion" -> criterion.name();
      case "selection" -> selection.name();
      case "active" -> active;
      default -> DirectFieldAccess.UNKNOWN_FIELD_SENTINEL;
    };
  }

  @Override
  public @Nullable Set<String> getAvailableFields() {
    return Set.of("criterion", "selection", "active");
  }
}
