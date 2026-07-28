package com.researchspace.model.record;

import com.researchspace.model.TaggableElnRecord;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Size;

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilterFactory;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.shiro.crypto.hash.Hash;
import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;

import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.model.Group;
import com.researchspace.model.Version;
import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldType;

/**
 * Equality is based on creator name and creator date.
 */
@Entity
@Audited
@Indexed
public class StructuredDocument extends Record implements TaggableElnRecord {

	/** Default name when creating new Structured Document */
	public static final String DEFAULT_NAME = "Untitled document";
	
	/** Default name when creating new Structured Document */
	public static final int MAX_TAG_LENGTH = 8000;
	
	/**
	 * Tag separator in docTag field
	 */
	public static final String TAG_DELIMITER = ",";

	/** Default width of RSpace viewer in browser */
	public static final int DEFAULT_WIDTH = 884;

	private static final long serialVersionUID = -2902057800012543299L;
	private boolean temporaryDoc = false;

	private StructuredDocument template;

	@IndexedEmbedded
	private List<Field> fields = new ArrayList<>();
	private RSForm form;
	private boolean markedForVersionIncrement = false;
	private Version userVersion = new Version(0L);
	private Delta delta = new Delta();

	@Setter
	private String docTag;
	@Setter
	private String tagMetaData;

	@Transient
	// caches to prevent unnecessary lookups
	protected Notebook notebook;

	/**
	 * Public constructor. In production, documents should be created using the
	 * RecordFactory, which enforces correct construction.
	 * 
	 * @param form
	 * @throws IllegalArgumentException if form is not 'NORMAL' type
	 */
	public StructuredDocument(RSForm form) {
		Validate.isTrue(FormType.NORMAL.equals(form.getFormType()),
				String.format("Structured documents can only be created from"
				+ "forms of 'NORMAL' type; this form (id=%d) is %s", form.getId(), form.getFormType()));
		this.form = form;
		setIconId(form.getIconId());
	}
	
	/**
	 * Public constructor. In production, documents should be created using the
	 * RecordFactory, which enforces correct construction.
	 * 
	 * @param form
	 */
	public StructuredDocument(RSForm form, ImportOverride importOveride) {
		super(importOveride);
		this.form = form;
		setIconId(form.getIconId());
	}

	/** For hibernate or record factory */
	protected StructuredDocument() { }

    /**
     * If this document was created from a template, this field stores the 
     *  template.<br>
     *  Otherwise is null
     * @return
     */
	@ManyToOne(fetch = FetchType.LAZY)
	public StructuredDocument getTemplate(){
		return template;
	}
	/*
	 * Package-scoped for hibernate. Client code  should use validating method
	 * setTemplateSource which asserts that the argument is indeed a template.
	 */
	void setTemplate(StructuredDocument template) {
		this.template = template;
	}

	/**
	 * 
	 * @param template
	 * @throws IllegalArgumentException if <code>template</<code> is not a template
	 */
	public void setTemplateSource(StructuredDocument template) {
		if (template != null) {
			Validate.isTrue(template.isTemplate(), "Reference to a template must have type 'TEMPLATE'");
		}
		this.template = template;
	}

	public boolean isTemporaryDoc() {
		return temporaryDoc;
	}

	/**
	 * Sets this record as being temporary, i.e., during an autosave.
	 * 
	 * @param temporary
	 */
	public void setTemporaryDoc(boolean temporary) {
		this.temporaryDoc = temporary;
	}

	/**
	 * The version of the document that will be presented to the user (as
	 * opposed to <em> audit versions </em>, which are global revisions for
	 * database auditing).
	 * 
	 * @return
	 */
	@Embedded
	@AuditTrailProperty(name = "userVersion", properties = "version")
	public Version getUserVersion() {
		return userVersion;
	}

	/**
	 * Sets the Version of this document. Normally clients needn't call this
	 * method to incremetn the version following a change, this is handled
	 * internally. <br/>
	 * This method exists to set the version explicitly e.g., on restoration
	 * from an audit.
	 * 
	 * @param userVersion
	 */
	public void setUserVersion(Version userVersion) {
		assert (userVersion != null);
		this.userVersion = userVersion;
	}

	/**
	 * Increments version only if this document is flagged to be incrementable.
	 * <p>
	 * Otherwise, does nothing.
	 * 
	 */
	public void incrementVersion() {
		if (isMarkedForVersionIncrement()) {
			setUserVersion(userVersion.increment());
			setMarkedForVersionIncrement(false);
		}
	}

