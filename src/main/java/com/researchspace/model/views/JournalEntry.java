package com.researchspace.model.views;

import java.util.Date;

import com.researchspace.model.EditStatus;
import com.researchspace.model.SignatureInfo;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RSForm;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Model used by journal entry to render record content.
 */
@Getter
@Setter
@NoArgsConstructor
public class JournalEntry {

	private Long id;
	private String name;
	private String html;
	private Date creationDate;
	private Date modificationDate;
	/**
	 * The position the record was found in the list, journal viewer needs this so it knows where to
	 * begin and end searches.
	 * 
	 * @return
	 */
	private Integer position;
	private String tags;
	private String tagMetaData;

	private boolean canSign = false;
	private boolean canWitness = false;
	private boolean canShare = false;

	private boolean signed = false;
	private boolean witnessed = false;
	private SignatureInfo signatureInfo;
	
	private String globalId;
	private String baseURL;
	
	private String formName;
	private long formIconId;

	private EditStatus editStatus;

	public JournalEntry(BaseRecord document, String html) {
		this(document.getName(), html);
		this.id = document.getId();
		this.globalId = document.getGlobalIdentifier();
		this.creationDate = document.getCreationDate();
		this.modificationDate = document.getModificationDateAsDate();

		if (document.isStructuredDocument()) {
			RSForm form = document.asStrucDoc().getForm();
			if (form != null) {
				this.formName = form.getName();
				this.formIconId = form.getIconId();
			}
		}
	}

	public JournalEntry(String message, String html) {
		this.name = message;
		this.html = html;
	}

}
