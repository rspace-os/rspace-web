package com.researchspace.service;

import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;

/** Strategy interface for whether a document or notebook can be shared or not. */
public interface DocumentSharedStateCalculator {
  /**
   * @param userOrGroup The group/user that we're trying to share with.
   * @param document The item that is being tested for sharing status
   * @param subject The current user
   * @return <code>true</code> if <code>document</code> can be shared, <code>false</code> otherwise
   */
  boolean canShare(AbstractUserOrGroupImpl userOrGroup, BaseRecord document, User subject);
}
