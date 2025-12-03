package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.mechanics.pipe.Pipes;
import me.blvckbytes.craft_book_pipe_predicates.PredicateAndLanguage;
import me.blvckbytes.craft_book_pipe_predicates.search.display.ResultDisplayData;
import me.blvckbytes.craft_book_pipe_predicates.search.display.ResultDisplayHandler;
import me.blvckbytes.item_predicate_parser.predicate.StringifyState;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class PipeSearchHandler implements Listener {

  private final ResultDisplayHandler resultDisplayHandler;
  private final Plugin plugin;
  private final Pipes pipesMechanic;

  private final Map<UUID, SearchSession> searchSessionByPlayerId;

  public PipeSearchHandler(ResultDisplayHandler resultDisplayHandler, Plugin plugin) {
    this.resultDisplayHandler = resultDisplayHandler;
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
        player.sendMessage("§eStopping early: exceeded max piston count of " + PistonSearchSession.MAX_PISTON_COUNT);

      if (flags.contains(PistonSearchFlag.EXCEEDED_MAX_RETRY_COUNT))
        player.sendMessage("§Stopping early: exceeded max retry count of " + PistonSearchSession.MAX_RETRY_COUNT);

      if (pistons.isEmpty()) {
        player.sendMessage("§cNo pistons found!");
        return;
      }

      var containerSearch = new ContainerSearchSession(pistons, containerResults -> {
        searchSessionByPlayerId.remove(playerId);

        if (containerResults.isEmpty()) {
          player.sendMessage("§cNo containers found!");
          return;
        }

        player.sendMessage("§aTesting all items of " + containerResults.size() + " containers (this could take a while)...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
          var matches = new ArrayList<ItemAndSlot>();

          var resultCounter = 0;

          for (var containerResult : containerResults) {
            var blockContents = containerResult.inventory().getStorageContents();

            for (var slotIndex = 0; slotIndex < blockContents.length; ++slotIndex) {
              var item = blockContents[slotIndex];

              if (item == null || item.getType().isAir())
                continue;

              ++resultCounter;

              if (!query.predicate().test(item))
                continue;

              matches.add(new ItemAndSlot(item, containerResult.block(), slotIndex));
            }
          }

          var predicateString = new StringifyState(true).appendPredicate(query.predicate()).toString();

          if (matches.isEmpty()) {
            player.sendMessage("§cYour query of §4" + predicateString + " §cdidn't result in any matches!");
            return;
          }

          player.sendMessage("§aYour query of §2" + predicateString + " §aresulted in §2" + matches.size() + "/" + resultCounter + " §amatches!");

          resultDisplayHandler.show(player, new ResultDisplayData(matches));
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
