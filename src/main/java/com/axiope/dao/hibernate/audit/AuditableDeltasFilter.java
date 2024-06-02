package com.axiope.dao.hibernate.audit;

import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import java.rmi.server.RemoteCall;

/**
 * Audit filter that filters out insignificant changes to the object as defined by whether not a
 * document's hasAuditableDeltas() is <code>true</code> or <code>false</code>.
 */
public class AuditableDeltasFilter extends ObjectAuditFilter {

  /** */
  private static final long serialVersionUID = 1L;

  @Override
  public boolean filter(Object entity) {
    RemoteCall rc;
    if (entity instanceof StructuredDocument) {
      StructuredDocument sd = (StructuredDocument) entity;
      if (!sd.hasAuditableDeltas()) {
        return false;
      }
    } else if (Field.class.isAssignableFrom(entity.getClass())) {
      Field f = (Field) entity;
      if (f.getStructuredDocument() == null || !f.getStructuredDocument().hasAuditableDeltas()) {
        return false;
      }
    } else if (RecordToFolder.class.isAssignableFrom(entity.getClass())) {
      RecordToFolder rtf = (RecordToFolder) entity;
      BaseRecord br = rtf.getRecord();
      if (br != null && br.isStructuredDocument() && !br.asStrucDoc().hasAuditableDeltas()) {
        return false;
      }
    }
    return true;
  }
}
