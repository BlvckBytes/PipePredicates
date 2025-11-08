package me.blvckbytes.craft_book_pipe_predicates;

import me.blvckbytes.bbconfigmapper.ScalarType;
import me.blvckbytes.bukkitevaluable.ConfigKeeper;
import me.blvckbytes.craft_book_pipe_predicates.config.MainSection;
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

  private record ItemPredicateAndLanguage(ItemPredicate predicate, TranslationLanguage language) {}

  private final PredicateDataHandler dataHandler;
  private final PipeEventHandler pipeEventHandler;
  private final PredicateHelper predicateHelper;
  private final ConfigKeeper<MainSection> config;
  private final Logger logger;

  private final Map<UUID, ItemPredicateAndLanguage> setManyPredicateByPlayerId;

  public PipePredicateCommand(
    PredicateDataHandler dataHandler,
    PipeEventHandler pipeEventHandler,
    PredicateHelper predicateHelper,
    ConfigKeeper<MainSection> config,
    Logger logger
  ) {
    this.dataHandler = dataHandler;
    this.pipeEventHandler = pipeEventHandler;
    this.predicateHelper = predicateHelper;
    this.config = config;
    this.logger = logger;
    this.setManyPredicateByPlayerId = new HashMap<>();
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

    if (normalizedAction.constant == CommandAction.SET_MANY || normalizedAction.constant == CommandAction.SET_MANY_LOCALIZED) {
      if (setManyPredicateByPlayerId.remove(player.getUniqueId()) != null) {
        config.rootSection.playerMessages.commandPipePredicateSetManyExited.sendMessage(player, config.rootSection.builtBaseEnvironment);
        return true;
      }

      var predicateAndLanguage = tryParsePredicateAndLanguage(player, args, normalizedAction.constant == CommandAction.SET_MANY_LOCALIZED);

      if (predicateAndLanguage == null)
        return true;

      setManyPredicateByPlayerId.put(player.getUniqueId(), predicateAndLanguage);

      config.rootSection.playerMessages.commandPipePredicateSetManyEntered.sendMessage(
        player,
        config.rootSection.getBaseEnvironment()
          .withStaticVariable("predicate", new StringifyState(true).appendPredicate(predicateAndLanguage.predicate).toString())
          .withStaticVariable("predicate_language", TranslationLanguage.matcher.getNormalizedName(predicateAndLanguage.language))
          .build()
      );

      return true;
    }

    var pistonSign = tryResolvePistonSignFromTargetBlock(player, player.getTargetBlock(null, 5));

    if (pistonSign == null)
      return true;

    switch (normalizedAction.constant) {
      case REMOVE -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender)) {
          config.rootSection.playerMessages.missingPermissionPipePredicateModify.sendMessage(sender, config.rootSection.builtBaseEnvironment);
          return false;
        }

        PredicateData predicateData;

        if ((predicateData = dataHandler.remove(pistonSign)) == null) {
          config.rootSection.playerMessages.commandPipePredicateNoPredicate.sendMessage(player, config.rootSection.builtBaseEnvironment);
          return true;
        }

        predicateData.restoreLines(pistonSign);

        config.rootSection.playerMessages.commandPipePredicateRemoveSuccess.sendMessage(sender, config.rootSection.builtBaseEnvironment);
        return true;
      }

      case GET_ENTERED, GET_EXPANDED -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_READ.has(sender)) {
          config.rootSection.playerMessages.missingPermissionPipePredicateRead.sendMessage(sender, config.rootSection.builtBaseEnvironment);
          return false;
        }

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
        }

        var isRequestingExpanded = normalizedAction.constant == CommandAction.GET_EXPANDED;
        var requestedPredicate = isRequestingExpanded ? predicateData.expandedPredicate() : predicateData.tokensPredicate();
        var predicateLanguageName = TranslationLanguage.matcher.getNormalizedName(predicateData.predicateLanguage());
        var isDefaultPredicateLanguage = predicateData.predicateLanguage().equals(config.rootSection.defaultPredicateLanguage);

        var setCommand = "/" + label + " ";

        if (isDefaultPredicateLanguage)
          setCommand += CommandAction.matcher.getNormalizedName(CommandAction.SET);
        else
          setCommand += CommandAction.matcher.getNormalizedName(CommandAction.SET_LOCALIZED) + " " + predicateLanguageName;

        setCommand += " " + requestedPredicate;

        player.spigot().sendMessage(
          new ComponentBuilder(
            (
              isDefaultPredicateLanguage
                ? config.rootSection.playerMessages.commandPipePredicateGetPredicateDefaultLanguage
                : config.rootSection.playerMessages.commandPipePredicateGetPredicateOtherLanguage
            )
            .asScalar(
              ScalarType.STRING,
              config.rootSection.getBaseEnvironment()
                .withStaticVariable("predicate", requestedPredicate)
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

      case SET, SET_LOCALIZED -> {
        if (!PluginPermission.PIPE_PREDICATE_COMMAND_MODIFY.has(sender)) {
          config.rootSection.playerMessages.missingPermissionPipePredicateModify.sendMessage(sender, config.rootSection.builtBaseEnvironment);
          return false;
        }

        var predicateAndLanguage = tryParsePredicateAndLanguage(player, args, normalizedAction.constant == CommandAction.SET_LOCALIZED);

        if (predicateAndLanguage == null)
          return true;

        applyPredicateToPistonSign(player, pistonSign, predicateAndLanguage.predicate, predicateAndLanguage.language);
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
      return CommandAction.matcher.createCompletions(null, actionFilter);

    var normalizedAction = CommandAction.matcher.matchFirst(args[0], actionFilter);

    if (
      normalizedAction == null ||
      (
        normalizedAction.constant != CommandAction.SET
        && normalizedAction.constant != CommandAction.SET_LOCALIZED
        && normalizedAction.constant != CommandAction.SET_MANY
        && normalizedAction.constant != CommandAction.SET_MANY_LOCALIZED
      )
    ) {
      return null;
    }

    int predicateArgsOffset = 1;
    TranslationLanguage language;

    if (normalizedAction.constant == CommandAction.SET_LOCALIZED || normalizedAction.constant == CommandAction.SET_MANY_LOCALIZED) {
      predicateArgsOffset = 2;

      if (args.length == 2)
        return TranslationLanguage.matcher.createCompletions(args[1]);

      var languageSelection = TranslationLanguage.matcher.matchFirst(args[1]);

      if (languageSelection == null)
        return null;

      language = languageSelection.constant;
    }

    else
      language = config.rootSection.defaultPredicateLanguage;

    try {
      var tokens = predicateHelper.parseTokens(args, predicateArgsOffset);
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
    setManyPredicateByPlayerId.remove(event.getPlayer().getUniqueId());
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

    if (tryApplySetManyMode(player, clickedBlock))
      event.setCancelled(true);
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    var player = event.getPlayer();

    if (tryApplySetManyMode(player, event.getBlockAgainst()))
      event.setCancelled(true);
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    var player = event.getPlayer();

    if (tryApplySetManyMode(player, event.getBlock()))
      event.setCancelled(true);
  }

  @EventHandler
  public void onBucketEmpty(PlayerBucketEmptyEvent event) {
    var player = event.getPlayer();

    if (tryApplySetManyMode(player, event.getBlockClicked()))
      event.setCancelled(true);
  }

  private boolean tryApplySetManyMode(Player player, Block target) {
    var predicateAndLanguage = setManyPredicateByPlayerId.get(player.getUniqueId());

    if (predicateAndLanguage == null)
      return false;

    var pistonSign = tryResolvePistonSignFromTargetBlock(player, target);

    if (pistonSign == null)
      return false;

    applyPredicateToPistonSign(player, pistonSign, predicateAndLanguage.predicate, predicateAndLanguage.language);
    return true;
  }

  private @Nullable Sign tryResolvePistonSignFromTargetBlock(Player executor, Block target) {
    var pistonBlock = BlockUtility.resolvePistonBlock(target);

    if (pistonBlock == null) {
      config.rootSection.playerMessages.commandPipePredicateNoPiston.sendMessage(executor, config.rootSection.builtBaseEnvironment);
      return null;
    }

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

  private void applyPredicateToPistonSign(Player executor, Sign pistonSign, ItemPredicate predicate, TranslationLanguage language) {
    var existingPredicateData = dataHandler.access(pistonSign);

    PredicateData newPredicateData;

    if (existingPredicateData != null)
      newPredicateData = PredicateData.makeUpdate(predicate, language, existingPredicateData);
    else
      newPredicateData = PredicateData.makeInitial(predicate, language, pistonSign);

    pistonSign.setLine(0, "§" + MarkerConstants.PREDICATE_OK_COLOR + MarkerConstants.PREDICATE_MARKER);
    pistonSign.setLine(2, "");
    pistonSign.setLine(3, "");
    pistonSign.update(true, false);

    dataHandler.store(newPredicateData, pistonSign);

    config.rootSection.playerMessages.commandPipePredicateSetSuccess.sendMessage(
      executor,
      config.rootSection.getBaseEnvironment()
        .withStaticVariable("predicate", new StringifyState(true).appendPredicate(predicate))
        .build()
    );
  }

  private @Nullable ItemPredicateAndLanguage tryParsePredicateAndLanguage(Player executor, String[] args, boolean localized) {
    int predicateArgsOffset = 1;
    TranslationLanguage language;

    if (localized) {
      if (args.length < 2) {
        config.rootSection.playerMessages.commandPipePredicateSetLocalizedMissingLanguage.sendMessage(
          executor, config.rootSection.builtBaseEnvironment
        );
        return null;
      }

      predicateArgsOffset = 2;

      var languageSelection = TranslationLanguage.matcher.matchFirst(args[1]);

      if (languageSelection == null) {
        config.rootSection.playerMessages.commandPipePredicateSetLocalizedUnknownLanguage.sendMessage(
          executor,
          config.rootSection.getBaseEnvironment()
            .withStaticVariable("input", args[1])
            .build()
        );
        return null;
      }

      language = languageSelection.constant;
    }

    else
      language = config.rootSection.defaultPredicateLanguage;

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

    return new ItemPredicateAndLanguage(predicate, language);
  }

  private void showActionBarMessage(Player player, String message) {
    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
  }
}
