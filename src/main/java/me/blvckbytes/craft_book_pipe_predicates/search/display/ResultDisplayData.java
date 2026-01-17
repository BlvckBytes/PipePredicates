package me.blvckbytes.craft_book_pipe_predicates.search.display;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ResultDisplayData(
  @Nullable String predicateString,
  List<? extends ResultDisplayEntry> entries,
  @Nullable ResultDisplay backToDisplay
) {}