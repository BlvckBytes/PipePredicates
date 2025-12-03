package me.blvckbytes.craft_book_pipe_predicates;

import org.bukkit.entity.Player;

public class PredicateSearchSession extends PredicateInteractionSession {

  public final PredicateAndLanguage query;

  public PredicateSearchSession(Player player, PredicateAndLanguage query) {
    super(player);
    this.query = query;
  }
}
