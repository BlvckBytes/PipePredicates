package me.blvckbytes.craft_book_pipe_predicates.config;

import at.blvckbytes.component_markup.expression.ast.ExpressionNode;
import at.blvckbytes.component_markup.expression.interpreter.ExpressionInterpreter;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import at.blvckbytes.component_markup.expression.parser.ExpressionParseException;
import at.blvckbytes.component_markup.expression.parser.ExpressionParser;
import at.blvckbytes.component_markup.util.InputView;
import at.blvckbytes.component_markup.util.logging.InterpreterLogger;
import me.blvckbytes.bbconfigmapper.MappingError;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ExpressionValue {

  public final ExpressionNode expressionNode;

  private final InterpretationEnvironment baseEnvironment;
  private final InterpreterLogger logger;

  public ExpressionValue(String markup, InterpretationEnvironment baseEnvironment, InterpreterLogger logger) {
    var view = InputView.of(markup);

    try {
      this.expressionNode = ExpressionParser.parse(view, null);
    } catch (ExpressionParseException e) {
      logger.log(view, e.position, e.getErrorMessage(), null);

      throw new MappingError("The above error occurred while trying to parse an expression");
    }

    this.baseEnvironment = baseEnvironment;
    this.logger = logger;
  }

  public @Nullable Object interpret(@Nullable InterpretationEnvironment environment) {
    InterpretationEnvironment finalEnvironment;

    if (environment == null)
      finalEnvironment = baseEnvironment;
    else {
      baseEnvironment.forEachKnownName(key -> environment.withVariable(key, baseEnvironment.getVariableValue(key)));
      finalEnvironment = environment;
    }

    return ExpressionInterpreter.interpret(expressionNode, finalEnvironment, logger);
  }

  public static Set<Integer> asIntSet(@Nullable ExpressionValue value, InterpretationEnvironment environment) {
    if (value == null)
      return Collections.emptySet();

    var result = new HashSet<Integer>();

    for (var slotEntry : environment.getValueInterpreter().asList(value.interpret(environment)))
      result.add((int) environment.getValueInterpreter().asLong(slotEntry));

    return result;
  }
}
