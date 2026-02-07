package me.blvckbytes.craft_book_pipe_predicates.search.display.search;

import me.blvckbytes.craft_book_pipe_predicates.search.PredicateAndLabels;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public record SearchDisplayData(
  @Nullable PredicateAndLabels query,
  Collection<String> encounteredLabelValues,
  List<? extends SearchDisplayEntry> entries,
  @Nullable SearchDisplay backToDisplay
) {
  public @Nullable String getQueryPredicateString() {
    if (query == null)
      return null;

    return query.getStringification();
  }

  public Collection<String> getQueryPredicateLabels() {
    if (query == null)
      return Collections.emptyList();

    return query.getLabelValues();
  }
}