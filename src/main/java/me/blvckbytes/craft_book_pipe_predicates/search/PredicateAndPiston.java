package me.blvckbytes.craft_book_pipe_predicates.search;

import at.blvckbytes.component_markup.markup.interpreter.DirectFieldAccess;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.predicate.stringify.PlainStringifier;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class PredicateAndPiston implements DirectFieldAccess {

  public final ItemPredicate predicate;
  public final String predicateString;
  public final Block pistonBlock;

  public PredicateAndPiston(ItemPredicate predicate, Block pistonBlock) {
    this.predicate = predicate;
    this.pistonBlock = pistonBlock;
    this.predicateString = PlainStringifier.stringify(predicate, false);
  }

  @Override
  public @Nullable Object accessField(String rawIdentifier) {
    return switch (rawIdentifier) {
      case "x" -> pistonBlock.getX();
      case "y" -> pistonBlock.getY();
      case "z" -> pistonBlock.getZ();
      case "predicate" -> predicateString;
      default -> DirectFieldAccess.UNKNOWN_FIELD_SENTINEL;
    };
  }

  @Override
  public @Nullable Set<String> getAvailableFields() {
    return Set.of("x", "y", "z", "predicate");
  }
}
