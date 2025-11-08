package me.blvckbytes.craft_book_pipe_predicates;

import org.bukkit.entity.Player;

public abstract class PredicateInteractionSession {

  private static final long EXPIRY_SECONDS = 15;

  public final Player player;
  public boolean allowMultiUse;
  private long lastUse;

  public PredicateInteractionSession(Player player) {
    this.player = player;

    touchExpiry();
  }

  public void touchExpiry() {
    lastUse = System.currentTimeMillis();
  }

  public boolean isExpired() {
    return System.currentTimeMillis() - lastUse >= EXPIRY_SECONDS * 1000;
  }
}
