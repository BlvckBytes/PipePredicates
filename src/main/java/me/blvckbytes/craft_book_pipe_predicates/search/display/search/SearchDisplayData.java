package me.blvckbytes.craft_book_pipe_predicates.search.display.search;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public record SearchDisplayData(
  @Nullable String predicateString,
  List<? extends SearchDisplayEntry> entries,
  @Nullable SearchDisplay backToDisplay
) {}