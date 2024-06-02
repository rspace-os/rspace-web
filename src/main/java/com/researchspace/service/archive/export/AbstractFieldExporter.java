package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchiveUtils;
import com.researchspace.archive.model.ArchiveModelFactory;
import com.researchspace.linkedelements.FieldElementLinkPair;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.service.DiskSpaceLimitException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
abstract class AbstractFieldExporter<T extends IFieldLinkableElement> {

  void setSupport(FieldExporterSupport support) {
    this.support = support;
  }

  ArchiveModelFactory archiveModelFactory;
  FieldExporterSupport support;

  /**
   * Full constructor required.
   *
   * @param support
   */
  AbstractFieldExporter(FieldExporterSupport support) {
    this();
    this.support = support;
  }

  /*
   * For testing
   */
  AbstractFieldExporter() {
    this.archiveModelFactory = new ArchiveModelFactory();
  }

  /**
   * Template method to frame the export process for field-linkable elements. It's not declared
   * final so as to allow subclasses to replace the implementation if need be, but if possible this
   * implementation should be used. <br>
   * Abstract classes defined in this class provide callbacks for subclass specialisation.
   *
   * @param context
   * @param itemPair
   * @return <code>true</code> if item was included in export,<code>false</code> otherwise
   * @throws DiskSpaceLimitException
   */
  public boolean export(FieldExportContext context, FieldElementLinkPair<T> itemPair) {
    T item = itemPair.getElement();
    item = getRevisionIfAvailable(context.getRevision(), item);
    if (!context.getExportRecordList().containsFieldAttachment(item.getOid())) {
      log.warn(
          "fieldExport: Item  [{}] is not in whitelisted set of field attachments", item.getOid());
      return false;
    }
    try {
      String newLink = getReplacementUrl(context, item);
      log.debug("fieldExport: relative links made for {} - {}", item.getId(), newLink);
      ArchivalField archiveField = context.getArchiveField();
      String fielddata = doUpdateLinkText(itemPair, newLink, context);
      archiveField.setFieldData(fielddata);
      createFieldArchiveObject(item, newLink, context);
      return true;
    } catch (Exception ex) {
      if (ex instanceof DiskSpaceLimitException) {
        throw (DiskSpaceLimitException) ex;
      }
      log.warn(
          "fieldExport: Error exporting fieldElement with id [{}]: {}",
          item.getId(),
          ex.getMessage());
      return false;
    }
  }

  /**
   * Create {@link ArchivalGalleryMetadata} object.
   *
   * @param item
   * @param archiveLink
   * @param context
   */
  abstract void createFieldArchiveObject(T item, String archiveLink, FieldExportContext context);

  /**
   * Update the field data in the export with the replacement relative link
   *
   * @param itemPair
   * @param replacementUrl The new relative link
   * @param context
   * @return The updated field content.
   */
  abstract String doUpdateLinkText(
      FieldElementLinkPair<T> itemPair, String replacementUrl, FieldExportContext context);

  /**
   * Get a replacement, relative link to put in the inserted document
   *
   * @param context
   * @param item
   * @return
   */
  abstract String getReplacementUrl(FieldExportContext context, T item)
      throws URISyntaxException, IOException;

  T getRevisionIfAvailable(Number revisionNo, T object) {
    T revision = null;
    if (revisionNo != null) {
      AuditedEntity<?> audited =
          support.getObjectForRevision(object.getClass(), object.getId(), revisionNo);
      if (audited != null) {
        revision = (T) audited.getEntity();
        if (object instanceof EcatMediaFile) {
          // audit tables don't store parents, so set them from the
          // object (RSPAC-1136)
          Set<RecordToFolder> parents = ((EcatMediaFile) object).getParents();
          ((EcatMediaFile) revision).setParents(parents);
        }
      }
    }
    if (revision == null) {
      revision = object; // use current version
    }
    return revision;
  }

  /** Given a prefix, appends with secure random string to generate unguessable URL RSPAC-570 */
  String getUniqueName(String prefix) {
    return ArchiveUtils.getUniqueName(prefix);
  }

  String updateLink(String fdata, String target, String replacement) {
    String targetToUse = target;
    if (!fdata.contains(target)) {
      // if target is not found it might be because field data contains
      // '&amp;' rather than '&'
      targetToUse = target.replace("&", "&amp;");
    }
    return fdata.replace(targetToUse, replacement);
  }
}
