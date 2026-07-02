package me.blvckbytes.pipe_predicates.search;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.bbtweaks.BBTweaksPlugin;
import me.blvckbytes.bbtweaks.pipes.mechanic.EnumerationBehavior;
import me.blvckbytes.bbtweaks.pipes.mechanic.PipesApi;
import me.blvckbytes.bbtweaks.pipes.mechanic.TubeColor;
import me.blvckbytes.pipe_predicates.CaseInsensitiveSet;
import me.blvckbytes.pipe_predicates.PipeTeleportCommand;
import me.blvckbytes.pipe_predicates.PistonPredicateRegistry;
import me.blvckbytes.pipe_predicates.config.ContainerCount;
import me.blvckbytes.pipe_predicates.config.MainSection;
import me.blvckbytes.pipe_predicates.search.display.capacity.CapacityInfo;
import me.blvckbytes.pipe_predicates.search.display.capacity.CapacityDisplayData;
import me.blvckbytes.pipe_predicates.search.display.capacity.CapacityDisplayHandler;
import me.blvckbytes.pipe_predicates.search.display.search.ItemCollectionEntry;
import me.blvckbytes.pipe_predicates.search.display.search.SearchDisplayData;
import me.blvckbytes.pipe_predicates.search.display.search.SearchDisplayHandler;
import me.blvckbytes.pipe_predicates.search.cubes.CubeRenderer;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.predicate.stringify.PlainStringifier;
import me.blvckbytes.syllables_matcher.TriState;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
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

  private final PipeTeleportCommand teleportCommand;
  private final PistonPredicateRegistry predicateRegistry;
  private final SearchDisplayHandler searchDisplayHandler;
  private final CapacityDisplayHandler capacityDisplayHandler;
  private final CubeRenderer cubeRenderer;
  private final ConfigKeeper<MainSection> config;
  private final Plugin plugin;
  private final PipesApi pipesApi;

  private final Map<UUID, EnumerationSession<?>> enumerationSessionByPlayerId;

  public PipeSearchHandler(
    PipeTeleportCommand teleportCommand,
    PistonPredicateRegistry predicateRegistry,
    SearchDisplayHandler searchDisplayHandler,
    CapacityDisplayHandler capacityDisplayHandler,
    CubeRenderer cubeRenderer,
    ConfigKeeper<MainSection> config,
    Plugin plugin
  ) {
    this.teleportCommand = teleportCommand;
    this.predicateRegistry = predicateRegistry;
    this.searchDisplayHandler = searchDisplayHandler;
    this.capacityDisplayHandler = capacityDisplayHandler;
    this.cubeRenderer = cubeRenderer;
    this.config = config;
    this.plugin = plugin;

    var bbtweaksPlugin = Bukkit.getPluginManager().getPlugin("BBTweaks");

    if (bbtweaksPlugin == null || !bbtweaksPlugin.isEnabled())
      throw new IllegalStateException("Expected the BBTweaks-plugin to be loaded and enabled");

    this.pipesApi = ((BBTweaksPlugin) bbtweaksPlugin).getPipesApi();

    if (pipesApi == null)
      throw new IllegalStateException("Expected the PipesAPI of the BBTweaks-plugin to be available");

    this.enumerationSessionByPlayerId = new HashMap<>();
  }

  public TriState handleFrameLocking(Player player, Block origin, boolean lockFrames, NamespacedKey lockedFrameKey) {
    return handleEnumeration(player, () -> (
      new FrameLockSession(
        origin, pipesApi, plugin,
        session -> handleEnumerationWarmup(session, player),
        session -> handleFrameLockingCompletion(session, player),
        lockFrames, lockedFrameKey
      )
    ));
  }

  private void handleFrameLockingCompletion(FrameLockSession session, Player player) {
    enumerationSessionByPlayerId.remove(player.getUniqueId());

    if (!session.didEncounterPipeBlocks())
      return;

    if (session.getLockChangeCount() <= 0) {
      if (session.isLockFrames()) {
        config.rootSection.playerMessages.commandPipePredicateFrameLockAllLocked.sendMessage(player);
        return;
      }

      config.rootSection.playerMessages.commandPipePredicateFrameLockAllUnlocked.sendMessage(player);
      return;
    }

    var environment = new InterpretationEnvironment().withVariable("lock_count", session.getLockChangeCount());

    if (session.isLockFrames()) {
      config.rootSection.playerMessages.commandPipePredicateFrameLockDidLock.sendMessage(player, environment);
      return;
    }

    config.rootSection.playerMessages.commandPipePredicateFrameLockDidUnlock.sendMessage(player, environment);
  }

  public TriState handleLocatePredicate(Player player, Block origin, String containedString) {
    return handleEnumeration(player, () -> (
      new PredicateLocateSession(
        origin, pipesApi, plugin,
        predicateRegistry,
        session -> handleEnumerationWarmup(session, player),
        session -> handleLocatePredicateCompletion(session, player, containedString)
      )
    ));
  }

  private void handleLocatePredicateCompletion(PredicateLocateSession session, Player player, String containedString) {
    enumerationSessionByPlayerId.remove(player.getUniqueId());

    if (!session.didEncounterPipeBlocks())
      return;

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      var containedStringLower = containedString.toLowerCase();
      var results = new ArrayList<PredicateAndPiston>();

      for (var resultItem : session.result) {
        if (resultItem.predicateString.toLowerCase().contains(containedStringLower)) {
          teleportCommand.temporarilyAllow(player, resultItem.pistonBlock.getLocation());
          results.add(resultItem);
        }
      }

      if (results.isEmpty()) {
        config.rootSection.playerMessages.commandPipePredicateLocatePredicateNoMatches.sendMessage(
          player,
          new InterpretationEnvironment()
            .withVariable("query", containedString)
        );

        return;
      }

      config.rootSection.playerMessages.commandPipePredicateLocatePredicateMatches.sendMessage(
        player,
        new InterpretationEnvironment()
          .withVariable("query", containedString)
          .withVariable("results", results)
      );
    });
  }

  public TriState handleSearch(Player player, Block origin, @Nullable ItemPredicate predicate) {
    return handleEnumeration(player, () -> (
      new SearchSession(
        origin, pipesApi, plugin,
        predicateRegistry,
        // Ensure that all signs are loaded and cached within the piston-predicate-registry for later access
        EnumSet.of(EnumerationBehavior.IGNORE_CHECK_VALVES, EnumerationBehavior.LOAD_PISTON_SIGNS),
        session -> handleEnumerationWarmup(session, player),
        session -> handleSearchCompletion(session, player, predicate)
      )
    ));
  }

  private void handleSearchCompletion(SearchSession session, Player player, @Nullable ItemPredicate predicate) {
    enumerationSessionByPlayerId.remove(player.getUniqueId());

    if (!session.didEncounterPipeBlocks())
      return;

    if (handleWarningsAndGetIfEmpty(session, player))
      return;

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      var matches = new ArrayList<ItemAndSlot>();

      var resultCounter = 0;

      for (var searchedInventory : session.getSearchedInventories()) {
        var blockContents = searchedInventory.inventory.getStorageContents();

        for (var slotIndex = 0; slotIndex < blockContents.length; ++slotIndex) {
          var item = blockContents[slotIndex];

          if (item == null || item.getType().isAir())
            continue;

          ++resultCounter;

          if (predicate != null && !predicate.test(item))
            continue;

          matches.add(new ItemAndSlot(item, searchedInventory.block, slotIndex + searchedInventory.slotOffset));
        }
      }

      var containerCounts = new ArrayList<ContainerCount>();
      var totalContainerCount = session.forEachContainerCountAndGetSum((material, amount) -> containerCounts.add(new ContainerCount(material, amount)));

      var environment = new InterpretationEnvironment()
        .withVariable("predicate", predicate == null ? "/" : PlainStringifier.stringify(predicate, true))
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

      searchDisplayHandler.show(player, new SearchDisplayData(predicate, displayData, null));
    });
  }

  public TriState handleCapacityCalculation(Player player, Block origin, @Nullable ItemPredicate containedPredicate) {
    return handleEnumeration(player, () -> (
      new SearchSession(
        origin, pipesApi, plugin,
        predicateRegistry,
        // Ensure that all signs are loaded and cached within the piston-predicate-registry for later access.
        EnumSet.of(EnumerationBehavior.LOAD_PISTON_SIGNS, EnumerationBehavior.DEPTH_FIRST),
        session -> handleEnumerationWarmup(session, player),
        session -> handleCapacityCalculationCompletion(session, player, containedPredicate)
      )
    ));
  }

  private void handleCapacityCalculationCompletion(SearchSession session, Player player, @Nullable ItemPredicate containedPredicate) {
    enumerationSessionByPlayerId.remove(player.getUniqueId());

    if (!session.didEncounterPipeBlocks())
      return;

    if (handleWarningsAndGetIfEmpty(session, player))
      return;

    var query = PredicateAndLabels.of(containedPredicate);

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      var capacityByPredicate = new HashMap<String, StorageCapacity>();
      var encounteredLabelValues = new CaseInsensitiveSet();

      for (var searchedInventory : session.getSearchedInventories()) {
        encounteredLabelValues.addAll(searchedInventory.getLabelValues());

        if (query != null) {
          if (!searchedInventory.isLabelled(query) || !searchedInventory.containsOrEqualsPredicate(query))
            continue;
        }

        var capacity = capacityByPredicate.computeIfAbsent(searchedInventory.getNearestActivePredicateString(), StorageCapacity::new);
        var storageContents = searchedInventory.inventory.getStorageContents();

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

      var capacityInfo = new CapacityInfo(query, capacities, encounteredLabelValues);

      capacityDisplayHandler.show(player, new CapacityDisplayData(capacityInfo));
    });
  }

  private boolean handleWarningsAndGetIfEmpty(SearchSession session, Player player) {
    if (session.hasFlag(SearchResultFlag.EXCEEDED_MAX_TUBE_COUNT)) {
      config.rootSection.playerMessages.commandPipePredicateSearchExceededPipes.sendMessage(
        player,
        new InterpretationEnvironment()
          .withVariable("limit", pipesApi.getMaxTubeBlockCount())
      );
    }

    if (session.hasFlag(SearchResultFlag.EXCEEDED_MAX_PISTON_COUNT)) {
      config.rootSection.playerMessages.commandPipePredicateSearchExceededPistons.sendMessage(
        player,
        new InterpretationEnvironment()
          .withVariable("limit", pipesApi.getMaxPistonBlockCount())
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
        origin, pipesApi, plugin,
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
