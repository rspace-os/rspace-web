package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceSettings.WorkspaceViewMode;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.UserManager;
import javax.validation.constraints.NotNull;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Helper class to validate, read and save the current workspace view mode (tree view or list view)
 * in the database for each user. Is used in `WorkspaceController.java`
 */
@NoArgsConstructor
public class WorkspaceViewModePreferences {

  @Autowired private UserManager userManager;

  /**
   * Function that will check if the browser already has the current workspace view mode, and if
   * not, retrieves the one the user set (default if not), and updates the database if it has
   * changed.
   *
   * @param receivedViewMode view mode received from request
   * @param user user who initiated the request / who's workspace view mode setting is required
   * @return view mode set into database
   */
  WorkspaceViewMode setOrGetWorkspaceViewMode(WorkspaceViewMode receivedViewMode, User user) {
    WorkspaceViewMode db = getDatabaseValue(user);
    if (receivedViewMode == null) {
      return db;
    } else {
      if (!receivedViewMode.equals(db)) setDatabaseValue(receivedViewMode, user);
      return receivedViewMode;
    }
  }

  @NotNull
  private WorkspaceViewMode getDatabaseValue(User user) {
    // Database has a default value, cannot be null
    return WorkspaceViewMode.valueOf(
        userManager.getPreferenceForUser(user, Preference.CURRENT_WORKSPACE_VIEW_MODE).getValue());
  }

  private void setDatabaseValue(@NotNull WorkspaceViewMode value, User user) {
    userManager.setPreference(
        Preference.CURRENT_WORKSPACE_VIEW_MODE, value.toString(), user.getUsername());
  }
}
