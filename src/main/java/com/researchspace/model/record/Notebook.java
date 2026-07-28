package com.researchspace.model.record;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.RecordType;

import lombok.NoArgsConstructor;

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

	/**
	 * Recursively copies this folder and all its subfolders and records to
	 * generate a complete copy.
	 * 
	 * @return The newly copied Folder.
	 * @throws IllegalAddChildOperation
	 */
	public Folder copy(User user, boolean recursiveCopyChildren) throws IllegalAddChildOperation {
		Notebook clone = new Notebook();
		copyBasicFolderFields(clone);
		doCopy(user, recursiveCopyChildren, clone);
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
	 * Transient method to return the number of notebook entries in the
	 * notebook.
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
