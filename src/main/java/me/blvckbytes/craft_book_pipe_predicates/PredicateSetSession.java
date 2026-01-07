package me.blvckbytes.craft_book_pipe_predicates;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class PredicateSetSession extends InteractionSession {

  // Null-values imply a removal of the current predicate, if any
  public final @Nullable PredicateAndLanguage valueToSet;

  public PredicateSetSession(Player player, @Nullable PredicateAndLanguage valueToSet) {
    super(player);
    this.valueToSet = valueToSet;
  }
}
