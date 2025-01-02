package me.blvckbytes.craft_book_pipe_predicates.config;

import me.blvckbytes.bbconfigmapper.sections.AConfigSection;
import me.blvckbytes.bukkitevaluable.BukkitEvaluable;
import me.blvckbytes.gpeee.interpreter.EvaluationEnvironmentBuilder;

public class PlayerMessagesSection extends AConfigSection {

  public BukkitEvaluable manualEditWhileInPredicateMode;
  public BukkitEvaluable commandPipePredicateUsage;
  public BukkitEvaluable commandPipePredicateNoPiston;
  public BukkitEvaluable commandPipePredicateNoSign;
  public BukkitEvaluable commandPipePredicateCannotEditSign;
  public BukkitEvaluable commandPipePredicateNoPredicate;
  public BukkitEvaluable commandPipePredicateEmptyPredicate;
  public BukkitEvaluable commandPipePredicateRemoveSuccess;
  public BukkitEvaluable commandPipePredicateSetSuccess;
  public BukkitEvaluable commandPipePredicateSetLocalizedUnknownLanguage;
  public BukkitEvaluable commandPipePredicateGetPredicate;
  public BukkitEvaluable commandPipePredicateGetPredicateHover;
  public BukkitEvaluable commandPipePredicateGetError;
  public BukkitEvaluable commandPipePredicateReloadSuccess;
  public BukkitEvaluable commandPipePredicateReloadFailure;

  public BukkitEvaluable missingPermissionPipePredicateCommand;
  public BukkitEvaluable missingPermissionPipePredicateRead;
  public BukkitEvaluable missingPermissionPipePredicateModify;
  public BukkitEvaluable missingPermissionPipePredicateReload;

  public PlayerMessagesSection(EvaluationEnvironmentBuilder baseEnvironment) {
    super(baseEnvironment);
  }
}
