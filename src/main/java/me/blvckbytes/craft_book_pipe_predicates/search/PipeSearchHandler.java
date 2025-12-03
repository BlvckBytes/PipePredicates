package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.mechanics.pipe.Pipes;
import me.blvckbytes.craft_book_pipe_predicates.PredicateAndLanguage;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PipeSearchHandler implements Listener {

  private final Plugin plugin;
  private final Pipes pipesMechanic;

  private final Map<UUID, SearchSession> searchSessionByPlayerId;

  public PipeSearchHandler(Plugin plugin) {
    this.plugin = plugin;
    this.pipesMechanic = (Pipes) CraftBookPlugin.inst().getMechanic(Pipes.class);

    if (pipesMechanic == null)
      throw new IllegalStateException("Expected the pipe-mechanic to be available");

    this.searchSessionByPlayerId = new HashMap<>();
  }

  // TODO: These messages should be configurable

  public void handleSearch(Player player, Block pistonBlock, PredicateAndLanguage query) {
    var playerId = player.getUniqueId();

    if (searchSessionByPlayerId.containsKey(playerId)) {
      player.sendMessage("§cAlready in an active search-session");
      return;
    }

    var pistonSearch = new PistonSearchSession(pistonBlock, pipesMechanic, plugin, (pistons, flags) -> {
      searchSessionByPlayerId.remove(playerId);

      if (flags.contains(PistonSearchFlag.EXCEEDED_MAX_PIPE_COUNT))
        player.sendMessage("§eStopping early: exceeded max pipe count of " + PistonSearchSession.MAX_PIPE_COUNT);

      if (flags.contains(PistonSearchFlag.EXCEEDED_MAX_PISTON_COUNT))
        player.sendMessage("§eStopping early: exceeded max piston count" + PistonSearchSession.MAX_PISTON_COUNT);

      if (flags.contains(PistonSearchFlag.EXCEEDED_MAX_RETRY_COUNT))
        player.sendMessage("§Stopping early: exceeded max retry count" + PistonSearchSession.MAX_RETRY_COUNT);

      if (pistons.isEmpty()) {
        player.sendMessage("§cNo pistons found!");
        return;
      }

      var containerSearch = new ContainerSearchSession(pistons, results -> {
        searchSessionByPlayerId.remove(playerId);

        if (results.isEmpty()) {
          player.sendMessage("§cNo containers found!");
          return;
        }

        player.sendMessage("§aTesting all items of " + results.size() + " containers (this could take a while)...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
          var resultCounter = 0;
          var matchCounter = 0;

          for (var result : results) {
            for (var item : result.inventory().getContents()) {
              if (item == null || item.getType().isAir())
                continue;

              ++resultCounter;

              if (!query.predicate().test(item))
                continue;

              player.sendMessage(item.getType().name() + " at " + result.block().getX() + " " + result.block().getY() + " " + result.block().getZ());

              ++matchCounter;
            }
          }

          player.sendMessage("§aMatches total: " + matchCounter + ", results total: " + resultCounter);
        });
      });

      player.sendMessage("§aAccessing attached containers of " + pistons.size () + " pistons (this could take a while)...");

      searchSessionByPlayerId.put(playerId, containerSearch);
      containerSearch.start();
    });

    player.sendMessage("§aEnumerating all pistons (this could take a while)...");

    searchSessionByPlayerId.put(playerId, pistonSearch);
    pistonSearch.start();
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    var searchSession = searchSessionByPlayerId.remove(event.getPlayer().getUniqueId());

    if (searchSession != null)
      searchSession.terminate();
  }
}
