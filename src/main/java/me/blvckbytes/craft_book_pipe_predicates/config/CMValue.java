package me.blvckbytes.craft_book_pipe_predicates.config;

import at.blvckbytes.component_markup.constructor.SlotType;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import at.blvckbytes.component_markup.markup.ast.node.MarkupNode;
import at.blvckbytes.component_markup.markup.ast.tag.built_in.BuiltInTagRegistry;
import at.blvckbytes.component_markup.markup.interpreter.MarkupInterpreter;
import at.blvckbytes.component_markup.markup.parser.MarkupParseException;
import at.blvckbytes.component_markup.markup.parser.MarkupParser;
import at.blvckbytes.component_markup.util.InputView;
import at.blvckbytes.component_markup.util.logging.InterpreterLogger;
import me.blvckbytes.bbconfigmapper.MappingError;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CMValue {

  public final MarkupNode markupNode;

  private final InterpretationEnvironment baseEnvironment;
  private final InterpreterLogger logger;

  public CMValue(String markup, InterpretationEnvironment baseEnvironment, InterpreterLogger logger) {
    var view = InputView.of(markup);

    try {
      this.markupNode = MarkupParser.parse(view, BuiltInTagRegistry.INSTANCE);
    } catch (MarkupParseException e) {
      logger.log(view, e.position, e.getErrorMessage(), null);

      throw new MappingError("The above error occurred while trying to parse component-markup");
    }

    this.baseEnvironment = baseEnvironment;
    this.logger = logger;
  }

  public void sendMessage(Audience player) {
    for (var component : interpret(SlotType.CHAT, null))
      player.sendMessage(component);
  }

  public void sendMessage(Audience player, InterpretationEnvironment environment) {
    for (var component : interpret(SlotType.CHAT, environment))
      player.sendMessage(component);
  }

  public void sendActionBar(Player player, InterpretationEnvironment environment) {
    var components = interpret(SlotType.CHAT, environment);

    if (!components.isEmpty())
      player.sendActionBar(components.get(0));
  }

  public void sendActionBar(Player player) {
    var components = interpret(SlotType.CHAT, null);

    if (!components.isEmpty())
      player.sendActionBar(components.get(0));
  }

  public List<Component> interpret(SlotType slotType, @Nullable InterpretationEnvironment environment) {
    InterpretationEnvironment finalEnvironment;

    if (environment == null)
      finalEnvironment = baseEnvironment;
    else {
      baseEnvironment.forEachKnownName(key -> environment.withVariable(key, baseEnvironment.getVariableValue(key)));
      finalEnvironment = environment;
    }

    return MarkupInterpreter.interpret(markupNode, slotType, finalEnvironment, AdventureComponentConstructor.INSTANCE, logger);
  }
}
