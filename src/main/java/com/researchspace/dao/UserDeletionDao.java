package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.UserDeletionPolicy;

/**
 * DAO for handling user deletion use case, where a user and all their related data is physically
 * deleted from the database
 */
public interface UserDeletionDao {
  /**
   * @param userId The database id of the user to be deleted
   * @param policy A policy configuring how the deletion should work.
   * @return The deleted user along with a human readable message indicating the outcome of this
   *     method.
   */
  ServiceOperationResult<User> deleteUser(Long userId, UserDeletionPolicy policy);
}
