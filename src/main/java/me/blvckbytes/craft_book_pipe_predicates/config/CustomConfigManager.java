package me.blvckbytes.craft_book_pipe_predicates.config;

import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import at.blvckbytes.component_markup.markup.ast.tag.built_in.BuiltInTagRegistry;
import at.blvckbytes.component_markup.markup.parser.MarkupParseException;
import at.blvckbytes.component_markup.markup.parser.MarkupParser;
import at.blvckbytes.component_markup.util.ErrorScreen;
import at.blvckbytes.component_markup.util.InputView;
import at.blvckbytes.component_markup.util.logging.InterpreterLogger;
import me.blvckbytes.bbconfigmapper.ConfigMapper;
import me.blvckbytes.bbconfigmapper.FValueConverter;
import me.blvckbytes.bukkitevaluable.ConfigManager;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CustomConfigManager extends ConfigManager implements InterpreterLogger {

  private final Logger logger;
  private InterpretationEnvironment baseEnvironment;

  public CustomConfigManager(Plugin plugin, String folderName) throws Exception {
    super(plugin, folderName);
    this.logger = plugin.getLogger();
  }

  @Override
  public ConfigMapper loadConfig(String fileName) throws Exception {
    var mapper = super.loadConfig(fileName);

    baseEnvironment = new InterpretationEnvironment();

    var globalLookupTable = new HashMap<String, Object>();

    if (mapper.getConfig().get("cLut") instanceof Map<?,?> map) {
      for (var entry : map.entrySet()) {
        var key = String.valueOf(entry.getKey());
        var view = InputView.of(String.valueOf(entry.getValue()));

        try {
          globalLookupTable.put(key, MarkupParser.parse(view, BuiltInTagRegistry.INSTANCE));
        } catch (MarkupParseException e) {
          log(view, e.position, e.getErrorMessage(), null);
        }
      }
    }

    if (mapper.getConfig().get("sLut") instanceof Map<?,?> map) {
      for (var entry : map.entrySet()) {
        var key = String.valueOf(entry.getKey());

        if (globalLookupTable.keySet().stream().anyMatch(key::equalsIgnoreCase))
          logger.warning("Duplicate s-lut-entry \"" + key + "\" in " + fileName);

        globalLookupTable.put(key, entry.getValue());
      }
    }

    baseEnvironment.withVariable("lut", globalLookupTable);

    return mapper;
  }

  @Override
  public @Nullable Class<?> getRequiredTypeFor(Class<?> type) {
    if (type == CMValue.class)
      return String.class;

    if (type == ExpressionValue.class)
      return String.class;

    return super.getRequiredTypeFor(type);
  }

  @Override
  public @Nullable FValueConverter getConverterFor(Class<?> type) {
    if (type == CMValue.class)
      return (value, evaluator) -> new CMValue((String) value, baseEnvironment, this);

    if (type == ExpressionValue.class)
      return (value, evaluator) -> new ExpressionValue((String) value, baseEnvironment, this);

    return super.getConverterFor(type);
  }

  @Override
  public void log(InputView view, int position, String message, @Nullable Throwable e) {
    for (var line : ErrorScreen.make(view, position, message))
      logger.log(Level.WARNING, line);

    if (e != null)
      logger.log(Level.WARNING, "The following error occurred:", e);
  }
}
