package me.blvckbytes.craft_book_pipe_predicates;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class PredicateSearchSession extends PredicateInteractionSession {

  public final @Nullable PredicateAndLanguage query;

  public PredicateSearchSession(Player player, @Nullable PredicateAndLanguage query) {
    super(player);
    this.query = query;
  }
}
