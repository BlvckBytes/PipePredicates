package me.blvckbytes.craft_book_pipe_predicates.config;

import me.blvckbytes.bbconfigmapper.sections.AConfigSection;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;

public class PlayerMessagesSection extends AConfigSection {

  public CMValue manualEditWhileInPredicateMode;
  public CMValue commandPipePredicateUsage;
  public CMValue commandPipePredicateNoPiston;
  public CMValue commandPipePredicateNoSign;
  public CMValue commandPipePredicateCannotEditSign;
  public CMValue commandPipePredicateCannotBuild;
  public CMValue commandPipePredicateNotLookingAtPipe;
  public CMValue commandPipePredicateNoPredicate;
  public CMValue commandPipePredicatePredicateError;
  public CMValue commandPipePredicateEmptyPredicate;
  public CMValue commandPipePredicateRemoveInit;
  public CMValue commandPipePredicateRemoveSuccess;
  public CMValue commandPipePredicateSetInit;
  public CMValue commandPipePredicateSetSuccess;
  public CMValue commandPipePredicateMissingLanguage;
  public CMValue commandPipePredicateUnknownLanguage;
  public CMValue commandPipePredicateInteractMultiEntered;
  public CMValue commandPipePredicateInteractMultiExited;
  public CMValue commandPipePredicateInteractMultiActionBarSignal;
  public CMValue commandPipePredicateInteractExpired;
  public CMValue commandPipePredicateGetInit;
  public CMValue commandPipePredicateGetPredicate;
  public CMValue commandPipePredicateGetError;
  public CMValue commandPipePredicateFrameLockInit;
  public CMValue commandPipePredicateFrameUnlockInit;
  public CMValue commandPipePredicateFrameLockNoFrames;
  public CMValue commandPipePredicateFrameLockAlreadyLocked;
  public CMValue commandPipePredicateFrameLockFramesLocked;
  public CMValue commandPipePredicateFrameLockAlreadyUnlocked;
  public CMValue commandPipePredicateFrameLockFramesUnlocked;
  public CMValue commandPipePredicateReloadSuccess;
  public CMValue commandPipePredicateReloadFailure;
  public CMValue commandPipePredicateSearchInSession;
  public CMValue commandPipePredicateSearchExceededPipes;
  public CMValue commandPipePredicateSearchExceededPistons;
  public CMValue commandPipePredicateSearchExceededRetry;
  public CMValue commandPipePredicateSearchNoContainers;
  public CMValue commandPipePredicateSearchActionbarWarmup;
  public CMValue commandPipePredicateSearchNoResults;
  public CMValue commandPipePredicateSearchShowingResults;
  public CMValue commandPipePredicateSearchGetItemContainerAbsent;
  public CMValue commandPipePredicateSearchGetItemContainerSizeChanged;
  public CMValue commandPipePredicateSearchGetItemMoved;
  public CMValue commandPipePredicateSearchGetItemSuccess;
  public CMValue commandPipePredicateSearchGetItemDropped;
  public CMValue commandPipePredicateSearchContainerOpened;
  public CMValue commandPipePredicateSearchContainerTeleportObstructed;
  public CMValue commandPipePredicateSearchContainerTeleported;
  public CMValue commandPipePredicateVisualizeInternalError;
  public CMValue commandPipePredicateVisualizeRanIntoLimit;
  public CMValue commandPipePredicateVisualizeSuccess;
  public CMValue commandPipePredicateVisualizeNoVisualization;
  public CMValue commandPipePredicateVisualizeClearedPriorVisualization;
  public CMValue commandPipePredicateVisualizeClearedVisualization;
  public CMValue missingPermissionPipePredicateCommand;

  public PlayerMessagesSection(EvaluationEnvironmentBuilder baseEnvironment) {
    super(baseEnvironment);
  }
}
