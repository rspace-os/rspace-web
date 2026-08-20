package com.researchspace.model.audit;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;

import org.hibernate.envers.RevisionType;

import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;

/**
 * A simple convenience wrapper class around a revisioned {@link BaseRecord}
 * that contains revision information.<br/>
 */
public class AuditedRecord extends AuditedEntity<BaseRecord> implements Serializable {

	private static final long serialVersionUID = 2462500457031711015L;

	/**
	 * Sort by name asc
	 */
	public static final Comparator<AuditedRecord> NAME_COMPARATOR = new Comparator<>() {
		@Override
		public int compare(AuditedRecord o1, AuditedRecord o2) {
			return o1.getEntity().getName().toLowerCase().compareTo(o2.getEntity().getName().toLowerCase());
		}
	};
	/**
	 * Sort by creation date asc
	 */
	public static final Comparator<AuditedRecord> CREATION_COMPARATOR = new Comparator<>() {
		@Override
		public int compare(AuditedRecord o1, AuditedRecord o2) {
			return o1.getEntity().getCreationDateMillis().compareTo(o2.getEntity().getCreationDateMillis());
		}
	};

	/**
	 * Sort by modification date asc
	 */
	public static final Comparator<AuditedRecord> MODIFICATION_COMPARATOR = new Comparator<>() {
		@Override
		public int compare(AuditedRecord o1, AuditedRecord o2) {
			return o1.getEntity().getModificationDateMillis().compareTo(o2.getEntity().getModificationDateMillis());
		}
	};
	
	/**
	 * Sort by deletion date ASC. Null is always last
	 * 
	 */
	public static final Comparator<AuditedRecord> DELETED_COMPARATOR = new Comparator<>() {
		@Override
		public int compare(AuditedRecord o1, AuditedRecord o2) {
			if(o1.getDeletedDate() == null && o2.getDeletedDate() == null) {
				return 0;
			} else if (o1.getDeletedDate() == null) {
				return -1;
			} else if (o2.getDeletedDate() == null) {
				return 1;
			} else {
				return o1.getDeletedDate().compareTo(o2.getDeletedDate());
			}
		
		}
	};

	public AuditedRecord(BaseRecord sd, Number rev) {
		super(sd, rev);
	}

	/**
	 * For test purposes; in application code use a constructor with arguments
	 * to populate this object
	 */
	public AuditedRecord() {
	}

	/**
	 * Main constructor. Deleted date can be derived from either RecordToFolder or BaseRecord deleted dates
	 *  depending on the method of deletion.
	 * @param record
	 * @param revision
	 * @param revType
	 * @param deletedDate Can be null if not deleted.
	 */
	public AuditedRecord(BaseRecord record, int revision, RevisionType revType, Date deletedDate) {
		super(record, revision, revType, deletedDate);
	}

	public BaseRecord getRecord() {
		return (BaseRecord) getEntity();
	}

	/**
	 * Convenience method to retrieve the audited record as a
	 * <code>Structured Document</code> where it is known to be a
	 * <code>Structured Document</code>.
	 * 
	 * @throws ClassCastException
	 *             if the record wrapped by this class is not a
	 *             {@link StructuredDocument}
	 * @return
	 */
	public StructuredDocument getRecordAsDocument() {
		return (StructuredDocument) getEntity();
	}
}
