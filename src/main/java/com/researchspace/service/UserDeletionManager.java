package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.views.ServiceOperationResult;

/** Handles deletion of user accounts */
public interface UserDeletionManager {

  /**
   * Removes a user from the database by their userId. Before removing, makes an XML export of users
   * work. Also creates a file listing filestore resources belonging to the user, so they can be
   * deleted later by calling {@link #deleteRemovedUserFilestoreResources(Long, boolean)}.
   *
   * @param userToDeleteId ID of user to delete
   * @param policy user deletion policy
   * @param subject user performing the deletion
   * @return A human-readable status message indicating the outcome of the deletion.
   */
  ServiceOperationResult<User> removeUser(
      Long userToDeleteId, UserDeletionPolicy policy, User subject);

  /**
   * Looks for a file that lists user's filestore resources (one should be generated on user
   * deletion), if there is one, deletes the listed resources from filestore.
   *
   * @param deletedUserId
   * @param removeListingFile flag deciding if the listing file should be deleted at the end of the
   *     operation
   * @param user performing the deletion, must have sysadmin role
   * @return number of successfully deleted files
   */
  ServiceOperationResult<Integer> deleteRemovedUserFilestoreResources(
      Long deletedUserId, boolean removeListingFile, User subject);

  /**
   * Boolean test as to whether the User identified by <code>userId</code> can be removed. It checks
   * if
   *
   * <ul>
   *   <li>userToDeleteId is allowed to be deleted ( i.e. not sole PI in a group? is it community
   *       admin?)
   *   <li>Deleter has 'USER:DELETE' permission
   * </ul>
   *
   * This method is to quickly identify any obvious reasons why use deletion will fail
   * <em>before</em> calling #removeUser. and prevent an XML being made<br>
   * If this method returns <code>true</code> it does not guarantee that deletion will succeed -
   * there may be some FK constraints that prevent user being deleted, for example.
   *
   * @param userToDeleteId ID of user to delete
   * @param policy the user deletion policy
   * @param subject user who would be performing the deletion
   * @return <code>ServiceOperationResult#succeeded==true</code> if there are no <em>a priori</em>
   *     reasons why deletion will fail; <code>ServiceOperationResult#succeeded==false</code> if
   *     deletion attempt will certainly fail.
   */
  ServiceOperationResult<User> isUserRemovable(
      Long userToDeleteId, UserDeletionPolicy policy, User subject);
}
