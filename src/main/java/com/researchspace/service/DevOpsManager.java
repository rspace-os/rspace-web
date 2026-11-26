package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import java.util.List;

public interface DevOpsManager {

  /**
   * Checks the record pointed to by globalId parameter, and if it has a known fixable problem, runs
   * the fix (or only describes a fix, if 'runFix' param is false)
   *
   * @param oid globalId of record to check
   * @param subject user attempting to run the fix (usually sysadmin)
   * @param runFix whether the fix should be applied, or just described
   * @return long message summarizing what was done, for displaying to the user who runs the fix
   */
  String fixRecord(GlobalIdentifier oid, User subject, boolean runFix);

  /**
   * Finds candidate fields for {@link #reportAnnotatedImagesInField(Long)}: current text fields of
   * non-deleted, non-temporary documents whose content contains <code>data-type="annotation"</code>
   * (a LIKE scan over all text field content). Runs read-only.
   *
   * @return candidate field ids in ascending order
   */
  List<Long> findFieldIdsWithAnnotatedImages();

  /**
   * Checks each annotated image in a current text field for a missing or deleted link
   * (FieldAttachment) to its Gallery image (SUPPORT-536, RSDEV-1837). Runs read-only: nothing is
   * written, not even the document state Hibernate would otherwise flush on load.
   *
   * <p>Images whose link is active are not reported, nor is anything for a field that is no longer
   * a current text field. Each entry says whether {@link #relinkAnnotatedImagesInField(Long)} would
   * restore the link or skip it, and why.
   *
   * @param fieldId id of a field returned by {@link #findFieldIdsWithAnnotatedImages()}
   * @return one entry per annotated image whose link is missing or deleted
   */
  List<AnnotatedImageRelink> reportAnnotatedImagesInField(Long fieldId);

  /**
   * As {@link #reportAnnotatedImagesInField(Long)}, but restores each link that isn't skipped.
   *
   * <p>A link is restored only if the document owner may read the Gallery image, so a repair never
   * grants access the owner did not already have. Templates and deleted Gallery images are skipped.
   *
   * @param fieldId id of a field returned by {@link #findFieldIdsWithAnnotatedImages()}
   * @return one entry per annotated image whose link was missing or deleted
   */
  List<AnnotatedImageRelink> relinkAnnotatedImagesInField(Long fieldId);
}
