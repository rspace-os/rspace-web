package com.researchspace.service.archive;

import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.service.AuditManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.archive.StoichiometryImporter.IdAndRevision;
import com.researchspace.service.archive.export.StoichiometryReader;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("stoichiometryImportRevisionFixupManager")
public class StoichiometryImportRevisionFixupManagerImpl
    implements StoichiometryImportRevisionFixupManager {

  private static final Logger log =
      LoggerFactory.getLogger(StoichiometryImportRevisionFixupManagerImpl.class);

  private static final String STOICHIOMETRY_ATTR = "data-stoichiometry-table";

  @Autowired private AuditManager auditManager;
  @Autowired private FieldManager fieldManager;

  private final StoichiometryReader reader = new StoichiometryReader();

  @Override
  public void fixupStoichiometryRevisions(ImportArchiveReport report, User user) {
    if (report.getImportedRecords().isEmpty()) {
      return;
    }
    for (BaseRecord record : report.getImportedRecords()) {
      if (!(record instanceof StructuredDocument)) {
        continue;
      }
      fixupFieldsForRecord(record.getId(), user);
    }
  }

  private void fixupFieldsForRecord(long recordId, User user) {
    List<Field> fields = fieldManager.getFieldsByRecordId(recordId, user);
    for (Field field : fields) {
      String fieldData = field.getFieldData();
      if (fieldData == null || !fieldData.contains(STOICHIOMETRY_ATTR)) {
        continue;
      }
      try {
        fixupField(field, user);
      } catch (Exception e) {
        log.warn(
            "Failed to fixup stoichiometry revisions for field id={} in record id={}",
            field.getId(),
            recordId,
            e);
      }
    }
  }

  private void fixupField(Field field, User user) {
    List<StoichiometryDTO> stoichiometries =
        reader.extractStoichiometriesFromFieldContents(field.getFieldData());
    boolean modified = false;
    String updatedData = field.getFieldData();

    for (StoichiometryDTO dto : stoichiometries) {
      Long stoichId = dto.getId();
      AuditedEntity<Stoichiometry> audited =
          auditManager.getNewestRevisionForEntity(Stoichiometry.class, stoichId);
      if (audited == null || audited.getRevision() == null) {
        log.warn(
            "Could not find Envers revision for stoichiometry id={}; "
                + "imported stoichiometry will have null revision",
            stoichId);
        continue;
      }
      IdAndRevision replacement = new IdAndRevision();
      replacement.id = stoichId;
      replacement.revision = audited.getRevision().longValue();
      updatedData =
          reader.createReplacementHtmlContentForTargetStoichiometryInFieldData(
              updatedData, dto, replacement);
      modified = true;
    }

    if (modified) {
      field.setFieldData(updatedData);
      fieldManager.save(field, user);
    }
  }
}
