package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.mechanics.pipe.Pipes;
import me.blvckbytes.bbconfigmapper.ScalarType;
import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.PredicateAndLanguage;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.display.ResultDisplayData;
import me.blvckbytes.craft_book_pipe_predicates.search.display.ResultDisplayHandler;
import me.blvckbytes.craft_book_pipe_predicates.search.cubes.CubeRenderer;
import me.blvckbytes.item_predicate_parser.predicate.StringifyState;
import me.blvckbytes.syllables_matcher.TriState;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class PipeSearchHandler implements Listener {

  private final ResultDisplayHandler resultDisplayHandler;
  private final CubeRenderer cubeRenderer;
  private final ConfigKeeper<MainSection> config;
  private final Plugin plugin;
  private final Pipes pipesMechanic;

  private final Map<UUID, EnumerationSession<?>> enumerationSessionByPlayerId;

  public PipeSearchHandler(
    ResultDisplayHandler resultDisplayHandler,
    CubeRenderer cubeRenderer,
    ConfigKeeper<MainSection> config,
    Plugin plugin
  ) {
    this.resultDisplayHandler = resultDisplayHandler;
    this.cubeRenderer = cubeRenderer;
    this.config = config;
    this.plugin = plugin;
    this.pipesMechanic = (Pipes) CraftBookPlugin.inst().getMechanic(Pipes.class);

    if (pipesMechanic == null)
      throw new IllegalStateException("Expected the pipe-mechanic to be available");

    this.enumerationSessionByPlayerId = new HashMap<>();
  }

  public TriState handleSearch(Player player, Block origin, @Nullable PredicateAndLanguage query) {
    return handleEnumeration(player, () -> (
      new SearchSession(
        origin, pipesMechanic, plugin,
        session -> handleEnumerationWarmup(session, player),
        session -> handleSearchCompletion(session, player, query)
      )
    ));
  }

  private void handleSearchCompletion(SearchSession session, Player player, @Nullable PredicateAndLanguage query) {
    enumerationSessionByPlayerId.remove(player.getUniqueId());

    if (!session.didEncounterPipeBlocks())
      return;

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

      if (session.hasFlag(SearchResultFlag.EXCEEDED_MAX_TUBE_COUNT)) {
        config.rootSection.playerMessages.commandPipePredicateSearchExceededPipes.sendMessage(
          player,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("limit", pipesMechanic.getMaxTubeBlockCount())
            .build()
        );
      }

      if (session.hasFlag(SearchResultFlag.EXCEEDED_MAX_PISTON_COUNT)) {
        config.rootSection.playerMessages.commandPipePredicateSearchExceededPistons.sendMessage(
          player,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("limit", pipesMechanic.getMaxPistonBlockCount())
            .build()
        );
      }

      if (session.hasFlag(SearchResultFlag.EXCEEDED_MAX_RETRY_COUNT)) {
        config.rootSection.playerMessages.commandPipePredicateSearchExceededRetry.sendMessage(
          player,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("limit", SearchSession.MAX_RETRY_COUNT)
            .build()
        );
      }

      if (session.getSnapshotInventories().isEmpty()) {
        config.rootSection.playerMessages.commandPipePredicateSearchNoContainers.sendMessage(
          player,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("piston_count", session.getPistonCount())
            .withStaticVariable("tube_count", session.getTubeCount())
            .build()
        );

        return;
      }

      var predicateString = query == null ? "/" : new StringifyState(true).appendPredicate(query.predicate()).toString();

      var matches = new ArrayList<ItemAndSlot>();

      var resultCounter = 0;

      for (var containerResult : session.getSnapshotInventories()) {
        var blockContents = containerResult.inventory().getStorageContents();

        for (var slotIndex = 0; slotIndex < blockContents.length; ++slotIndex) {
          var item = blockContents[slotIndex];

          if (item == null || item.getType().isAir())
            continue;

          ++resultCounter;

          if (query != null && !query.predicate().test(item))
            continue;

          matches.add(new ItemAndSlot(item, containerResult.block(), slotIndex + containerResult.slotOffset()));
        }
      }

      if (matches.isEmpty()) {
        config.rootSection.playerMessages.commandPipePredicateSearchNoResults.sendMessage(
          player,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("predicate", predicateString)
            .withStaticVariable("item_count", resultCounter)
            .withStaticVariable("container_count", session.getContainerCount())
            .withStaticVariable("piston_count", session.getPistonCount())
            .withStaticVariable("tube_count", session.getTubeCount())
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
          // TODO: Split container counts into categories (chests, shulkers, furnaces, etc.)
          .withStaticVariable("container_count", session.getContainerCount())
          .withStaticVariable("piston_count", session.getPistonCount())
          .withStaticVariable("tube_count", session.getTubeCount())
          .build()
      );

      resultDisplayHandler.show(player, new ResultDisplayData(matches));
    });
  }

  private void handleEnumerationWarmup(EnumerationSession<?> session, Player player) {
    var warmupMessage = config.rootSection.playerMessages.commandPipePredicateSearchActionbarWarmup.asScalar(
      ScalarType.STRING,
      config.rootSection.getBaseEnvironment()
        .withStaticVariable("piston_count", session.getPistonCount())
        .withStaticVariable("tube_count", session.getTubeCount())
        .build()
    );

    //noinspection deprecation
    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(warmupMessage));
  }

  public TriState handleVisualization(Player player, Block origin) {
    return handleEnumeration(player, () -> (
      new VisualizeSession(
        origin, pipesMechanic, plugin,
        session -> handleEnumerationWarmup(session, player),
        session -> handleVisualizationCompletion(session, player)
      )
    ));
  }

  private void handleVisualizationCompletion(VisualizeSession session, Player player) {
    enumerationSessionByPlayerId.remove(player.getUniqueId());

    if (!session.didEncounterPipeBlocks())
      return;

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      if (cubeRenderer.removeAll(player))
        config.rootSection.playerMessages.commandPipePredicateVisualizeClearedPriorVisualization.sendMessage(player, config.rootSection.builtBaseEnvironment);

      var messageEnvironment = config.rootSection.getBaseEnvironment()
        .withStaticVariable("origin_x", session.origin.getX())
        .withStaticVariable("origin_y", session.origin.getY())
        .withStaticVariable("origin_z", session.origin.getZ())
        .withStaticVariable("cube_count", session.getCubeCount())
        .withStaticVariable("cube_limit", VisualizeSession.TUBE_COUNT_LIMIT)
        .build();

      if (!cubeRenderer.renderColoredCubes(player, session.cubePositionsByColor)) {
        config.rootSection.playerMessages.commandPipePredicateVisualizeInternalError.sendMessage(player, messageEnvironment);
        return;
      }

      if (session.didRunIntoLimit())
        config.rootSection.playerMessages.commandPipePredicateVisualizeRanIntoLimit.sendMessage(player, messageEnvironment);

      config.rootSection.playerMessages.commandPipePredicateVisualizeSuccess.sendMessage(player, messageEnvironment);
    });
  }

  private TriState handleEnumeration(Player player, Supplier<EnumerationSession<?>> sessionCreator) {
    var playerId = player.getUniqueId();

    if (enumerationSessionByPlayerId.containsKey(playerId)) {
      config.rootSection.playerMessages.commandPipePredicateSearchInSession.sendMessage(player, config.rootSection.builtBaseEnvironment);
      return TriState.NULL;
    }

    var enumerationSession = sessionCreator.get();

    enumerationSessionByPlayerId.put(playerId, enumerationSession);
    enumerationSession.start();

    if (enumerationSession.didEncounterPipeBlocks())
      return TriState.TRUE;

    return TriState.FALSE;
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    var enumerationSession = enumerationSessionByPlayerId.remove(event.getPlayer().getUniqueId());

    if (enumerationSession != null)
      enumerationSession.terminate();
  }
}