	/**
	 * Convenience method to get all text fields in a document
	 * 
	 * @return a possibly empty but non-null list of fields of
	 *         {@link FieldType#TEXT}
	 */
	@Transient
	public List<Field> getTextFields() {
		List<Field> rc = new ArrayList<>();
		for (Field f : getFields()) {
			if (FieldType.TEXT.equals(f.getType())) {
				rc.add(f);
			}
		}
		return rc;
	}

	/**
	 * @return
	 */
	@Transient
	boolean isMarkedForVersionIncrement() {
		return markedForVersionIncrement;
	}

	/**
	 * Use this to mark version increment rather than update version directly,
	 * so that there is never more than one version increment per database save.
	 * 
	 * @param markedForVersionIncrement
	 */
	void setMarkedForVersionIncrement(boolean markedForVersionIncrement) {
		this.markedForVersionIncrement = markedForVersionIncrement;
	}

	/**
	 * Overrides the default behaviour. This method marks the record as having
	 * been deleted. 
	 */
	public void setRecordDeleted(boolean isDeleted){
		super.setRecordDeleted(isDeleted);
		notifyDelta(deleted ? DeltaType.DELETED : DeltaType.UNDELETED);
	}

	/**
	 * Sets the name of this record. This name can be modified freely
	 */
	@Override
	public void setName(String name) {
		if (getName() == null || !getName().equals(name)) {
			super.setName(name);
			notifyDelta(DeltaType.RENAME);
		}
	}

	/*
	 * For Hibernate and JSPs
	 */
	@Embedded
	// RSPAC-380
	@AttributeOverrides({ @AttributeOverride(name = "deltaString", column = @Column(length = 2000)), })
	public Delta getDelta() {
		return delta;
	}

	@Transient
	public String getDeltaStr() {
		return delta.getDeltaString();
	}

	/**
	 * Clears this document of known changes. This is called by a Hibernate
	 * post-load listener to clear the state of the newly loaded object.
	 */
	public void clearDelta() {
		delta = new Delta();
		setMarkedForVersionIncrement(false);
	}

	public boolean hasAuditableDeltas() {
		return !delta.getDeltaMsg().isEmpty();
	}

	/**
	 * Provides notification that this document has changed significantly, to
	 * warrant a new audited version.<br/>
	 * Updates modification time, and will result in version number increment.
	 * 
	 * @param type
	 *            the {@link DeltaType} of the change
	 * @param msg
	 *            an optional message, can be null. If not supplied the message
	 *            will default to {@link DeltaType#toString()}.
	 */
	public void notifyDelta(DeltaType type, String msg) {

		if (StringUtils.isEmpty(msg)) {
			delta.addDeltaMsg(type.toString());
		} else {
			delta.addDeltaMsg(msg);
		}
		
		if (type.isIncrementVersion()) {
			setMarkedForVersionIncrement(true);
			getEditInfo().setModificationDateMillis(new Date().getTime());
		}
	}

	/**
	 * @param type
	 */
	public void notifyDelta(DeltaType type) {
		notifyDelta(type, "");
	}

	/**
	 * For Hibernate only
	 */
	void setDelta(Delta delta) {
		this.delta = delta;
	}

	/**
	 * Gets the template from which this document's structure is defined.
	 * 
	 * @return
	 */
	@ManyToOne
	public RSForm getForm() {
		return form;
	}

