package com.researchspace.dao.customliquibaseupdates.v62;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.dao.customliquibaseupdates.AbstractDBHelpers;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.UserManager;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DeleteUserPreferenceForChameleonToursIT extends AbstractDBHelpers {

  private @Autowired UserManager userManager;

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void checkChameleonPreferenceClearedByLiquibaseUpdate()
      throws SetupException, CustomChangeException {

    User user = createInitAndLoginAnyUser();

    // check no preference initially for new user
    openTransaction();
    user = userManager.get(user.getId());
    UserPreference initialPreference =
        user.getValueForPreference(Preference.CHAMELEON_TOURS_ENABLED_FOR_USER);
    assertNull(initialPreference.getId());
    commitTransaction();

    // set the preference
    openTransaction();
    user = userManager.get(user.getId());
    user.setPreference(
        new UserPreference(
            Preference.CHAMELEON_TOURS_ENABLED_FOR_USER, user, Boolean.TRUE.toString()));
    userManager.save(user);
    commitTransaction();

    // verify preference is set
    openTransaction();
    user = userManager.get(user.getId());
    UserPreference setPreference =
        user.getValueForPreference(Preference.CHAMELEON_TOURS_ENABLED_FOR_USER);
    assertNotNull(setPreference.getId());
    assertEquals(true, setPreference.getValueAsBoolean());
    commitTransaction();

    // run liquibase update
    DeleteUserPreferenceForChameleonTours updater = new DeleteUserPreferenceForChameleonTours();
    updater.setUp();
    updater.execute(null);

    // verify preference no longer set after liquibase update
    openTransaction();
    user = userManager.get(user.getId());
    UserPreference deletedPreference =
        user.getValueForPreference(Preference.CHAMELEON_TOURS_ENABLED_FOR_USER);
    assertNull(deletedPreference.getId());
    commitTransaction();
  }
}
