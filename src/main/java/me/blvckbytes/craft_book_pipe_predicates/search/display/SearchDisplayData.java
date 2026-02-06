package me.blvckbytes.craft_book_pipe_predicates.search.display;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public record SearchDisplayData(
  boolean useActionCycle,
  @Nullable String predicateString,
  List<? extends SearchDisplayEntry> entries,
  @Nullable SearchDisplay backToDisplay
) {}