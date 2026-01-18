package me.blvckbytes.craft_book_pipe_predicates.search;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.mechanics.pipe.Pipes;
import com.sk89q.craftbook.mechanics.pipe.TubeColor;
import me.blvckbytes.craft_book_pipe_predicates.FloodgateIntegration;
import me.blvckbytes.craft_book_pipe_predicates.PredicateAndLanguage;
import me.blvckbytes.craft_book_pipe_predicates.config.ContainerCount;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.display.ItemCollectionEntry;
import me.blvckbytes.craft_book_pipe_predicates.search.display.ResultDisplayData;
import me.blvckbytes.craft_book_pipe_predicates.search.display.ResultDisplayHandler;
import me.blvckbytes.craft_book_pipe_predicates.search.cubes.CubeRenderer;
import me.blvckbytes.item_predicate_parser.predicate.StringifyState;
import me.blvckbytes.syllables_matcher.TriState;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class PipeSearchHandler implements Listener {

  private final ResultDisplayHandler resultDisplayHandler;
  private final CubeRenderer cubeRenderer;
  private final @Nullable FloodgateIntegration floodgateIntegration;
  private final ConfigKeeper<MainSection> config;
  private final Plugin plugin;
  private final Pipes pipesMechanic;

  private final Map<UUID, EnumerationSession<?>> enumerationSessionByPlayerId;

  public PipeSearchHandler(
    ResultDisplayHandler resultDisplayHandler,
    CubeRenderer cubeRenderer,
    @Nullable FloodgateIntegration floodgateIntegration,
    ConfigKeeper<MainSection> config,
    Plugin plugin
  ) {
    this.resultDisplayHandler = resultDisplayHandler;
    this.cubeRenderer = cubeRenderer;
    this.floodgateIntegration = floodgateIntegration;
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
          new InterpretationEnvironment()
            .withVariable("limit", pipesMechanic.getMaxTubeBlockCount())
        );
      }

      if (session.hasFlag(SearchResultFlag.EXCEEDED_MAX_PISTON_COUNT)) {
        config.rootSection.playerMessages.commandPipePredicateSearchExceededPistons.sendMessage(
          player,
          new InterpretationEnvironment()
            .withVariable("limit", pipesMechanic.getMaxPistonBlockCount())
        );
      }

      if (session.hasFlag(SearchResultFlag.EXCEEDED_MAX_RETRY_COUNT)) {
        config.rootSection.playerMessages.commandPipePredicateSearchExceededRetry.sendMessage(
          player,
          new InterpretationEnvironment()
            .withVariable("limit", SearchSession.MAX_RETRY_COUNT)
        );
      }

      if (session.getSnapshotInventories().isEmpty()) {
        config.rootSection.playerMessages.commandPipePredicateSearchNoContainers.sendMessage(
          player,
          new InterpretationEnvironment()
            .withVariable("piston_count", session.getPistonCount())
            .withVariable("tube_count", session.getTubeCount())
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

      var containerCounts = new ArrayList<ContainerCount>();
      var totalContainerCount = session.forEachContainerCountAndGetSum((material, amount) -> containerCounts.add(new ContainerCount(material, amount)));

      var environment = new InterpretationEnvironment()
        .withVariable("predicate", predicateString)
        .withVariable("item_count", resultCounter)
        .withVariable("total_container_count", totalContainerCount)
        .withVariable("container_counts", containerCounts)
        .withVariable("piston_count", session.getPistonCount())
        .withVariable("tube_count", session.getTubeCount())
        .withVariable("match_count", matches.size());

      if (matches.isEmpty()) {
        config.rootSection.playerMessages.commandPipePredicateSearchNoResults.sendMessage(player, environment);
        return;
      }

      config.rootSection.playerMessages.commandPipePredicateSearchShowingResults.sendMessage(player, environment);

      // Let's show the bucketed overview by default instead of the other way around, as I
      // believe that there's not much of a need for the individual screen anymore.
      var displayData = ItemCollectionEntry.collectEntries(matches);

      var useActionCycle = floodgateIntegration != null && floodgateIntegration.isFloodgatePlayer(player);

      resultDisplayHandler.show(player, new ResultDisplayData(useActionCycle, predicateString, displayData, null));
    });
  }

  private void handleEnumerationWarmup(EnumerationSession<?> session, Player player) {
    config.rootSection.playerMessages.commandPipePredicateSearchActionbarWarmup.sendActionBar(
      player,
      new InterpretationEnvironment()
        .withVariable("piston_count", session.getPistonCount())
        .withVariable("tube_count", session.getTubeCount())
    );
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
        config.rootSection.playerMessages.commandPipePredicateVisualizeClearedPriorVisualization.sendMessage(player);

      var messageEnvironment = new InterpretationEnvironment()
        .withVariable("origin_x", session.origin.getX())
        .withVariable("origin_y", session.origin.getY())
        .withVariable("origin_z", session.origin.getZ())
        .withVariable("cube_count", session.tubeBlocks.size())
        .withVariable("cube_limit", VisualizeSession.CUBE_COUNT_LIMIT);

      var cubePositionsByColor = new HashMap<TubeColor, List<Vector>>();

      session.tubeBlocks.forEach(it -> {
        var vector = new Vector(it.block().getX(), it.block().getY(), it.block().getZ());
        cubePositionsByColor.computeIfAbsent(it.color(), k -> new ArrayList<>()).add(vector);
      });

      if (!cubeRenderer.renderColoredCubes(player, cubePositionsByColor)) {
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
      config.rootSection.playerMessages.commandPipePredicateSearchInSession.sendMessage(player);
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
