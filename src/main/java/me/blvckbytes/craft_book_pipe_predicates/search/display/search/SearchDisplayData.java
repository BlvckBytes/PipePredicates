package me.blvckbytes.craft_book_pipe_predicates.search.display.search;

import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record SearchDisplayData(
  @Nullable ItemPredicate predicate,
  List<? extends SearchDisplayEntry> entries,
  @Nullable SearchDisplay backToDisplay
) {}