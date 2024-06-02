package com.researchspace.api.v1.service;

import com.researchspace.api.v1.controller.FormTemplatesCommon.FormPost;
import com.researchspace.model.User;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.session.UserSessionTracker;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Facade interface for complex operations on RSForm objects, or for functionality shared between UI
 * and API Form controllers
 */
public interface RSFormApiHandler {

  /**
   * Updates a Form with a new set of form definitions. It combines editing, saving and update
   * operations into a single operation. Note that <em>All</em> fields have to be supplied, not just
   * the updated ones.<br>
   * If successful, will:
   *
   * <ul>
   *   <li>Update the form version
   *   <li>Maintain publishing state of the original form
   *   <li>Update the ID of the current form and its form fields.
   * </ul>
   *
   * @param id
   * @param formPost - the updated field definitions
   * @param user
   * @param activeUsers
   * @return An update RSForm with the definitions in <code>formPost</code> applied.
   * @throws IllegalStateException if form could not be edited.
   * @throws IllegalArgumentException if incoming FormField ids are not a subset of the persisted
   *     form IDs.
   */
  AbstractForm editForm(Long id, FormPost formPost, User user, UserSessionTracker activeUsers);

  /**
   * Saves an image icon for the form
   *
   * @param file
   * @param formId
   * @param user
   * @return
   * @throws IOException
   */
  AbstractForm saveImage(MultipartFile file, Long formId, User user) throws IOException;
}
