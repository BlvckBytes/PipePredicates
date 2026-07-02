package me.blvckbytes.pipe_predicates;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import at.blvckbytes.component_markup.util.TriState;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import me.blvckbytes.bbtweaks.util.CompactId;
import me.blvckbytes.pipe_predicates.config.MainSection;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PipeTeleportCommand implements CommandExecutor, TabCompleter, Listener {

  private static final long ALLOWANCE_DURATION_MS = 1000 * 60 * 15;

  // I don't think that cleaning these entries up is necessary at all - they're bound to the lifetime of
  // the corresponding player's session (until quit) and one player will not have multiple tens of thousands
  // of entries. Coordinating cleanup asynchronously is too much of a hassle and doing so synchronously
  // needlessly weighs down the main thread; let's just try and see if it ever becomes an issue.

  private static class PlayerBucket {
    Long2LongMap expirationStampByAllowedBlockId = new Long2LongOpenHashMap();

    PlayerBucket() {
      expirationStampByAllowedBlockId.defaultReturnValue(-1);
    }

    void temporarilyAllow(int x, int y, int z) {
      var blockId = CompactId.computeWorldlessBlockId(x, y, z);
      expirationStampByAllowedBlockId.put(blockId, System.currentTimeMillis() + ALLOWANCE_DURATION_MS);
    }

    TriState doesAllow(int x, int y, int z) {
      var expirationStamp = expirationStampByAllowedBlockId.get(CompactId.computeWorldlessBlockId(x, y, z));

      if (expirationStamp < 0)
        return TriState.NULL;

      if (System.currentTimeMillis() >= expirationStamp)
        return TriState.FALSE;

      return TriState.TRUE;
    }
  }

  private static class WorldBucket {
    Map<UUID, PlayerBucket> playerBucketByPlayerId = new HashMap<>();

    void temporarilyAllow(Player player, Location location) {
      playerBucketByPlayerId
        .computeIfAbsent(player.getUniqueId(), k -> new PlayerBucket())
        .temporarilyAllow(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    TriState doesAllow(Player player, Location location) {
      var playerBucket = playerBucketByPlayerId.get(player.getUniqueId());

      if (playerBucket == null)
        return TriState.NULL;

      return playerBucket.doesAllow(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    void removeAllFor(Player player) {
      playerBucketByPlayerId.remove(player.getUniqueId());
    }
  }

  private final ConfigKeeper<MainSection> config;
  private final Map<UUID, WorldBucket> worldBucketByWorldId;

  public PipeTeleportCommand(ConfigKeeper<MainSection> config) {
    this.worldBucketByWorldId = new HashMap<>();
    this.config = config;
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
    if (!(sender instanceof Player player))
      return true;

    var worldBucket = worldBucketByWorldId.get(player.getWorld().getUID());

    if (worldBucket == null)
      return true;

    if (args.length != 3)
      return true;

    int x, y, z;

    try {
      x = Integer.parseInt(args[0]);
      y = Integer.parseInt(args[1]);
      z = Integer.parseInt(args[2]);
    } catch (Throwable ignored) {
      return true;
    }

    var targetLocation = new Location(player.getWorld(), x, y, z);
    var allowanceState = worldBucket.doesAllow(player, targetLocation);

    // Either tried to guess the command-structure or wanted to teleport from another world - ignore both cases.
    if (allowanceState == TriState.NULL)
      return true;

    var environment = new InterpretationEnvironment()
        .withVariable("x", x)
        .withVariable("y", y)
        .withVariable("z", z);

    if (allowanceState == TriState.FALSE) {
      config.rootSection.playerMessages.commandPipeTeleportExpired.sendMessage(player, environment);
      return true;
    }

    player.teleport(targetLocation);
    config.rootSection.playerMessages.commandPipeTeleportTeleported.sendMessage(player, environment);
    return true;
  }

  @Override
  public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
    return List.of();
  }

  public void temporarilyAllow(Player player, Location location) {
    var world = location.getWorld();

    if (world == null)
      return;

    worldBucketByWorldId
      .computeIfAbsent(world.getUID(), k -> new WorldBucket())
      .temporarilyAllow(player, location);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    var player = event.getPlayer();
    for (var worldBucket : worldBucketByWorldId.values())
      worldBucket.removeAllFor(player);
  }
}
