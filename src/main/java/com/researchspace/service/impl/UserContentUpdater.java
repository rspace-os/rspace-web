package com.researchspace.service.impl;

import com.researchspace.model.User;

public interface UserContentUpdater {
  /**
   * Updates called by this will only run ONCE and therefore this method can be called multiple
   * times - ie on every user login or on every app startup with no harmful effects other than the
   * cost of checking whether the updates are required to run or not.
   *
   * @param subject
   */
  void doUserContentUpdates(User subject);
}
