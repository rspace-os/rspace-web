package com.researchspace.model.field;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Filter;
import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.UniquelyIdentifiable;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.StructuredDocument;

/**
 * Natural sort order not consistent with equals ( uses column order of
 * template).
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Audited
public abstract class Field extends AbstractField implements Serializable,  UniquelyIdentifiable {

	private static final long serialVersionUID = 5835604593074331223L;

	private Field tempField;

	private Set<FieldAttachment> linkedMediaFiles = new HashSet<>();
	
	private List<ListOfMaterials> listsOfMaterials = new ArrayList<>();

	/**
	 * 
	 * Bidirectional association between media files and fields. This is the
	 * owning side of the relationship.
	 * 
	 * @return return possibly empty but non-null set of linked media files.
	 */
	@OneToMany(mappedBy = "field", cascade = CascadeType.ALL)
	@JsonIgnore
	@Filter(name = "fieldAttachmentNotDeleted")
	public Set<FieldAttachment> getLinkedMediaFiles() {
		return linkedMediaFiles;
	}

	/*
	 * For hibernate only . Client code should use add/removeMediaFileLinks to
	 * ensure all associations are correct.
	 */
	void setLinkedMediaFiles(Set<FieldAttachment> linkedMediaFiles) {
		this.linkedMediaFiles = linkedMediaFiles;
	}

	/**
	 * Creates an association between this field and an {@link EcatMediaFile}. <br/>
	 * If the FieldAttachment already exists, and was marked deleted, then adding  the media file
	 *  to the same field again will undelete the link. I.e it has the same effect as marking the association
	 *   as undeleted.
	 * 
	 * @param mediaFile
	 * @return <code>Optional<FieldAttachment></code>  if mediaFile is successfully added to this
	 *         field
	 */
	public Optional<FieldAttachment> addMediaFileLink(EcatMediaFile mediaFile) {
		FieldAttachment link = new FieldAttachment(this, mediaFile);
		boolean fieldAdded = mediaFile.getLinkedFields().add(link);
		boolean mediaAdded = linkedMediaFiles.add(link);
		if (!(mediaAdded && fieldAdded)) {
			setMediaFileLinkDeleted(mediaFile, false);
		}	
		return (fieldAdded && mediaAdded)?Optional.of(link):Optional.empty();
	}

	/**
	 * Removes an association between this field and an {@link EcatMediaFile}<br/>
	 * <p>
	 * Usually, it's better to mark the FieldAttachment as deleted rather than call this method.
	 * 
	 * @param mediaFile
	 * @return an optional removed FieldAttachment if removed successfully
	 */
	public Optional<FieldAttachment> removeMediaFileLink(EcatMediaFile mediaFile) {

		FieldAttachment toRemove = null;
		for (FieldAttachment fa : linkedMediaFiles) {
			if (fa.getMediaFile().equals(mediaFile)) {
				toRemove = fa;
			}
		}
		boolean mediaRemoved = linkedMediaFiles.remove(toRemove);
		boolean fieldRemoved = false;
		if (toRemove != null) {
			fieldRemoved = mediaFile.getLinkedFields().remove(toRemove);
			toRemove.setField(null);
			toRemove.setMediaFile(null);
		}
		
	   return (fieldRemoved && mediaRemoved)?Optional.of(toRemove):Optional.empty();
	}

	/**
	 * Marks the media file as deleted from this field.
	 * 
	 * @param mediaFile
	 * @param deleted
	 *            Set deleted (true) or not deleted (false).
	 * @return A optional {@link FieldAttachment}. Will be <code>null</code> if
	 *         <code>mediaFile</code> was not in the set of
	 *         <code>linkedMediaFiles</code> in this object.
	 */
	public Optional<FieldAttachment> setMediaFileLinkDeleted(EcatMediaFile mediaFile, boolean deleted) {
		return linkedMediaFiles.stream().filter(fa->fa.getMediaFile().equals(mediaFile))
		      .map(fa->fa.setDeleted(deleted))
		      .findFirst();
	}

	private StructuredDocument structuredDocument;

	protected Field() {
		setValidating(true);
	}

	@Transient
	public GlobalIdentifier getOid() {
		return new GlobalIdentifier(GlobalIdPrefix.FD, getId());
	}

	@ManyToOne
	public StructuredDocument getStructuredDocument() {
		return structuredDocument;
	}

	public void setStructuredDocument(StructuredDocument structuredDocument) {
		this.structuredDocument = structuredDocument;
	}

	/**
	 * Main public API method to retrieve field data as a String, regardless of
	 * subtype or the the database field used to store the data.
	 * 
	 * @return The data in this field as a String.
	 */
	@Transient
	@FullTextField(analyzer = "structureAnalyzer", name = "fieldData")
	public String getFieldData() {
		return getData();
	}

	/**
	 * Main public API method to set data. If this object has
	 * <code> isValidating() == true</code>, the argument data should be valid
	 * against the template in order to prevent data corruption.<br/>
	 * E.g., A safe call is :
	 * 
	 * <pre>
	 * if (field.isValidating &amp;&amp; field.validate(data)) {
	 * 	field.setFieldData(data);
	 * }
	 * </pre>
	 * 
	 * @param fieldData
	 * @throws IllegalArgumentException
	 *             if isValidating()== true and fieldData does not validate.
	 */
	public void setFieldData(String fieldData) {
		if (isValidating() && !validate(fieldData)) {
			throw new IllegalArgumentException("Field data [" + fieldData + "] invalid against template");
		}
		setData(fieldData);
		// notify as an auditable change.
		if (getStructuredDocument() != null) {
			getStructuredDocument().notifyDelta(DeltaType.FIELD_CHG, DeltaType.FIELD_CHG + "-" + getName());
		}
	}

	/**
	 * Gets a temporary field, may be <code>null</code>.
	 * 
	 * @return
	 */
	@OneToOne(cascade = CascadeType.ALL)
	public Field getTempField() {
		return tempField;
	}

	public void setTempField(Field tempField) {
		this.tempField = tempField;
	}

	/**
	 * 
	 * Bidirectional association between inventory list of materials and fields. 
	 * 
	 * @return return possibly empty but not-null list of list of materials connected to this field.
	 */
	@OneToMany(mappedBy = "elnField", cascade = CascadeType.ALL)
	@JsonIgnore
	@OrderBy(value = "id")
	public List<ListOfMaterials> getListsOfMaterials() {
		return listsOfMaterials;
	}

	/*
	 * For hibernate only. 
	 */
	void setListsOfMaterials(List<ListOfMaterials> loms) {
		listsOfMaterials = loms;
	}
	
	public void addListOfMaterials(ListOfMaterials lom) {
		listsOfMaterials.add(lom);
		lom.setElnField(this);
	}
	
	public void removeListOfMaterials(ListOfMaterials lom) {
		listsOfMaterials.remove(lom);
		lom.setElnField(null);
	}
	
	@Override
	public String toString() {
		String data = "";
		if (getFieldData() != null && getFieldData().length() > 15) {
			data = getFieldData().substring(0, 15);
			data = data + "...";
		} else {
			data = getFieldData();
		}
		String sd = "";
		if (structuredDocument != null) {
			sd = structuredDocument.getName();
		}
		return "Field [id=" + getId() + ", structuredDocument=" + sd + ", name=" + getName() + ", modificationDate="
				+ getModificationDate() + ", data=" + data + "]";
	}

	/**
	 * Iterates through fieldList and for each field tries to get temp field and
	 * add it to result list.
	 * 
	 * @param fieldList
	 * @param addOrgFieldIfNoTempField
	 *            decides whether original field should be added to result if it
	 *            has no temp field
	 * @return List<Field> new list containing temp fields, or mixture of temp
	 *         and nonTemp
	 */
	public static List<Field> getNewListOfTempFields(List<Field> fieldList, boolean addOrgFieldIfNoTempField) {
		List<Field> newFieldList = new ArrayList<>();

		if (fieldList != null) {
			for (Field field : fieldList) {
				if (field.getTempField() != null) {

					Field tempfield = field.getTempField();
					field.setFieldData(tempfield.getFieldData());
					newFieldList.add(field);

				} else {
					if (addOrgFieldIfNoTempField) {
						newFieldList.add(field);
					}
				}
			}
		}
		return newFieldList;
	}

	/**
	 * For each field in fieldList if the field has a temp field attached, and
	 * temp field was modified after modificationDate, then add temp field to
	 * result;
	 * 
	 * @param fieldList
	 * @param modificationDate
	 * @return List<Field> temp fields modified after modificationDate
	 */
	public static List<Field> getTempFieldsModifiedAfter(List<Field> fieldList, Long modificationDate) {
		List<Field> newFieldList = new ArrayList<>();
		for (Field field : fieldList) {
			if (field.getTempField() != null && field.getTempField().getModificationDate() > modificationDate) {
				Field tempfield = field.getTempField();
				field.setFieldData(tempfield.getFieldData());
				newFieldList.add(field);
			}
		}
		return newFieldList;
	}

	/**
	 * Checks whether there any field from fieldList has a temporary field
	 * attached.
	 * 
	 * @param fieldList
	 * @return true if there is field on fieldList with tempField attached
	 */
	public static boolean listContainsTempField(List<Field> fieldList) {		
		if (fieldList != null) {
			return fieldList.stream().anyMatch(field->field.getTempField() != null);
		}
		return false;
	}



	@Transient
	public boolean isMandatory() {
		return _getFieldForm().isMandatory();
	}

	@Transient
	public boolean isMandatoryStateSatisfied() {
		if (isMandatory()) {
			return StringUtils.isNotEmpty(getData());
		}
		return true;
	}

}
