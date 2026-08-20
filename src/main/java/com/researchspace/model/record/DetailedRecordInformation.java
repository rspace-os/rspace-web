package com.researchspace.model.record;

import com.researchspace.model.TaggableElnRecord;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.Transient;

import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.SignatureStatus;
import com.researchspace.model.core.GlobalIdentifier;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO object to be used for universal 'get info' dialog.
 * 
 * Combines basic information about record (from {@link RecordInformation}) with
 * additional about sharing status, workspace path etc.
 */
@Getter	
@Setter 
public class DetailedRecordInformation extends RecordInformation {

	private String path;
	private String tags;

	private boolean isShared;
	private boolean isImplicitlyShared;

	private Boolean signed;
	private Boolean witnessed;
	private SignatureStatus signatureStatus;

	private Map<String, String> sharedGroupsAndAccess = new HashMap<>(); /* e.g. mkGroup - EDIT */
	private Map<String, String> sharedUsersAndAccess = new HashMap<>(); /* e.g. matthias - VIEW */
	private Map<String, String> sharedNotebooksAndOwners = new HashMap<>(); /* e.g. NB5215 - matthias */ 
	private Map<String, String> implicitShares = new HashMap<>(); /* e.g. NB5215 - group23 */ 
	
	private int linkedByCount;
	
	private String status;
	private String currentEditor;

	// These fields make sense only for structured documents
	private String templateFormName;
	private GlobalIdentifier templateFormId;

	private String templateName;
	private String templateOid;

	public DetailedRecordInformation() { }

	public DetailedRecordInformation(RSForm form) {
		super(form);
		setType(DOCUMENT_CATEGORIES.FORM);
	}

	public DetailedRecordInformation(BaseRecord baseRecord) {
		super(baseRecord);

		// some fields are being populated by toRecordInfo() method
		RecordInformation basicInfo = baseRecord.toRecordInfo();
		setType(basicInfo.getType());
		setExtension(basicInfo.getExtension());
		setWidthResized(basicInfo.getWidthResized());
		setHeightResized(basicInfo.getHeightResized());
		setOriginalImageOid(basicInfo.getOriginalImageOid());
		setSize(basicInfo.getSize());
		setVersion(basicInfo.getVersion());

		setSigned(baseRecord.isSigned());
		setWitnessed(baseRecord.isWitnessed());

		if (baseRecord.isStructuredDocument()) {
			StructuredDocument sd = (StructuredDocument) baseRecord;
			setTemplateFormName(sd.getFormName());
			setTemplateFormId(sd.getForm().getOid());
		} else if (baseRecord.isEcatDocument()) {
			EcatDocumentFile doc = (EcatDocumentFile) baseRecord;
			setThumbnailId(doc.getThumbNail() != null ? doc.getThumbNail().getId() : null);
		}
		if (baseRecord.isTaggable()) {
			setTags(((TaggableElnRecord) baseRecord).getDocTag());
		}
	}

	/**
	 * 
	 * @param sharingInfo a list of group sharing information about the item, and its parent notebooks
	 */
	@Transient
	public void calculateSharedStatuses(RecordInfoSharingInfo sharingInfo) {
		
		// it's not shared
		if (sharingInfo == null || !sharingInfo.hasSharingInfo()) {
			return;
		}

		for (RecordGroupSharing recordShare : sharingInfo.getDirectShares()) {
			if (recordShare.getPublicLink() == null) {
				setShared(true);
			} else {
				continue;
			}
			AbstractUserOrGroupImpl sharee = recordShare.getSharee();
			Folder targetNotebook = recordShare.getTargetFolder();
			if (targetNotebook != null) {
				String notebookGlobalId = targetNotebook.getGlobalIdentifier();
				String notebookOwnerDisplayName = targetNotebook.getOwner().getDisplayName();
				sharedNotebooksAndOwners.put(notebookGlobalId, notebookOwnerDisplayName);
			} else if (sharee.isUser()) {
				sharedUsersAndAccess.put(sharee.getDisplayName(), recordShare.getPermType().getDisplayName());
			} else {
				sharedGroupsAndAccess.put(sharee.getDisplayName(), recordShare.getPermType().getDisplayName());
			}
		}
		// iterate over any parent shared notebooks. RSPAC-545
		for (RecordGroupSharing indirectShare : sharingInfo.getImplicitShares()) {
			String parentSharedNotebookGlobalId = indirectShare.getShared().getGlobalIdentifier();
			String shareeDisplayName = indirectShare.getSharee().getDisplayName();

			implicitShares.merge(parentSharedNotebookGlobalId, shareeDisplayName,(existing, added)->existing + ", " + added );
			this.isImplicitlyShared = true;
		}
	}

}
