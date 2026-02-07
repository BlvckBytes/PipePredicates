package me.blvckbytes.craft_book_pipe_predicates.search;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.mechanics.pipe.EnumerationBehavior;
import com.sk89q.craftbook.mechanics.pipe.Pipes;
import com.sk89q.craftbook.mechanics.pipe.TubeColor;
import me.blvckbytes.craft_book_pipe_predicates.PistonPredicateRegistry;
import me.blvckbytes.craft_book_pipe_predicates.PredicateAndLanguage;
import me.blvckbytes.craft_book_pipe_predicates.config.ContainerCount;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.display.capacity.CapacityDisplayData;
import me.blvckbytes.craft_book_pipe_predicates.search.display.capacity.CapacityDisplayHandler;
import me.blvckbytes.craft_book_pipe_predicates.search.display.search.ItemCollectionEntry;
import me.blvckbytes.craft_book_pipe_predicates.search.display.search.SearchDisplayData;
import me.blvckbytes.craft_book_pipe_predicates.search.display.search.SearchDisplayHandler;
import me.blvckbytes.craft_book_pipe_predicates.search.cubes.CubeRenderer;
import me.blvckbytes.item_predicate_parser.predicate.stringify.PlainStringifier;
import me.blvckbytes.syllables_matcher.TriState;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public class PipeSearchHandler implements Listener {

  private final PistonPredicateRegistry predicateRegistry;
  private final SearchDisplayHandler searchDisplayHandler;
  private final CapacityDisplayHandler capacityDisplayHandler;
  private final CubeRenderer cubeRenderer;
  private final ConfigKeeper<MainSection> config;
  private final Plugin plugin;
  private final Pipes pipesMechanic;

  private final Map<UUID, EnumerationSession<?>> enumerationSessionByPlayerId;

  public PipeSearchHandler(
    PistonPredicateRegistry predicateRegistry,
    SearchDisplayHandler searchDisplayHandler,
    CapacityDisplayHandler capacityDisplayHandler,
    CubeRenderer cubeRenderer,
    ConfigKeeper<MainSection> config,
    Plugin plugin
  ) {
    this.predicateRegistry = predicateRegistry;
    this.searchDisplayHandler = searchDisplayHandler;
    this.capacityDisplayHandler = capacityDisplayHandler;
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
        predicateRegistry,
        EnumSet.of(EnumerationBehavior.IGNORE_CHECK_VALVES),
        session -> handleEnumerationWarmup(session, player),
        session -> handleSearchCompletion(session, player, query)
      )
    ));
  }

  private void handleSearchCompletion(SearchSession session, Player player, @Nullable PredicateAndLanguage query) {
    enumerationSessionByPlayerId.remove(player.getUniqueId());

    if (!session.didEncounterPipeBlocks())
      return;

    if (handleWarningsAndGetIfEmpty(session, player))
      return;

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      var predicateString = query == null ? "/" : PlainStringifier.stringify(query.predicate(), true);

      var matches = new ArrayList<ItemAndSlot>();

      var resultCounter = 0;

      for (var searchedInventory : session.getSearchedInventories()) {
        var blockContents = searchedInventory.inventory().getStorageContents();

        for (var slotIndex = 0; slotIndex < blockContents.length; ++slotIndex) {
          var item = blockContents[slotIndex];

          if (item == null || item.getType().isAir())
            continue;

          ++resultCounter;

          if (query != null && !query.predicate().test(item))
            continue;

          matches.add(new ItemAndSlot(item, searchedInventory.block(), slotIndex + searchedInventory.slotOffset()));
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

      searchDisplayHandler.show(player, new SearchDisplayData(predicateString, displayData, null));
    });
  }

  public TriState handleCapacityCalculation(Player player, Block origin) {
    return handleEnumeration(player, () -> (
      new SearchSession(
        origin, pipesMechanic, plugin,
        predicateRegistry,
        // Ensure that all signs are loaded and cached within the piston-predicate-registry for later access
        EnumSet.of(EnumerationBehavior.IGNORE_CHECK_VALVES, EnumerationBehavior.LOAD_PISTON_SIGNS),
        session -> handleEnumerationWarmup(session, player),
        session -> handleCapacityCalculationCompletion(session, player)
      )
    ));
  }

  private void handleCapacityCalculationCompletion(SearchSession session, Player player) {
    enumerationSessionByPlayerId.remove(player.getUniqueId());

    if (!session.didEncounterPipeBlocks())
      return;

    if (handleWarningsAndGetIfEmpty(session, player))
      return;

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      var capacityByPredicate = new HashMap<String, StorageCapacity>();

      // TODO: Allow a predicate to be passed which then has to be contained in the activePredicate of the searchedInventory

      for (var searchedInventory : session.getSearchedInventories()) {
        var capacity = capacityByPredicate.computeIfAbsent(searchedInventory.getExpandedActivePredicateString(), StorageCapacity::new);
        var storageContents = searchedInventory.inventory().getStorageContents();

        var occupiedSlotCount = 0;

        for (ItemStack item : storageContents) {
          if (item == null || item.getType().isAir()) {
            ++capacity.vacantSlotCount;
            continue;
          }

          ++occupiedSlotCount;
        }

        capacity.occupiedSlotCount += occupiedSlotCount;
        capacity.addEntry(searchedInventory, occupiedSlotCount, storageContents.length);
      }

      var capacities = new ArrayList<>(capacityByPredicate.values());
      capacities.forEach(StorageCapacity::combineStorageBlocks);

      capacityDisplayHandler.show(player, new CapacityDisplayData(capacities, null));
    });
  }

  private boolean handleWarningsAndGetIfEmpty(SearchSession session, Player player) {
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

    if (session.getSearchedInventories().isEmpty()) {
      config.rootSection.playerMessages.commandPipePredicateSearchNoContainers.sendMessage(
        player,
        new InterpretationEnvironment()
          .withVariable("piston_count", session.getPistonCount())
          .withVariable("tube_count", session.getTubeCount())
      );

      return true;
    }

    return false;
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
