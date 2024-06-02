package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.record.FormUserMenu;
import com.researchspace.model.record.RSForm;
import java.util.List;

/** DAO methods to persist whether a Form appears in users's create menu or not. */
public interface FormCreateMenuDao extends GenericDao<FormUserMenu, Long> {

  boolean removeForUserAndForm(User user, String stableID);

  boolean formExistsInMenuForUser(String formStableID, User user);

  /**
   * Modifies in place a List of {@link RSForm} with the property 'setInSubjectMenu==true' if for
   * the given user, the form is in their menu table.
   *
   * @param user
   * @param allForms
   */
  void updateFormsInMenuForUser(User user, List<RSForm> allForms);
}
