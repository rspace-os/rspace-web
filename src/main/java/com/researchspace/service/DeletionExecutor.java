package com.researchspace.service;

import com.researchspace.model.views.CompositeRecordOperationResult;

/**
 * Executes a record deletion following a {@link DeletionPlan} and putting results of deletion into
 * a {@link CompositeRecordOperationResult}
 */
public interface DeletionExecutor {

  /**
   * Deletes records as defined in the DeletionPlan
   *
   * @param result
   * @param plan
   */
  void execute(CompositeRecordOperationResult result, DeletionPlan plan);
}
