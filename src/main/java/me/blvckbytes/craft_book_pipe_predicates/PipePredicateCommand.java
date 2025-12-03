package me.blvckbytes.craft_book_pipe_predicates;

import me.blvckbytes.bbconfigmapper.ScalarType;
import me.blvckbytes.bukkitevaluable.BukkitEvaluable;
import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
import me.blvckbytes.craft_book_pipe_predicates.search.PipeSearchHandler;
import me.blvckbytes.item_predicate_parser.PredicateHelper;
import me.blvckbytes.item_predicate_parser.parse.ItemPredicateParseException;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.predicate.StringifyState;
import me.blvckbytes.item_predicate_parser.translation.TranslationLanguage;
import me.blvckbytes.syllables_matcher.NormalizedConstant;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PipePredicateCommand implements CommandExecutor, TabCompleter, Listener {

  private final PredicateDataHandler dataHandler;
  private final PipeEventHandler pipeEventHandler;
  private final PipeSearchHandler pipeSearchHandler;
  private final PredicateHelper predicateHelper;
  private final ConfigKeeper<MainSection> config;
  private final Logger logger;

  private final Map<UUID, PredicateInteractionSession> interactionSessionByPlayerId;

  public PipePredicateCommand(
    PredicateDataHandler dataHandler,
    PipeEventHandler pipeEventHandler,
    PipeSearchHandler pipeSearchHandler,
    PredicateHelper predicateHelper,
    ConfigKeeper<MainSection> config,
    Logger logger
  ) {
    this.dataHandler = dataHandler;
    this.pipeEventHandler = pipeEventHandler;
    this.pipeSearchHandler = pipeSearchHandler;
    this.predicateHelper = predicateHelper;
    this.config = config;
    this.logger = logger;
    this.interactionSessionByPlayerId = new HashMap<>();
  }

  public void tickSessions() {
    for (var iterator = interactionSessionByPlayerId.values().iterator(); iterator.hasNext();) {
      var session = iterator.next();

      if (session.isExpired()) {
        iterator.remove();
        config.rootSection.playerMessages.commandPipePredicateInteractExpired.sendMessage(session.player, config.rootSection.builtBaseEnvironment);

        if (session.allowMultiUse)
          showActionBarMessage(session.player, ""); // Immediately clear action-bar signal

        continue;
      }

      if (session.allowMultiUse) {
        BukkitEvaluable message;

        if ((message = config.rootSection.playerMessages.commandPipePredicateInteractMultiActionBarSignal) != null)
          showActionBarMessage(session.player, message.asScalar(ScalarType.STRING, config.rootSection.builtBaseEnvironment));
      }
    }
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    if (!(sender instanceof Player player))
      return false;

    if (!CommandAction.canExecuteAny(sender)) {
      config.rootSection.playerMessages.missingPermissionPipePredicateCommand.sendMessage(sender, config.rootSection.builtBaseEnvironment);
      return false;
    }

    var actionFilter = CommandAction.getPermissionFilterFor(sender);
    NormalizedConstant<CommandAction> normalizedAction;

    if (args.length == 0 || (normalizedAction = CommandAction.matcher.matchFirst(args[0], actionFilter)) == null) {
      config.rootSection.playerMessages.commandPipePredicateUsage.sendMessage(
        player,
        config.rootSection.getBaseEnvironment()
          .withStaticVariable("label", label)
          .withStaticVariable("action_names", CommandAction.matcher.createCompletions(null, actionFilter))
          .build()
      );
      return true;
    }

    if (normalizedAction.constant == CommandAction.RELOAD) {
      if (!PluginPermission.PIPE_PREDICATE_COMMAND_RELOAD.has(sender)) {
        config.rootSection.playerMessages.missingPermissionPipePredicateReload.sendMessage(sender, config.rootSection.builtBaseEnvironment);
        return false;
      }

      try {
        this.config.reload();

        config.rootSection.playerMessages.commandPipePredicateReloadSuccess.sendMessage(sender, config.rootSection.builtBaseEnvironment);
      } catch (Exception e) {
        logger.log(Level.SEVERE, "An error occurred while trying to reload the config", e);

        config.rootSection.playerMessages.commandPipePredicateReloadFailure.sendMessage(sender, config.rootSection.builtBaseEnvironment);
      }

      return true;
    }

    if (normalizedAction.constant == CommandAction.SEARCH) {
      if (!PluginPermission.PIPE_PREDICATE_COMMAND_SEARCH.has(sender)) {
        config.rootSection.playerMessages.missingPermissionPipePredicateSearch.sendMessage(sender, config.rootSection.builtBaseEnvironment);
        return false;
      }

      var predicateAndLanguage = tryParsePredicateAndLanguage(player, args);

      if (predicateAndLanguage == null)
        return true;

      config.rootSection.playerMessages.commandPipePredicateSearchInit.sendMessage(
        sender,
        config.rootSection.getBaseEnvironment()
          .withStaticVariable("predicate", new StringifyState(true).appendPredicate(predicateAndLanguage.predicate()).toString())
          .build()
      );

      interactionSessionByPlayerId.put(player.getUniqueId(), new PredicateSearchSession(player, predicateAndLanguage));
      return true;
    }

    switch (normalizedAction.constant) {
      case REMOVE -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender)) {
          config.rootSection.playerMessages.missingPermissionPipePredicateModify.sendMessage(sender, config.rootSection.builtBaseEnvironment);
          return false;
        }

        config.rootSection.playerMessages.commandPipePredicateRemoveInit.sendMessage(sender, config.rootSection.builtBaseEnvironment);

        interactionSessionByPlayerId.put(player.getUniqueId(), new PredicateSetSession(player, null));
        return true;
      }

      case GET -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_READ.has(sender)) {
          config.rootSection.playerMessages.missingPermissionPipePredicateRead.sendMessage(sender, config.rootSection.builtBaseEnvironment);
          return false;
        }

        config.rootSection.playerMessages.commandPipePredicateGetInit.sendMessage(sender, config.rootSection.builtBaseEnvironment);

        interactionSessionByPlayerId.put(player.getUniqueId(), new PredicateGetSession(player));
        return true;
      }

      case SET -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender)) {
          config.rootSection.playerMessages.missingPermissionPipePredicateModify.sendMessage(sender, config.rootSection.builtBaseEnvironment);
          return false;
        }

        var predicateAndLanguage = tryParsePredicateAndLanguage(player, args);

        if (predicateAndLanguage == null)
          return true;

        config.rootSection.playerMessages.commandPipePredicateSetInit.sendMessage(
          sender,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("predicate", new StringifyState(true).appendPredicate(predicateAndLanguage.predicate()).toString())
            .build()
        );

        interactionSessionByPlayerId.put(player.getUniqueId(), new PredicateSetSession(player, predicateAndLanguage));
        return true;
      }

      default -> { return true; }
    }
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

    if (normalizedAction.constant != CommandAction.SET && normalizedAction.constant != CommandAction.SEARCH)
      return null;

    TranslationLanguage language;

    if (args.length == 2)
      return TranslationLanguage.matcher.createCompletions(args[1]);

    var languageSelection = TranslationLanguage.matcher.matchFirst(args[1]);

    if (languageSelection == null)
      return null;

    language = languageSelection.constant;

    try {
      var tokens = predicateHelper.parseTokens(args, 2);
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
    config.rootSection.playerMessages.manualEditWhileInPredicateMode.sendMessage(event.getPlayer(), config.rootSection.builtBaseEnvironment);
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

    // Searches do not benefit from multi-use - would only be confusing to users.
    if (interactionSession == null || interactionSession instanceof PredicateSearchSession)
      return;

    if (!interactionSession.allowMultiUse) {
      interactionSession.allowMultiUse = true;
      interactionSession.touchExpiry();
      config.rootSection.playerMessages.commandPipePredicateInteractMultiEntered.sendMessage(player, config.rootSection.builtBaseEnvironment);
      return;
    }

    interactionSessionByPlayerId.remove(player.getUniqueId());
    showActionBarMessage(player, ""); // Immediately clear action-bar signal

    config.rootSection.playerMessages.commandPipePredicateInteractMultiExited.sendMessage(player, config.rootSection.builtBaseEnvironment);
  }

  private boolean handleInteractionSessionAndGetIfCancel(Player player, Block target) {
    var interactionSession = interactionSessionByPlayerId.get(player.getUniqueId());

    if (interactionSession == null)
      return false;

    if (!interactionSession.allowMultiUse)
      interactionSessionByPlayerId.remove(player.getUniqueId());

    if (interactionSession instanceof PredicateSearchSession searchSession) {
      var piston = tryResolvePistonFromTargetBlock(player, target);

      if (piston == null)
        return false;

      if (!pipeEventHandler.canBuildAt(player, piston)) {
        config.rootSection.playerMessages.commandPipePredicateCannotBuild.sendMessage(player, config.rootSection.builtBaseEnvironment);
        return true;
      }

      pipeSearchHandler.handleSearch(player, piston, searchSession.query);
      return true;
    }

    var pistonSign = tryResolvePistonSignFromTargetBlock(player, target);

    if (pistonSign == null)
      return false;

    interactionSession.touchExpiry();

    if (interactionSession instanceof PredicateGetSession) {
      var predicateData = dataHandler.access(pistonSign);

      if (predicateData == null) {
        config.rootSection.playerMessages.commandPipePredicateNoPredicate.sendMessage(player, config.rootSection.builtBaseEnvironment);
        return true;
      }

      if (predicateData.parseException() != null) {
        config.rootSection.playerMessages.commandPipePredicateGetError.sendMessage(
          player,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("predicate_error", predicateHelper.createExceptionMessage(predicateData.parseException()))
            .build()
        );
        return true;
      }

      var predicateLanguageName = TranslationLanguage.matcher.getNormalizedName(predicateData.predicateLanguage());
      var predicateValue = predicateData.tokensPredicate();
      var setCommand = "/" + config.rootSection.commands.pipePredicate.evaluatedName + " " + CommandAction.matcher.getNormalizedName(CommandAction.SET) + " " + predicateLanguageName + " " + predicateValue;

      player.spigot().sendMessage(
        new ComponentBuilder(
          config.rootSection.playerMessages.commandPipePredicateGetPredicate
          .asScalar(
            ScalarType.STRING,
            config.rootSection.getBaseEnvironment()
              .withStaticVariable("predicate", predicateValue)
              .withStaticVariable("predicate_language", predicateLanguageName)
              .build()
          )
        )
          .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, setCommand))
          .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(
            config.rootSection.playerMessages.commandPipePredicateGetPredicateHover.asScalar(ScalarType.STRING, config.rootSection.builtBaseEnvironment)
          ).create()))
          .create()
      );

      return true;
    }

    if (interactionSession instanceof PredicateSetSession setSession) {
      if (setSession.valueToSet == null) {
        PredicateData predicateData;

        if ((predicateData = dataHandler.remove(pistonSign)) == null) {
          config.rootSection.playerMessages.commandPipePredicateNoPredicate.sendMessage(player, config.rootSection.builtBaseEnvironment);
          return true;
        }

        predicateData.restoreLines(pistonSign);

        config.rootSection.playerMessages.commandPipePredicateRemoveSuccess.sendMessage(
          player,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("predicate", predicateData.tokensPredicate())
            .build()
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
      pistonSign.setLine(2, "");
      pistonSign.setLine(3, "");

      // This call already saves the sign, so don't invoke saving twice
      dataHandler.store(newPredicateData, pistonSign);

      config.rootSection.playerMessages.commandPipePredicateSetSuccess.sendMessage(
        player,
        config.rootSection.getBaseEnvironment()
                .withStaticVariable("predicate", new StringifyState(true).appendPredicate(predicate))
                .build()
      );

      return true;
    }

    return true;
  }

  private @Nullable Block tryResolvePistonFromTargetBlock(Player executor, Block target) {
    var pistonBlock = BlockUtility.resolvePistonBlock(target);

    if (pistonBlock == null) {
      config.rootSection.playerMessages.commandPipePredicateNoPiston.sendMessage(executor, config.rootSection.builtBaseEnvironment);
      return null;
    }

    return pistonBlock;
  }

  private @Nullable Sign tryResolvePistonSignFromTargetBlock(Player executor, Block target) {
    var pistonBlock = tryResolvePistonFromTargetBlock(executor, target);

    if (pistonBlock == null)
      return null;

    var allowInitialize = PluginPermission.AUTO_INITIALIZE_SIGNS.has(executor);
    var pistonSign = BlockUtility.getPistonSign(pistonBlock, allowInitialize);

    if (pistonSign == null) {
      config.rootSection.playerMessages.commandPipePredicateNoSign.sendMessage(executor, config.rootSection.builtBaseEnvironment);
      return null;
    }

    if (!pipeEventHandler.canEditSign(executor, pistonSign)) {
      config.rootSection.playerMessages.commandPipePredicateCannotEditSign.sendMessage(executor, config.rootSection.builtBaseEnvironment);
      return null;
    }

    if (allowInitialize)
      BlockUtility.initializeBlankSignIfApplicable(pistonSign);

    return pistonSign;
  }

  private @Nullable PredicateAndLanguage tryParsePredicateAndLanguage(Player executor, String[] args) {
    if (args.length < 2) {
      config.rootSection.playerMessages.commandPipePredicateSetMissingLanguage.sendMessage(
        executor, config.rootSection.builtBaseEnvironment
      );
      return null;
    }

    var predicateArgsOffset = 2;
    var languageSelection = TranslationLanguage.matcher.matchFirst(args[1]);

    if (languageSelection == null) {
      config.rootSection.playerMessages.commandPipePredicateSetUnknownLanguage.sendMessage(
        executor,
        config.rootSection.getBaseEnvironment()
          .withStaticVariable("input", args[1])
          .build()
      );
      return null;
    }

    var language = languageSelection.constant;

    ItemPredicate predicate;

    try {
      var tokens = predicateHelper.parseTokens(args, predicateArgsOffset);
      predicate = predicateHelper.parsePredicate(language, tokens);
    } catch (ItemPredicateParseException e) {
      config.rootSection.playerMessages.commandPipePredicatePredicateError.sendMessage(
        executor,
        config.rootSection.getBaseEnvironment()
          .withStaticVariable("error_message", predicateHelper.createExceptionMessage(e))
          .build()
      );

      return null;
    }

    if (predicate == null) {
      config.rootSection.playerMessages.commandPipePredicateEmptyPredicate.sendMessage(executor, config.rootSection.builtBaseEnvironment);
      return null;
    }

    return new PredicateAndLanguage(predicate, language);
  }

  private void showActionBarMessage(Player player, String message) {
    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
  }
}
