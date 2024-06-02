package com.axiope.dao.hibernate.audit;

import com.researchspace.model.FieldAttachment;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.record.Delta;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;

/**
 * Filters out temporary fields and records that are autosaved, and also records that have no
 * auditable changes assigned to them.
 */
public class PermanentEntityFilter extends ObjectAuditFilter {

  /** */
  private static final long serialVersionUID = 1L;

  @Override
  public boolean filter(Object entity) {
    if (entity instanceof StructuredDocument) {
      StructuredDocument sd = (StructuredDocument) entity;
      if (sd.isTemporaryDoc()) {
        return false;
      } else return sd.getDelta() == null || checkNoAuditDelta(sd);
    } else if (Field.class.isAssignableFrom(entity.getClass())) {
      Field f = (Field) entity;
      return f.getStructuredDocument() != null && f.getTempField() == null;
    } else if (FieldAttachment.class.isAssignableFrom(entity.getClass())) {
      FieldAttachment f = (FieldAttachment) entity;
      return f.getField() != null
          && f.getField().getStructuredDocument() != null
          && f.getField().getTempField() == null;
    } else if (entity instanceof RSForm) {
      RSForm sd = (RSForm) entity;
      return !sd.isTemporary() && sd.getTempForm() == null;
    } else if (FieldForm.class.isAssignableFrom(entity.getClass())) {
      FieldForm f = (FieldForm) entity;
      return f.getForm() != null && !f.isTemporary() && f.getTempFieldForm() == null;
    }
    return true;
  }

  private boolean checkNoAuditDelta(StructuredDocument sd) {
    Delta delta = sd.getDelta();
    return !delta.getDeltaString().contains(DeltaType.NOREVISION_UPDATE.name());
  }
}
