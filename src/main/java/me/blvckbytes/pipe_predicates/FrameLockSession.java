package me.blvckbytes.pipe_predicates;

import org.bukkit.entity.Player;

public class FrameLockSession extends InteractionSession {

  public final boolean lockOrUnlock;

  public FrameLockSession(Player player, boolean lockOrUnlock) {
    super(player);

    this.lockOrUnlock = lockOrUnlock;
  }
}
