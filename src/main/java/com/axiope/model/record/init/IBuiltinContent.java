package com.axiope.model.record.init;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.UserFolderSetup;
import java.util.List;
import java.util.Optional;

/**
 * Defines methods for builtin form and template creation. All arguments to methods in this
 * interface should not be <code>null</code>. Implementations should just create the relevant
 * objects, not persist them
 */
public interface IBuiltinContent {

  /*
   * Create the Form and templates, owned by the user, and persist them
   *
   * @param user the user creating the form
   */
  RSForm createForm(User createdBy);

  /**
   * Creates a SampleTemplate. Default implementation returns empty optional
   *
   * @param createdBy
   * @return
   */
  default Optional<Sample> createSampleTemplate(User createdBy) {
    return Optional.empty();
  }

  List<StructuredDocument> createTemplates(User createdBy);

  List<StructuredDocument> createExamples(User createdBy, UserFolderSetup folderSetup);

  List<StructuredDocument> createExamples(
      User createdBy, UserFolderSetup folderSetup, StructuredDocument linkTo);

  /**
   * Returns an optional string, possibly null, of an icon to display by this form: E.g.,
   * 'antibody32.png'. The associated icon should be in src/main/resources/formIcons
   *
   * @return
   */
  String getFormIconName();

  boolean isForm(String formName);
}
