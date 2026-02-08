package me.blvckbytes.craft_book_pipe_predicates.search;

import com.sk89q.craftbook.mechanics.pipe.*;
import me.blvckbytes.craft_book_pipe_predicates.PistonPredicateRegistry;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Consumer;

public class PredicateLocateSession extends EnumerationSession<PredicateLocateSession> {

  private final PistonPredicateRegistry predicateRegistry;
  public final List<PredicateAndPiston> result;

  public PredicateLocateSession(
    Block origin, Pipes pipesMechanic, Plugin plugin,
    PistonPredicateRegistry predicateRegistry,
    Consumer<PredicateLocateSession> warmupHandler,
    Consumer<PredicateLocateSession> completionHandler
  ) {
    super(origin, pipesMechanic, plugin, warmupHandler, completionHandler);

    this.predicateRegistry = predicateRegistry;
    this.result = new ArrayList<>();
  }
  @Override
  protected EnumerationDecision onTube(Block block, int cachedBlock) {
    return EnumerationDecision.CONTINUE;
  }

  @Override
  protected EnumerationDecision onPiston(Block block, int cachedBlock) {
    var predicate = predicateRegistry.getPredicateForPiston(block);

    if (predicate != null)
      result.add(new PredicateAndPiston(predicate, block));

    return EnumerationDecision.CONTINUE;
  }

  @Override
  protected void beforeCompletion() {}

  @Override
  protected void beforeSubPipe() {}

  @Override
  protected void afterSubPipe() {}

  @Override
  protected EnumSet<EnumerationBehavior> getEnumerationBehavior() {
    return EnumSet.of(EnumerationBehavior.IGNORE_CHECK_VALVES, EnumerationBehavior.LOAD_PISTON_SIGNS);
  }

  @Override
  protected void beforeRetry() {
    result.clear();
  }

  @Override
  protected boolean isDone() {
    return true;
  }
}
