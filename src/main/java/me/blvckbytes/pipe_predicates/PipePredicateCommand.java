package me.blvckbytes.pipe_predicates;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.bbtweaks.pipes.CachedBlock;
import me.blvckbytes.bbtweaks.pipes.InvalidateCachedBlockEvent;
import me.blvckbytes.pipe_predicates.config.MainSection;
import me.blvckbytes.pipe_predicates.search.PredicateAndLabels;
import me.blvckbytes.pipe_predicates.search.cubes.CubeRenderer;
import me.blvckbytes.pipe_predicates.search.PipeSearchHandler;
import me.blvckbytes.item_predicate_parser.ItemPredicateParserPlugin;
import me.blvckbytes.item_predicate_parser.event.*;
import me.blvckbytes.item_predicate_parser.parse.ItemPredicateParseException;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.translation.keyed.DisjunctionKey;
import me.blvckbytes.syllables_matcher.NormalizedConstant;
import me.blvckbytes.syllables_matcher.TriState;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PipePredicateCommand implements CommandExecutor, TabCompleter, Listener {

  private final PredicateDataHandler dataHandler;
  private final PipeEventHandler pipeEventHandler;
  private final PipeSearchHandler pipeSearchHandler;
  private final ItemPredicateParserPlugin ipp;
  private final CubeRenderer cubeRenderer;
  private final ConfigKeeper<MainSection> config;
  private final Logger logger;

  private final NamespacedKey lockedFrameKey;

  public PipePredicateCommand(
    PredicateDataHandler dataHandler,
    PipeEventHandler pipeEventHandler,
    PipeSearchHandler pipeSearchHandler,
    ItemPredicateParserPlugin ipp,
    CubeRenderer cubeRenderer,
    ConfigKeeper<MainSection> config,
    Plugin plugin
  ) {
    this.dataHandler = dataHandler;
    this.pipeEventHandler = pipeEventHandler;
    this.pipeSearchHandler = pipeSearchHandler;
    this.ipp = ipp;
    this.cubeRenderer = cubeRenderer;
    this.config = config;
    this.logger = plugin.getLogger();

    this.lockedFrameKey = new NamespacedKey(plugin, "locked-frame");
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    if (!(sender instanceof Player player))
      return false;

    if (!CommandAction.canExecuteAny(sender)) {
      config.rootSection.playerMessages.missingPermissionPipePredicateCommand.sendMessage(sender);
      return false;
    }

    var actionFilter = CommandAction.getPermissionFilterFor(sender);
    NormalizedConstant<CommandAction> normalizedAction;

    if (args.length == 0 || (normalizedAction = CommandAction.matcher.matchFirst(args[0], actionFilter)) == null) {
      config.rootSection.playerMessages.commandPipePredicateUsage.sendMessage(
        player,
        new InterpretationEnvironment()
          .withVariable("label", label)
          .withVariable("action_names", CommandAction.matcher.createCompletions(null, actionFilter))
      );
      return true;
    }

    // Keep these aliases around, as they're already broadly known on our server
    switch (normalizedAction.constant) {
      case SET, SET_LANGUAGE, GET, REMOVE:
        return ipp.getMainCommand().getExecutor().onCommand(sender, command, label, args);
    }

    if (normalizedAction.constant == CommandAction.GENERATE) {
      var pistonBlock = BlockUtility.resolvePistonBlock(resolveFacedTargetBlock(player));

      if (pistonBlock == null || !(pistonBlock.getBlockData() instanceof Directional directional)) {
        config.rootSection.playerMessages.commandPipePredicateNotLookingAtPipe.sendMessage(player);
        return true;
      }

      if (!(pistonBlock.getRelative(directional.getFacing()).getState() instanceof Container container)) {
        config.rootSection.playerMessages.commandPipePredicateGenerateNoContainer.sendMessage(player);
        return true;
      }

      var allowInitialize = PluginPermission.AUTO_INITIALIZE_SIGNS.has(player);
      var pistonSign = BlockUtility.getPistonSign(pistonBlock, allowInitialize);

      if (pistonSign == null) {
        config.rootSection.playerMessages.commandPipePredicateGenerateNoSign.sendMessage(player);
        return true;
      }

      if (!pipeEventHandler.canEditSign(player, pistonSign)) {
        config.rootSection.playerMessages.commandPipePredicateCannotBuild.sendMessage(player);
        return true;
      }

      var predicateHelper = ipp.getPredicateHelper();
      var targetLanguage = predicateHelper.getSelectedLanguage(player);
      var translationRegistry = ipp.getTranslationLanguageRegistry().getTranslationRegistry(targetLanguage);

      var containedMaterials = new HashSet<Material>();

      for (var storedItem : container.getInventory().getStorageContents()) {
        if (storedItem != null && !storedItem.getType().isAir())
          containedMaterials.add(storedItem.getType());
      }

      if (containedMaterials.isEmpty()) {
        config.rootSection.playerMessages.commandPipePredicateGenerateEmptyContainer.sendMessage(player);
        return true;
      }

      var sortedMaterials = new ArrayList<>(containedMaterials);
      sortedMaterials.sort(Comparator.comparingInt(Enum::ordinal));

      var orTranslation = translationRegistry.getNormalizedPrefixedTranslationBySingleton(DisjunctionKey.INSTANCE);

      if (orTranslation == null)
        throw new IllegalStateException("Could not locate translation for the OR operator in language " + targetLanguage);

      var predicateJoiner = new StringJoiner(" " + orTranslation + " ");

      for (var material : sortedMaterials) {
        var materialTranslation = translationRegistry.getNormalizedPrefixedTranslationBySingleton(material);

        if (materialTranslation == null)
          throw new IllegalStateException("Could not locate translation for " + material + " in language " + targetLanguage);

        predicateJoiner.add(materialTranslation);
      }

      var predicateString = predicateJoiner.toString();

      ItemPredicate predicate;

      try {
        var tokens = predicateHelper.parseTokens(predicateString);
        predicate = predicateHelper.parsePredicate(targetLanguage, tokens);
      } catch (ItemPredicateParseException e) {
        throw new IllegalStateException("Could not parse the predicate, despite it having been auto-generated");
      }

      setPredicate(pistonSign, new PredicateAndLanguage(predicate, targetLanguage));

      config.rootSection.playerMessages.commandPipePredicateGeneratePredicateSet.sendMessage(
        player,
        new InterpretationEnvironment()
          .withVariable("predicate", predicateString)
          .withVariable("set_command", "/" + label + " " + CommandAction.matcher.getNormalizedName(CommandAction.SET) + " " + predicateString)
          .withVariable("sign_x", pistonSign.getX())
          .withVariable("sign_y", pistonSign.getY())
          .withVariable("sign_z", pistonSign.getZ())
      );

      return true;
    }

    if (normalizedAction.constant == CommandAction.RELOAD) {
      try {
        this.config.reload();

        config.rootSection.playerMessages.commandPipePredicateReloadSuccess.sendMessage(sender);
      } catch (Exception e) {
        logger.log(Level.SEVERE, "An error occurred while trying to reload the config", e);

        config.rootSection.playerMessages.commandPipePredicateReloadFailure.sendMessage(sender);
      }

      return true;
    }

    if (normalizedAction.constant == CommandAction.CLEAR_VISUALIZE) {
      if (!cubeRenderer.removeAll(player)) {
        config.rootSection.playerMessages.commandPipePredicateVisualizeNoVisualization.sendMessage(player);
        return true;
      }

      config.rootSection.playerMessages.commandPipePredicateVisualizeClearedVisualization.sendMessage(player);
      return true;
    }

    if (normalizedAction.constant == CommandAction.VISUALIZE) {
      // TODO: Idea: flags for excluding pistons, excluding glass
      //       if everything is excluded, print "no results"
      var targetBlock = resolveFacedTargetBlock(player);

      if (!pipeEventHandler.canBuildAt(player, targetBlock)) {
        config.rootSection.playerMessages.commandPipePredicateCannotBuild.sendMessage(player);
        return true;
      }

      var searchResult = pipeSearchHandler.handleVisualization(player, targetBlock);

      if (searchResult == TriState.FALSE) {
        config.rootSection.playerMessages.commandPipePredicateNotLookingAtPipe.sendMessage(player);
        return true;
      }

      return true;
    }

    if (normalizedAction.constant == CommandAction.LOCK_FRAMES || normalizedAction.constant == CommandAction.UNLOCK_FRAMES) {
      var targetBlock = resolveFacedTargetBlock(player);

      if (!pipeEventHandler.canBuildAt(player, targetBlock)) {
        config.rootSection.playerMessages.commandPipePredicateCannotBuild.sendMessage(player);
        return true;
      }

      var lockFrames = normalizedAction.constant == CommandAction.LOCK_FRAMES;
      var lockResult = pipeSearchHandler.handleFrameLocking(player, targetBlock, lockFrames, lockedFrameKey);

      if (lockResult == TriState.FALSE) {
        config.rootSection.playerMessages.commandPipePredicateNotLookingAtPipe.sendMessage(player);
        return true;
      }

      return true;
    }

    if (normalizedAction.constant == CommandAction.CAPACITIES) {
      ItemPredicate containedPredicate = null;

      if (args.length > 1) {
        var predicateAndLanguage = tryParsePredicateAndLanguage(player, args);

        if (predicateAndLanguage == null)
          return true;

        containedPredicate = predicateAndLanguage.predicate;
      }

      var targetBlock = resolveFacedTargetBlock(player);

      if (!pipeEventHandler.canBuildAt(player, targetBlock)) {
        config.rootSection.playerMessages.commandPipePredicateCannotBuild.sendMessage(player);
        return true;
      }

      var searchResult = pipeSearchHandler.handleCapacityCalculation(player, targetBlock, containedPredicate);

      if (searchResult == TriState.FALSE) {
        config.rootSection.playerMessages.commandPipePredicateNotLookingAtPipe.sendMessage(player);
        return true;
      }

      return true;
    }

    if (normalizedAction.constant == CommandAction.LOCATE_PREDICATES) {
      var containedBuilder = new StringJoiner(" ");

      for (var argsIndex = 1; argsIndex < args.length; ++argsIndex)
        containedBuilder.add(args[argsIndex]);

      var containedString = containedBuilder.toString();

      if (containedString.isBlank()) {
        config.rootSection.playerMessages.commandPipePredicateLocatePredicateEmptyQuery.sendMessage(player);
        return true;
      }

      var targetBlock = resolveFacedTargetBlock(player);

      if (!pipeEventHandler.canBuildAt(player, targetBlock)) {
        config.rootSection.playerMessages.commandPipePredicateCannotBuild.sendMessage(player);
        return true;
      }

      var searchResult = pipeSearchHandler.handleLocatePredicate(player, targetBlock, containedString);

      if (searchResult == TriState.FALSE) {
        config.rootSection.playerMessages.commandPipePredicateNotLookingAtPipe.sendMessage(player);
        return true;
      }

      return true;
    }

    if (normalizedAction.constant == CommandAction.SEARCH) {
      ItemPredicate predicate = null;

      if (args.length > 1) {
        var predicateAndLanguage = tryParsePredicateAndLanguage(player, args);

        if (predicateAndLanguage == null)
          return true;

        predicate = predicateAndLanguage.predicate;
      }

      var targetBlock = resolveFacedTargetBlock(player);

      if (!pipeEventHandler.canBuildAt(player, targetBlock)) {
        config.rootSection.playerMessages.commandPipePredicateCannotBuild.sendMessage(player);
        return true;
      }

      var predicateAndLabels = PredicateAndLabels.of(predicate);

      if (predicateAndLabels != null && !predicateAndLabels.labels.isEmpty()) {
        config.rootSection.playerMessages.commandPipePredicateSearchLabelsAreUnsupported.sendMessage(player);
        return true;
      }

      var searchResult = pipeSearchHandler.handleSearch(player, targetBlock, predicate);

      if (searchResult == TriState.FALSE) {
        config.rootSection.playerMessages.commandPipePredicateNotLookingAtPipe.sendMessage(player);
        return true;
      }

      return true;
    }

    return true;
  }

  private Block resolveFacedTargetBlock(Player player) {
    var rayTraceResult = player.getWorld().rayTraceBlocks(
      player.getEyeLocation(),
      player.getEyeLocation().getDirection(),
      10.0,
      FluidCollisionMode.NEVER,
      true
    );

    if (rayTraceResult == null || rayTraceResult.getHitBlock() == null)
      return player.getEyeLocation().getBlock();

    var targetBlock = rayTraceResult.getHitBlock();

    // Handle pistons, signs on pistons as well as containers
    var pistonBlock = BlockUtility.resolvePistonBlock(targetBlock);

    if (pistonBlock != null)
      return pistonBlock;

    // Handle signs on tube-blocks, possibly with one wall-block in-between (as that allows
    // to make things look nicer for shortcut-commands bound to signs on walls, behind which
    // a pipe is running along).

    var blockData = targetBlock.getBlockData();
    var signMountingFace = BlockFace.SELF;

    if (blockData instanceof WallSign wallSign)
      signMountingFace = wallSign.getFacing().getOppositeFace();
    else if (blockData instanceof org.bukkit.block.data.type.Sign)
      signMountingFace = BlockFace.DOWN;

    if (signMountingFace != BlockFace.SELF) {
      targetBlock = targetBlock.getRelative(signMountingFace);

      if (!CachedBlock.isValidPipeBlock(CachedBlock.fromBlock(targetBlock))) {
        var nextBlockInDirection = targetBlock.getRelative(signMountingFace);

        if (CachedBlock.isValidPipeBlock(CachedBlock.fromBlock(nextBlockInDirection)))
          targetBlock = nextBlockInDirection;
      }
    }

    return targetBlock;
  }

  @Override
  public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    if (!(sender instanceof Player player))
      return null;

    if (!CommandAction.canExecuteAny(sender))
      return null;

    var actionFilter = CommandAction.getPermissionFilterFor(sender);

    if (args.length == 1)
      return CommandAction.matcher.createCompletions(args[0], actionFilter);

    var normalizedAction = CommandAction.matcher.matchFirst(args[0], actionFilter);

    if (normalizedAction == null)
      return null;

    // Keep these aliases around, as they're already broadly known on our server
    switch (normalizedAction.constant) {
      case SET, SET_LANGUAGE, GET, REMOVE: {
        if (ipp.getMainCommand().getExecutor() instanceof TabCompleter tabCompleter)
          return tabCompleter.onTabComplete(sender, command, label, args);
      }
    }

    if (normalizedAction.constant != CommandAction.SEARCH && normalizedAction.constant != CommandAction.CAPACITIES)
      return null;

    var predicateHelper = ipp.getPredicateHelper();
    var language = predicateHelper.getSelectedLanguage(player);

    try {
      var tokens = predicateHelper.parseTokens(args, 1);
      var completions = predicateHelper.createCompletion(language, tokens);

      if (completions.expandedPreviewOrError() != null)
        player.sendActionBar(completions.expandedPreviewOrError());

      return completions.suggestions();
    } catch (ItemPredicateParseException e) {
      player.sendActionBar(predicateHelper.createExceptionMessage(e));
      return null;
    }
  }

  @EventHandler(priority = EventPriority.LOW)
  public void onInteractAtEntity(PlayerInteractEntityEvent event) {
    var frame = getFrameIfLocked(event.getRightClicked());

    if (frame == null)
      return;

    event.setCancelled(true);

    var frameFace = frame.getAttachedFace();
    var mountedOnBlock = frame.getLocation().getBlock().getRelative(frameFace);

    if (!(mountedOnBlock.getState() instanceof Container container))
      return;

    event.getPlayer().openInventory(container.getInventory());
  }

  @EventHandler
  public void onHangingBreak(HangingBreakEvent event) {
    var cause = event.getCause();

    // Let it still be destroyed by physics/obstruction, as to not end up
    // with forever-hanging-in-air item-frames.
    if (cause != HangingBreakEvent.RemoveCause.ENTITY && cause != HangingBreakEvent.RemoveCause.EXPLOSION)
      return;

    if (getFrameIfLocked(event.getEntity()) != null)
      event.setCancelled(true);
  }

  @EventHandler
  public void onDamageByEntity(EntityDamageByEntityEvent event) {
    if (getFrameIfLocked(event.getEntity()) != null)
      event.setCancelled(true);
  }

  @EventHandler
  public void onDamage(EntityDamageEvent event) {
    if (getFrameIfLocked(event.getEntity()) != null)
      event.setCancelled(true);
  }

  private @Nullable ItemFrame getFrameIfLocked(Entity entity) {
    if (!(entity instanceof ItemFrame frame))
      return null;

    var lockFlag = frame.getPersistentDataContainer().get(lockedFrameKey, PersistentDataType.BOOLEAN);

    if (lockFlag == null || !lockFlag)
      return null;

    return frame;
  }

  @EventHandler
  public void onInteract(PlayerInteractEvent event) {
    var clickedBlock = event.getClickedBlock();

    if (clickedBlock == null)
      return;

    if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !(clickedBlock.getState() instanceof Sign sign))
      return;

    if (!sign.getLine(1).equalsIgnoreCase(MarkerConstants.PIPE_MARKER))
      return;

    if (dataHandler.access(sign) == null)
      return;

    event.setCancelled(true);
    config.rootSection.playerMessages.manualEditWhileInPredicateMode.sendMessage(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPredicateGet(PredicateGetEvent event) {
    var sign = tryResolveSignFromEventAndAcknowledge(event);

    if (sign == null)
      return;

    var predicateData = dataHandler.access(sign);

    if (predicateData != null) {
      if (predicateData.parsedPredicate() != null) {
        event.setResult(new PredicateAndLanguage(predicateData.parsedPredicate(), predicateData.predicateLanguage()));
        return;
      }

      if (predicateData.parseException() != null)
        event.setError(predicateData.parseException());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPredicateRemove(PredicateRemoveEvent event) {
    var sign = tryResolveSignFromEventAndAcknowledge(event);

    if (sign == null)
      return;

    var predicateData = dataHandler.remove(sign);

    if (predicateData != null ) {
      predicateData.restoreLines(sign);

      if (predicateData.parsedPredicate() != null)
        event.setRemovedPredicate(new PredicateAndLanguage(predicateData.parsedPredicate(), predicateData.predicateLanguage()));

      Bukkit.getPluginManager().callEvent(new InvalidateCachedBlockEvent(sign.getBlock()));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPredicateSet(PredicateSetEvent event) {
    var sign = tryResolveSignFromEventAndAcknowledge(event);

    if (sign == null)
      return;

    setPredicate(sign, event.getValue());
  }

  private void setPredicate(Sign sign, PredicateAndLanguage predicateAndLanguage) {
    var oldPredicateData = dataHandler.access(sign);
    PredicateData newPredicateData;

    if (oldPredicateData == null)
      newPredicateData = PredicateData.makeInitial(predicateAndLanguage.predicate, predicateAndLanguage.language, sign);
    else
      newPredicateData = PredicateData.makeUpdate(predicateAndLanguage.predicate, predicateAndLanguage.language, oldPredicateData);

    sign.setLine(0, "§" + MarkerConstants.PREDICATE_OK_COLOR + MarkerConstants.PREDICATE_MARKER);
    sign.setLine(1, MarkerConstants.PIPE_MARKER);
    sign.setLine(2, "");
    sign.setLine(3, "");

    // This call already saves the sign, so don't invoke saving twice
    dataHandler.store(newPredicateData, sign);

    Bukkit.getPluginManager().callEvent(new InvalidateCachedBlockEvent(sign.getBlock()));
  }

  private @Nullable Sign tryResolveSignFromEventAndAcknowledge(PredicateEvent predicateEvent) {
    // We're using the highest priority as to let other handlers take precedence - if somebody
    // responded already, that means that the piston's output-block is a predicate-keeper itself
    // and the piston-predicate may only be accessed by interacting with said piston directly.
    if (predicateEvent.isAcknowledged())
      return null;

    var pistonBlock = BlockUtility.resolvePistonBlock(predicateEvent.getBlock());

    if (pistonBlock == null)
      return null;

    var allowInitialize = PluginPermission.AUTO_INITIALIZE_SIGNS.has(predicateEvent.getPlayer());
    var pistonSign = BlockUtility.getPistonSign(pistonBlock, allowInitialize);

    if (pistonSign == null)
      return null;

    predicateEvent.acknowledge();

    if (!pipeEventHandler.canEditSign(predicateEvent.getPlayer(), pistonSign)) {
      predicateEvent.setDeniedAccessBlock(pistonSign.getBlock());
      return null;
    }

    predicateEvent.setDataHoldingBlock(pistonSign.getBlock());

    return pistonSign;
  }

  private @Nullable PredicateAndLanguage tryParsePredicateAndLanguage(Player executor, String[] args) {
    var predicateHelper = ipp.getPredicateHelper();
    var language = predicateHelper.getSelectedLanguage(executor);

    ItemPredicate predicate;

    try {
      var tokens = predicateHelper.parseTokens(args, 1);
      predicate = predicateHelper.parsePredicate(language, tokens);
    } catch (ItemPredicateParseException e) {
      config.rootSection.playerMessages.commandPipePredicatePredicateError.sendMessage(
        executor,
        new InterpretationEnvironment()
          .withVariable("error_message", predicateHelper.createExceptionMessage(e))
      );

      return null;
    }

    if (predicate == null) {
      config.rootSection.playerMessages.commandPipePredicateEmptyPredicate.sendMessage(executor);
      return null;
    }

    return new PredicateAndLanguage(predicate, language);
  }
}
