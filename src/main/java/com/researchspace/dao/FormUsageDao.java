package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.record.FormUsage;
import com.researchspace.model.record.RSForm;
import java.util.List;
import java.util.Optional;

/** Saves form usage information for dynamic menu construction */
public interface FormUsageDao extends GenericDao<FormUsage, Long> {

  /**
   * Gets the most recently used popular forms, in descending order
   *
   * @param numDocuments The number of uses to consider (default =100)
   * @param limit The number of results to return (default =4)
   * @param toExclude A Form to exclude, can be null
   * @param A User
   * @return A possibly empty but non-<code>null</code> <code>List</code> of Form stable
   *     identifiers.
   */
  public List<String> getMostPopularForms(int numDocuments, int limit, RSForm toExclude, User u);

  /**
   * Gets the most recently used {@link RSForm} for a given user.
   *
   * @param user
   * @return The FormUsage object for that event or an empty Optional if the user has never used a
   *     Form before.
   */
  Optional<FormUsage> getMostRecentlyUsedFormForUser(User u);
}
