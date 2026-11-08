package com.researchspace.model.record;

import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.RecordType;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

@Entity
@XmlRootElement
@Audited
@Indexed
@AuditTrailData(auditDomain = AuditDomain.NOTEBOOK)
@NoArgsConstructor
public class Notebook extends Folder {

  private static final long serialVersionUID = -7870462732662559290L;

  public Notebook(ImportOverride override) {
    super(override);
  }

  /** Default name for new notebook */
  public static final String DEFAULT_NOTEBOOK_NAME = "Untitled Notebook";

  @Override
  public Notebook copy() {
    Notebook clone = new Notebook();
    copyBasicFolderFields(clone);
    shallowCopyRecordInfo(clone);
    clone.setSharingACL(null);
    return clone;
  }

  @Transient
  @Override
  public boolean isNotebook() {
    return hasType(RecordType.NOTEBOOK);
  }

  private long entryCount = 0;

  /**
   * Transient method to return the number of notebook entries in the notebook.
   *
   * @return
   */
  @Transient
  public long getEntryCount() {
    return entryCount;
  }

  public void setEntryCount(long count) {
    entryCount = count;
  }

  @Transient
  @Override
  public String getRecordInfoType() {
    return DOCUMENT_CATEGORIES.NOTEBOOK;
  }

  @Transient
  @Override
  protected GlobalIdPrefix getGlobalIdPrefix() {
    return GlobalIdPrefix.NB;
  }
}
