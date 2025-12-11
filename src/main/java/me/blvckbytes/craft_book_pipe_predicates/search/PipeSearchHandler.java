package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.mechanics.pipe.Pipes;
import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.PredicateAndLanguage;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
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
  private final ConfigKeeper<MainSection> config;
  private final Plugin plugin;
  private final Pipes pipesMechanic;

  private final Map<UUID, SearchSession> searchSessionByPlayerId;

  public PipeSearchHandler(
    ResultDisplayHandler resultDisplayHandler,
    ConfigKeeper<MainSection> config,
    Plugin plugin
  ) {
    this.resultDisplayHandler = resultDisplayHandler;
    this.config = config;
    this.plugin = plugin;
    this.pipesMechanic = (Pipes) CraftBookPlugin.inst().getMechanic(Pipes.class);

    if (pipesMechanic == null)
      throw new IllegalStateException("Expected the pipe-mechanic to be available");

    this.searchSessionByPlayerId = new HashMap<>();
  }

  public void handleSearch(Player player, Block pistonBlock, PredicateAndLanguage query) {
    var playerId = player.getUniqueId();

    if (searchSessionByPlayerId.containsKey(playerId)) {
      config.rootSection.playerMessages.commandPipePredicateSearchInSession.sendMessage(player, config.rootSection.builtBaseEnvironment);
      return;
    }

    var pistonSearch = new PistonSearchSession(pistonBlock, pipesMechanic, plugin, (pistons, tubeCount, flags) -> {
      searchSessionByPlayerId.remove(playerId);

      if (flags.contains(PistonSearchFlag.EXCEEDED_MAX_TUBE_COUNT)) {
        config.rootSection.playerMessages.commandPipePredicateSearchExceededPipes.sendMessage(
          player,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("limit", pipesMechanic.getMaxTubeBlockCount())
            .build()
        );
      }

      if (flags.contains(PistonSearchFlag.EXCEEDED_MAX_PISTON_COUNT)) {
        config.rootSection.playerMessages.commandPipePredicateSearchExceededPistons.sendMessage(
          player,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("limit", pipesMechanic.getMaxPistonBlockCount())
            .build()
        );
      }

      if (flags.contains(PistonSearchFlag.EXCEEDED_MAX_RETRY_COUNT)) {
        config.rootSection.playerMessages.commandPipePredicateSearchExceededRetry.sendMessage(
          player,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("limit", PistonSearchSession.MAX_RETRY_COUNT)
            .build()
        );
      }

      if (pistons.isEmpty()) {
        config.rootSection.playerMessages.commandPipePredicateSearchNoPistons.sendMessage(player, config.rootSection.builtBaseEnvironment);
        return;
      }

      var containerSearch = new ContainerSearchSession(pistons, containerResults -> {
        searchSessionByPlayerId.remove(playerId);

        if (containerResults.isEmpty()) {
          config.rootSection.playerMessages.commandPipePredicateSearchNoContainers.sendMessage(player, config.rootSection.builtBaseEnvironment);
          return;
        }

        config.rootSection.playerMessages.commandPipePredicateSearchBeginTesting.sendMessage(
          player,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("container_count", containerResults.size())
            .build()
        );

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
            config.rootSection.playerMessages.commandPipePredicateSearchNoResults.sendMessage(
              player,
              config.rootSection.getBaseEnvironment()
                .withStaticVariable("predicate", predicateString)
                .withStaticVariable("item_count", resultCounter)
                .build()
            );

            return;
          }

          config.rootSection.playerMessages.commandPipePredicateSearchShowingResults.sendMessage(
            player,
            config.rootSection.getBaseEnvironment()
              .withStaticVariable("predicate", predicateString)
              .withStaticVariable("item_count", resultCounter)
              .withStaticVariable("match_count", matches.size())
              .build()
          );

          resultDisplayHandler.show(player, new ResultDisplayData(matches));
        });
      });

      config.rootSection.playerMessages.commandPipePredicateSearchBeginEnumerateContainers.sendMessage(
        player,
        config.rootSection.getBaseEnvironment()
          .withStaticVariable("piston_count", pistons.size())
          .withStaticVariable("tube_count", tubeCount)
          .build()
      );

      searchSessionByPlayerId.put(playerId, containerSearch);
      containerSearch.start();
    });

    config.rootSection.playerMessages.commandPipePredicateSearchBeginEnumeratePistons.sendMessage(player, config.rootSection.builtBaseEnvironment);

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
