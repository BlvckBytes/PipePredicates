package me.blvckbytes.craft_book_pipe_predicates;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import com.sk89q.craftbook.mechanics.pipe.CachedBlock;
import com.sk89q.craftbook.mechanics.pipe.InvalidateCachedBlockEvent;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.cubes.CubeRenderer;
import me.blvckbytes.craft_book_pipe_predicates.search.PipeSearchHandler;
import me.blvckbytes.item_predicate_parser.PredicateHelper;
import me.blvckbytes.item_predicate_parser.parse.ItemPredicateParseException;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.predicate.StringifyState;
import me.blvckbytes.item_predicate_parser.translation.TranslationLanguage;
import me.blvckbytes.syllables_matcher.NormalizedConstant;
import me.blvckbytes.syllables_matcher.TriState;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
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
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PipePredicateCommand implements CommandExecutor, TabCompleter, Listener {

  private record SignAndPiston(@Nullable Sign sign, Block piston) {}

  private final PredicateDataHandler dataHandler;
  private final PipeEventHandler pipeEventHandler;
  private final PipeSearchHandler pipeSearchHandler;
  private final PredicateHelper predicateHelper;
  private final CubeRenderer cubeRenderer;
  private final ConfigKeeper<MainSection> config;
  private final Logger logger;

  private final Map<UUID, InteractionSession> interactionSessionByPlayerId;
  private final NamespacedKey lockedFrameKey;

  public PipePredicateCommand(
    PredicateDataHandler dataHandler,
    PipeEventHandler pipeEventHandler,
    PipeSearchHandler pipeSearchHandler,
    PredicateHelper predicateHelper,
    CubeRenderer cubeRenderer,
    ConfigKeeper<MainSection> config,
    Plugin plugin
  ) {
    this.dataHandler = dataHandler;
    this.pipeEventHandler = pipeEventHandler;
    this.pipeSearchHandler = pipeSearchHandler;
    this.predicateHelper = predicateHelper;
    this.cubeRenderer = cubeRenderer;
    this.config = config;
    this.logger = plugin.getLogger();

    this.interactionSessionByPlayerId = new HashMap<>();
    this.lockedFrameKey = new NamespacedKey(plugin, "locked-frame");
  }

  public void tickSessions() {
    for (var iterator = interactionSessionByPlayerId.values().iterator(); iterator.hasNext();) {
      var session = iterator.next();

      if (session.isExpired()) {
        iterator.remove();
        config.rootSection.playerMessages.commandPipePredicateInteractExpired.sendMessage(session.player);

        if (session.allowMultiUse)
          showActionBarMessage(session.player, ""); // Immediately clear action-bar signal

        continue;
      }

      if (session.allowMultiUse)
        config.rootSection.playerMessages.commandPipePredicateInteractMultiActionBarSignal.sendActionBar(session.player);
    }
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

    if (normalizedAction.constant == CommandAction.LOCK_FRAMES) {
      config.rootSection.playerMessages.commandPipePredicateFrameLockInit.sendMessage(sender);

      interactionSessionByPlayerId.put(player.getUniqueId(), new FrameLockSession(player, true));
      return true;
    }

    if (normalizedAction.constant == CommandAction.UNLOCK_FRAMES) {
      config.rootSection.playerMessages.commandPipePredicateFrameUnlockInit.sendMessage(sender);

      interactionSessionByPlayerId.put(player.getUniqueId(), new FrameLockSession(player, false));
      return true;
    }

    if (normalizedAction.constant == CommandAction.SEARCH) {
      PredicateAndLanguage predicateAndLanguage = null;

      if (args.length > 1) {
        predicateAndLanguage = tryParsePredicateAndLanguage(player, args, true);

        if (predicateAndLanguage == null)
          return true;
      }

      var targetBlock = resolveFacedTargetBlock(player);

      if (!pipeEventHandler.canBuildAt(player, targetBlock)) {
        config.rootSection.playerMessages.commandPipePredicateCannotBuild.sendMessage(player);
        return true;
      }

      var searchResult = pipeSearchHandler.handleSearch(player, targetBlock, predicateAndLanguage);

      if (searchResult == TriState.FALSE) {
        config.rootSection.playerMessages.commandPipePredicateNotLookingAtPipe.sendMessage(player);
        return true;
      }

      return true;
    }

    switch (normalizedAction.constant) {
      case REMOVE -> {
        config.rootSection.playerMessages.commandPipePredicateRemoveInit.sendMessage(sender);

        interactionSessionByPlayerId.put(player.getUniqueId(), new PredicateSetSession(player, null));
        return true;
      }

      case GET -> {
        config.rootSection.playerMessages.commandPipePredicateGetInit.sendMessage(sender);

        interactionSessionByPlayerId.put(player.getUniqueId(), new PredicateGetSession(player));
        return true;
      }

      case SET, SET_LANGUAGE -> {
        var predicateAndLanguage = tryParsePredicateAndLanguage(player, args, normalizedAction.constant == CommandAction.SET);

        if (predicateAndLanguage == null)
          return true;

        config.rootSection.playerMessages.commandPipePredicateSetInit.sendMessage(
          sender,
          new InterpretationEnvironment()
            .withVariable("predicate", new StringifyState(true).appendPredicate(predicateAndLanguage.predicate()).toString())
        );

        interactionSessionByPlayerId.put(player.getUniqueId(), new PredicateSetSession(player, predicateAndLanguage));
        return true;
      }

      default -> { return true; }
    }
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

    if (normalizedAction.constant != CommandAction.SET && normalizedAction.constant != CommandAction.SEARCH && normalizedAction.constant != CommandAction.SET_LANGUAGE)
      return null;

    TranslationLanguage language;
    int argsOffset;

    if (normalizedAction.constant == CommandAction.SET_LANGUAGE) {
      if (args.length == 2)
        return TranslationLanguage.matcher.createCompletions(args[1]);

      var languageSelection = TranslationLanguage.matcher.matchFirst(args[1]);

      if (languageSelection == null)
        return null;

      language = languageSelection.constant;
      argsOffset = 2;
    }

    else {
      language = predicateHelper.getSelectedLanguage(player);
      argsOffset = 1;
    }

    try {
      var tokens = predicateHelper.parseTokens(args, argsOffset);
      var completions = predicateHelper.createCompletion(language, tokens);

      if (completions.expandedPreviewOrError() != null)
        showActionBarMessage(player, completions.expandedPreviewOrError());

      return completions.suggestions();
    } catch (ItemPredicateParseException e) {
      showActionBarMessage(player, predicateHelper.createExceptionMessage(e));
      return null;
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    interactionSessionByPlayerId.remove(event.getPlayer().getUniqueId());
  }

  @EventHandler
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
    if (!(event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK))
      return;

    if(event.getHand() == EquipmentSlot.OFF_HAND)
      return;

    var player = event.getPlayer();
    var clickedBlock = event.getClickedBlock();

    if (clickedBlock == null)
      return;

    if (handleInteractionSessionAndGetIfCancel(player, clickedBlock)) {
      event.setCancelled(true);
      return;
    }

    // If there was no cancellation, and we're about to edit a sign, check if it's in predicate-mode, as
    // opening an editor for a locked sign (predicate-signs cannot be manually added) will frustrate the user.

    if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !(clickedBlock.getState() instanceof Sign sign))
      return;

    if (!sign.getLine(1).equalsIgnoreCase(MarkerConstants.PIPE_MARKER))
      return;

    if (dataHandler.access(sign) == null)
      return;

    event.setCancelled(true);
    config.rootSection.playerMessages.manualEditWhileInPredicateMode.sendMessage(event.getPlayer());
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    var player = event.getPlayer();

    if (handleInteractionSessionAndGetIfCancel(player, event.getBlockAgainst()))
      event.setCancelled(true);
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    var player = event.getPlayer();

    if (handleInteractionSessionAndGetIfCancel(player, event.getBlock()))
      event.setCancelled(true);
  }

  @EventHandler
  public void onBucketEmpty(PlayerBucketEmptyEvent event) {
    var player = event.getPlayer();

    if (handleInteractionSessionAndGetIfCancel(player, event.getBlockClicked()))
      event.setCancelled(true);
  }

  @EventHandler
  public void onSneak(PlayerToggleSneakEvent event) {
    var player = event.getPlayer();

    if (!event.isSneaking())
      return;

    var interactionSession = interactionSessionByPlayerId.get(player.getUniqueId());

    if (interactionSession == null)
      return;

    if (!interactionSession.allowMultiUse) {
      interactionSession.allowMultiUse = true;
      interactionSession.touchExpiry();
      config.rootSection.playerMessages.commandPipePredicateInteractMultiEntered.sendMessage(player);
      return;
    }

    interactionSessionByPlayerId.remove(player.getUniqueId());
    showActionBarMessage(player, ""); // Immediately clear action-bar signal

    config.rootSection.playerMessages.commandPipePredicateInteractMultiExited.sendMessage(player);
  }

  private boolean handleInteractionSessionAndGetIfCancel(Player player, Block target) {
    var interactionSession = interactionSessionByPlayerId.get(player.getUniqueId());

    if (interactionSession == null)
      return false;

    if (!interactionSession.allowMultiUse)
      interactionSessionByPlayerId.remove(player.getUniqueId());

    var resolveResult = tryResolvePistonSignFromTargetBlock(player, target);

    if (resolveResult == null)
      return true;

    if (interactionSession.requiresSign() && resolveResult.sign() == null) {
      config.rootSection.playerMessages.commandPipePredicateNoSign.sendMessage(player);
      return true;
    }

    interactionSession.touchExpiry();

    if (interactionSession instanceof PredicateGetSession) {
      var pistonSign = Objects.requireNonNull(resolveResult.sign());
      var predicateData = dataHandler.access(pistonSign);

      if (predicateData == null) {
        config.rootSection.playerMessages.commandPipePredicateNoPredicate.sendMessage(player);
        return true;
      }

      if (predicateData.parseException() != null) {
        config.rootSection.playerMessages.commandPipePredicateGetError.sendMessage(
          player,
          new InterpretationEnvironment()
            .withVariable("predicate_error", predicateHelper.createExceptionMessage(predicateData.parseException()))
        );
        return true;
      }

      var predicateLanguageName = TranslationLanguage.matcher.getNormalizedName(predicateData.predicateLanguage());
      var predicateValue = predicateData.tokensPredicate();

      var setCommand = "/" + config.rootSection.commands.pipePredicate.getShortestNameOrAlias() + " ";

      if (predicateData.predicateLanguage() == predicateHelper.getSelectedLanguage(player)) {
        setCommand += CommandAction.matcher.getNormalizedName(CommandAction.SET) + " " + predicateValue;
      }
      else {
        setCommand += CommandAction.matcher.getNormalizedName(CommandAction.SET_LANGUAGE) + " " + predicateLanguageName + " " + predicateValue;
      }

      config.rootSection.playerMessages.commandPipePredicateGetPredicate.sendMessage(
        player,
        new InterpretationEnvironment()
          .withVariable("predicate", predicateValue)
          .withVariable("predicate_language", predicateLanguageName)
          .withVariable("set_command", setCommand)
      );

      return true;
    }

    if (interactionSession instanceof PredicateSetSession setSession) {
      var pistonSign = Objects.requireNonNull(resolveResult.sign());

      if (setSession.valueToSet == null) {
        PredicateData predicateData;

        if ((predicateData = dataHandler.remove(pistonSign)) == null) {
          config.rootSection.playerMessages.commandPipePredicateNoPredicate.sendMessage(player);
          return true;
        }

        predicateData.restoreLines(pistonSign);
        Bukkit.getPluginManager().callEvent(new InvalidateCachedBlockEvent(resolveResult.sign().getBlock()));

        config.rootSection.playerMessages.commandPipePredicateRemoveSuccess.sendMessage(
          player,
          new InterpretationEnvironment()
            .withVariable("predicate", predicateData.tokensPredicate())
        );

        return true;
      }

      var predicate = setSession.valueToSet.predicate();
      var language = setSession.valueToSet.language();
      var existingPredicateData = dataHandler.access(pistonSign);

      PredicateData newPredicateData;

      if (existingPredicateData != null)
        newPredicateData = PredicateData.makeUpdate(predicate, language, existingPredicateData);
      else
        newPredicateData = PredicateData.makeInitial(predicate, language, pistonSign);

      pistonSign.setLine(0, "§" + MarkerConstants.PREDICATE_OK_COLOR + MarkerConstants.PREDICATE_MARKER);
      pistonSign.setLine(1, MarkerConstants.PIPE_MARKER);
      pistonSign.setLine(2, "");
      pistonSign.setLine(3, "");

      // This call already saves the sign, so don't invoke saving twice
      dataHandler.store(newPredicateData, pistonSign);
      Bukkit.getPluginManager().callEvent(new InvalidateCachedBlockEvent(resolveResult.sign().getBlock()));

      config.rootSection.playerMessages.commandPipePredicateSetSuccess.sendMessage(
        player,
        new InterpretationEnvironment()
          .withVariable("predicate", new StringifyState(true).appendPredicate(predicate))
      );

      return true;
    }

    if (interactionSession instanceof FrameLockSession lockSession) {
      var pistonBlock = resolveResult.piston();
      var containerBlock = pistonBlock.getRelative(((Directional) pistonBlock.getBlockData()).getFacing());

      var foundUnlockedFrame = new AtomicBoolean();
      var foundLockedFrame = new AtomicBoolean();

      var frameCount = BlockUtility.forEachFrameOnContainer(containerBlock, frame -> {
        var pdc = frame.getPersistentDataContainer();
        var lockFlag = pdc.get(lockedFrameKey, PersistentDataType.BOOLEAN);

        if (lockFlag != null && lockFlag) {
          foundLockedFrame.set(true);

          if (!lockSession.lockOrUnlock)
            pdc.set(lockedFrameKey, PersistentDataType.BOOLEAN, false);

          return;
        }

        foundUnlockedFrame.set(true);

        if (lockSession.lockOrUnlock)
          pdc.set(lockedFrameKey, PersistentDataType.BOOLEAN, true);
      });

      if (frameCount == 0) {
        config.rootSection.playerMessages.commandPipePredicateFrameLockNoFrames.sendMessage(player);
        return true;
      }

      var environment = new InterpretationEnvironment()
        .withVariable("frame_count", frameCount);

      if (lockSession.lockOrUnlock) {
        if (!foundUnlockedFrame.get()) {
          config.rootSection.playerMessages.commandPipePredicateFrameLockAlreadyLocked.sendMessage(player, environment);
          return true;
        }

        config.rootSection.playerMessages.commandPipePredicateFrameLockFramesLocked.sendMessage(player, environment);
        return true;
      }

      if (!foundLockedFrame.get()) {
        config.rootSection.playerMessages.commandPipePredicateFrameLockAlreadyUnlocked.sendMessage(player, environment);
        return true;
      }

      config.rootSection.playerMessages.commandPipePredicateFrameLockFramesUnlocked.sendMessage(player, environment);
      return true;
    }

    return true;
  }

  private @Nullable Block tryResolvePistonFromTargetBlock(Player executor, Block target) {
    var pistonBlock = BlockUtility.resolvePistonBlock(target);

    if (pistonBlock == null) {
      config.rootSection.playerMessages.commandPipePredicateNoPiston.sendMessage(executor);
      return null;
    }

    return pistonBlock;
  }

  private @Nullable SignAndPiston tryResolvePistonSignFromTargetBlock(Player executor, Block target) {
    var pistonBlock = tryResolvePistonFromTargetBlock(executor, target);

    if (pistonBlock == null)
      return null;

    var allowInitialize = PluginPermission.AUTO_INITIALIZE_SIGNS.has(executor);
    var pistonSign = BlockUtility.getPistonSign(pistonBlock, allowInitialize);

    if (pistonSign == null)
      return new SignAndPiston(null, pistonBlock);

    if (!pipeEventHandler.canEditSign(executor, pistonSign)) {
      config.rootSection.playerMessages.commandPipePredicateCannotEditSign.sendMessage(executor);
      return null;
    }

    return new SignAndPiston(pistonSign, pistonBlock);
  }

  private @Nullable PredicateAndLanguage tryParsePredicateAndLanguage(Player executor, String[] args, boolean useSelectedLanguage) {
    int predicateArgsOffset;
    TranslationLanguage language;

    if (useSelectedLanguage) {
      language = predicateHelper.getSelectedLanguage(executor);
      predicateArgsOffset = 1;
    }

    else {
      if (args.length < 2) {
        config.rootSection.playerMessages.commandPipePredicateMissingLanguage.sendMessage(executor);
        return null;
      }

      var languageSelection = TranslationLanguage.matcher.matchFirst(args[1]);

      if (languageSelection == null) {
        config.rootSection.playerMessages.commandPipePredicateUnknownLanguage.sendMessage(
          executor,
          new InterpretationEnvironment()
            .withVariable("input", args[1])
        );
        return null;
      }

      language = languageSelection.constant;
      predicateArgsOffset = 2;
    }

    ItemPredicate predicate;

    try {
      var tokens = predicateHelper.parseTokens(args, predicateArgsOffset);
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

  @SuppressWarnings("deprecation")
  private void showActionBarMessage(Player player, String message) {
    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
  }
}
