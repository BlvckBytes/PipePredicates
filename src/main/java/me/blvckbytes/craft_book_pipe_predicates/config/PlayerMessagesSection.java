package me.blvckbytes.craft_book_pipe_predicates.config;

import me.blvckbytes.bbconfigmapper.sections.AConfigSection;
import me.blvckbytes.bukkitevaluable.BukkitEvaluable;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;
import org.jetbrains.annotations.Nullable;

public class PlayerMessagesSection extends AConfigSection {

  public BukkitEvaluable manualEditWhileInPredicateMode;
  public BukkitEvaluable commandPipePredicateUsage;
  public BukkitEvaluable commandPipePredicateNoPiston;
  public BukkitEvaluable commandPipePredicateNoSign;
  public BukkitEvaluable commandPipePredicateCannotEditSign;
  public BukkitEvaluable commandPipePredicateCannotBuild;
  public BukkitEvaluable commandPipePredicateNoPredicate;
  public BukkitEvaluable commandPipePredicatePredicateError;
  public BukkitEvaluable commandPipePredicateEmptyPredicate;
  public BukkitEvaluable commandPipePredicateRemoveInit;
  public BukkitEvaluable commandPipePredicateRemoveSuccess;
  public BukkitEvaluable commandPipePredicateSetInit;
  public BukkitEvaluable commandPipePredicateSetSuccess;
  public BukkitEvaluable commandPipePredicateMissingLanguage;
  public BukkitEvaluable commandPipePredicateUnknownLanguage;
  public BukkitEvaluable commandPipePredicateInteractMultiEntered;
  public BukkitEvaluable commandPipePredicateInteractMultiExited;
  public @Nullable BukkitEvaluable commandPipePredicateInteractMultiActionBarSignal;
  public BukkitEvaluable commandPipePredicateInteractExpired;
  public BukkitEvaluable commandPipePredicateGetInit;
  public BukkitEvaluable commandPipePredicateGetPredicate;
  public BukkitEvaluable commandPipePredicateGetPredicateHover;
  public BukkitEvaluable commandPipePredicateGetError;
  public BukkitEvaluable commandPipePredicateReloadSuccess;
  public BukkitEvaluable commandPipePredicateReloadFailure;

  public BukkitEvaluable commandPipePredicateSearchInit;
  public BukkitEvaluable commandPipePredicateSearchInSession;
  public BukkitEvaluable commandPipePredicateSearchExceededPipes;
  public BukkitEvaluable commandPipePredicateSearchExceededPistons;
  public BukkitEvaluable commandPipePredicateSearchExceededRetry;
  public BukkitEvaluable commandPipePredicateSearchNoPistons;
  public BukkitEvaluable commandPipePredicateSearchNoContainers;
  public BukkitEvaluable commandPipePredicateSearchBeginEnumeratePistons;
  public BukkitEvaluable commandPipePredicateSearchBeginEnumerateContainers;
  public BukkitEvaluable commandPipePredicateSearchBeginTesting;
  public BukkitEvaluable commandPipePredicateSearchNoResults;
  public BukkitEvaluable commandPipePredicateSearchShowingResults;
  public BukkitEvaluable commandPipePredicateSearchGetItemContainerAbsent;
  public BukkitEvaluable commandPipePredicateSearchGetItemContainerSizeChanged;
  public BukkitEvaluable commandPipePredicateSearchGetItemMoved;
  public BukkitEvaluable commandPipePredicateSearchGetItemSuccess;
  public BukkitEvaluable commandPipePredicateSearchGetItemDropped;
  public BukkitEvaluable commandPipePredicateSearchContainerOpened;
  public BukkitEvaluable commandPipePredicateSearchContainerTeleported;

  public BukkitEvaluable missingPermissionPipePredicateCommand;

  public PlayerMessagesSection(EvaluationEnvironmentBuilder baseEnvironment) {
    super(baseEnvironment);
  }
}