	@Transient
	@FullTextField(analyzer = "structureAnalyzer", name = "formName")
	@IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "form")))
	public String getFormName() {
		return form != null && form.getEditInfo() != null ? form.getEditInfo().getName() : null;
	}

	@Transient
	@FullTextField(analyzer = "structureAnalyzer", name = "formStableId")
	@IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "form")))
	public String getFormStableId() {
		return form != null ? form.getStableID() : null;
	}


	/**
     * This indexes on the name of the template which this record was created from
     * */
    @Transient
    @FullTextField(analyzer = "structureAnalyzer", name = "templateName")
    @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "template")))
    public String getTemplateName() {
	    return getTemplate() == null ? null : getTemplate().getEditInfo().getName();
    }


    /**
     * This indexes on the OID of the template which this record was created from
     * */
    @Transient
    @FullTextField(analyzer = "structureAnalyzer", name = "templateOid")
    @IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "template")))
    public String getTemplateOid() {
        return getTemplate() == null ? null : getTemplate().getOid().toString();
    }


	/**
	 * This is used by the auditing framework. It should not be used in general
	 * use since the correct form is set in at document creation time.
	 * 
	 * @param form
	 */
	public void setForm(RSForm form) {
		this.form = form;
	}

	/**
	 * Gets the list of Fields this document contains. If the collection is
	 * populated by the database, will be in natural sort order
	 * 
	 * @return
	 */
	@OneToMany(mappedBy = "structuredDocument", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy(value = "columnIndex")
	public List<Field> getFields() {
		return fields;
	}

	@Transient
	public int getFieldCount() {
		return fields.size();
	}

	public boolean addField(Field toAdd) {
		toAdd.setStructuredDocument(this);
		fields.add(toAdd);
		return true;
	}

	boolean removeField(Field toRemove) {
		toRemove.setStructuredDocument(null);
		return fields.remove(toRemove);
	}

	/**
	 * Deletes all Fields from this record, breaking both sides of the
	 * relationship.
	 */
	void removeAllFields() {
		List<Field> fields = getFields();
		setFields(new ArrayList<>());
		for (Field f : fields) {
			f.setStructuredDocument(null);
		}
	}

	/**
	 * Sets the fields for this document.
	 * 
	 * @param args
	 * @return <code>true</code> if fields were set
	 */
	public boolean setFields(List<Field> args) {
		this.fields = args;
		return true;
	}

	@Transient
	public String getConcatenatedFieldContent() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < fields.size(); i++) {
			sb.append(i);
			sb.append(fields.get(i).getFieldData());
		}
		return sb.toString();
	}
	
	@Transient
	public Hash getRecordContentHashForSigning() {
		return SecureStringUtils.getHashForSigning(getConcatenatedFieldContent());
	}

	/**
	 * Copies this record, any child records and its Fields. This method does
	 * not copy associated data such as image annotations, comments etc. To
	 * properly copy these, call RecordManagerImpl#copy
	 * <p/>
	 * <strong>Note</strong> that this method will also copy ACLs, so the copy
	 * will acquire the same permissions as the original. If this is not
	 * desired, set the ACL to null in the copy before adding to the Folder
	 * tree.
	 */
	public StructuredDocument copy() {
		StructuredDocument clone = copyNoFields();
		List<Field> copiedFields = new ArrayList<>();
		for (Field f : fields) {
			Field copy = (Field)f.shallowCopy();
			copy.setStructuredDocument(clone);
			copiedFields.add(copy);
		}
		clone.fields = copiedFields;
		clone.setDelta(delta.copy());

		return clone;
	}

	/**
	 * Shallow copy that does <b>NOT</b> copy c.r.m.Field objects.
	 * <p>
	 * <strong>Note</strong> that this method will also copy ACLs, so the copy
	 * will acquire the same permissions as the original. If this is not
	 * desired, set the ACL to null in the copy before adding to the Folder
	 * tree.
	 */
	public StructuredDocument copyNoFields() {
		StructuredDocument clone = new StructuredDocument(this.form);
        clone.setDocTag(this.docTag);
        clone.setTagMetaData(this.tagMetaData);
		this.shallowCopyRecordInfo(clone);
		clone.setMarkedForVersionIncrement(isMarkedForVersionIncrement());
		clone.setTemplate(this.getTemplate());
		return clone;
	}

	/**
	 * Boolean test for the innate ability of this document to be editable. Will
	 * return true unless the document is an Example type.
	 * 
	 * @return
	 */
	@Transient
	public boolean isEditable() {
		return true; // !RecordType.NORMAL_EXAMPLE.equals(getType());
	}

	/**
	 * Validates all field data against their forms.<br/>
	 * Documents should always be valid against their form specification. This
	 * method performs that validation.
	 * 
	 * @return An {@link ErrorList} of validation errors. If document is valid,
	 *         {@link ErrorList#hasErrorMessages()} will return
	 *         <code>false</code>.
	 * 
	 */
	public ErrorList validate() {
		ErrorList el = new ErrorList();
		for (Field f : getFields()) {
			el.addErrorList(f.getFieldForm().validate(f.getFieldData()));
		}
		return el;
	}

	@Transient
	public boolean isStructuredDocument() {
		return true;
	}

	/**
	 * is this structure document based on 'Basic Document' system form
	 */
	@Transient
	public boolean isBasicDocument() {
		if (form == null) {
			return false;
		}
		return form.isSystemForm() && form.getName().equals(RecordFactory.BASIC_DOCUMENT_FORM_NAME);

	}

	@Column(length = MAX_TAG_LENGTH)
	@Size(max = MAX_TAG_LENGTH)
	@FullTextField
	public String getDocTag() {
		return docTag;
	}

	@Lob
	public String getTagMetaData() {
		return tagMetaData;
	}

	/**
	 * Gets a field by its name
	 * 
	 * @param name
	 * @return The first Field found with that name, or <code>null</code> if no
	 *         such Field was found.
	 */
	public Field getField(String name) {
		Field res = null;
		for (Field field : fields) {
			if (field.getName().equals(name)) {
				res = field;
				break;
			}
		}
		return res;
	}

	/**
	 * @return
	 */
	@Transient
	public boolean isNotebookEntry() {
		if (notebook == null) {
			notebook = getParentNotebook();
		}
		return notebook != null;
	}

	/**
	 * Returns the notebook that this document belongs to, if there is any. If
	 * the document belongs to multiple notebooks (through sharing), it tries to
	 * returned one owned by the user whose document it is.
	 * 
	 * @return parent notebook or null if not a part of a notebook
	 */
	@Transient
	public Notebook getParentNotebook() {
		if (notebook != null) {
			return notebook;
		}
		for (BaseRecord br : getParentFolders()) {
			if (br.isNotebook()) {
				notebook = (Notebook) br;
				if (getOwner().equals(br.getOwner())) {
					break;
				}
			}
		}
		return notebook;
	}

	/**
	 * Returns the parent notebook, but ignores other people's notebooks when
	 * searching through parents. So if the standalone doc was shared into other
	 * people notebooks, the method will still return null.
	 * 
	 * @return
	 */
	@Transient
	public Notebook getNonSharedParentNotebook() {
		Notebook parentNotebook = getParentNotebook();
		if (getOwner().equals(parentNotebook.getOwner())) {
			return parentNotebook;
		}
		return null;
	}

	@Override
	@Transient
	protected GlobalIdPrefix getGlobalIdPrefix() {
		return GlobalIdPrefix.SD;
	}

	@Override
	@Transient
	public GlobalIdentifier getOidWithVersion() {
		return new GlobalIdentifier(getGlobalIdPrefix(), getId(), getUserVersion().getVersion());
	}

	public RecordInformation toRecordInfo() {
		RecordInformation recordInfo = super.toRecordInfo();
		recordInfo.setTemplateId(getId());
		recordInfo.setIconId(getIconId());
		recordInfo.setVersion(getUserVersion().getVersion());

		String type = null;
		if (hasType(RecordType.TEMPLATE)) {
			type = DOCUMENT_CATEGORIES.TEMPLATE;
		} else {
			type = DOCUMENT_CATEGORIES.STRUCTURED_DOCUMENT;
		}
		recordInfo.setType(type);

		return recordInfo;
	}
	/**
	 * Gets data from 1st field of document
	 * @return the data
	 * @throws IllegalStateException if document has no fields.
	 */
	@Transient
	public String getFirstFieldData(){
		if(fields.isEmpty()) {
			throw  new IllegalStateException("Document has no fields!");
		}
		return fields.get(0).getFieldData();
	}
	
	/**
	 * Boolean test as to whether this document is in a shared notebook. It calculates this as follows:
	 * <p>
	 *  <ul>
	 *    <li> Looks for a paths to the communal group folder
	 *     <li> If such a path exists, and the immediate parent is a notebook, return true
	 *     <li> Else return false
	 *  </ul>
	 * </p>
	 * @param group
	 * @return
	 */
	@Transient
	public boolean isDocumentInSharedNotebook(Group group) {
		if  (getParentNotebooks().size() > 0) {
			RSPath path = getShortestPathToParent(
					f -> f.getId() != null && f.getId().equals(group.getCommunalGroupFolderId()));
			if (!path.isEmpty()) {
				Optional<Folder> parentSharedNotebookOptional = path.getImmediateParentOf(this)
						.filter(p -> getParentNotebooks().contains(p));
				return parentSharedNotebookOptional.isPresent();
			}
		}
		return false;
	}

	private boolean allFieldsValid = true;

	// @Transient
	public boolean isAllFieldsValid() {
		return allFieldsValid;
	}

	public void setAllFieldsValid(boolean allFieldsValid) {
		this.allFieldsValid = allFieldsValid;
	}

}
