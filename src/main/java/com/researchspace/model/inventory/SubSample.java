package com.researchspace.model.inventory;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.permissions.RecordSharingACL;
import com.researchspace.model.units.Quantifiable;
import com.researchspace.model.units.QuantityInfo;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import jakarta.validation.ConstraintViolationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.Validate;
import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;

/**
 * Represents RSInventory SubSample.
 */
@Entity
@Audited
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@AuditTrailData(auditDomain = AuditDomain.INV_SUBSAMPLE)
@Indexed
public class SubSample extends MovableInventoryRecord implements Serializable, Quantifiable {

	private static final long serialVersionUID = 1867269597891360704L;

	@IndexedEmbedded(name = "extraFields")
	private List<ExtraField> extraFields = new ArrayList<>();

	@IndexedEmbedded
	private List<Barcode> barcodes = new ArrayList<>();

	private List<DigitalObjectIdentifier> identifiers = new ArrayList<>();

	@IndexedEmbedded(name = "files")
	private List<InventoryFile> files = new ArrayList<>();

	@IndexedEmbedded(name = "notes")
	private List<SubSampleNote> notes = new ArrayList<>();

	private SampleEntity sample;

	/* whether subsample was deleted implicitly as a part of sample deletion */
	private boolean deletedOnSampleDeletion;

	private QuantityInfo quantityInfo;

	public SubSample(SampleEntity sample) {
		setIconId(sample.getIconId());
		setSample(sample);
	}

	/** for hibernate & pagination criteria */
	public SubSample() { }
	
	@Transient
	@IndexedEmbedded
	@IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW, derivedFrom = @ObjectPath(@PropertyValue(propertyName = "sample")))
	@Override
	public User getOwner() {
		if (getSample() == null) {
			return null;
		}
		return getSample().getOwner();
	}

	/** Sharing  setup is delegated to parent sample. */
	@Override
	public InventorySharingMode getSharingMode() {
		if (getSample() == null) {
			return null;
		}
		return getSample().getSharingMode();
	}

	/** Sharing setup is delegated to parent sample. */
	@Override
	public RecordSharingACL getSharingACL() {
		if (getSample() == null) {
			return null;
		}
		return getSample().getSharingACL();
	}

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "unitId", column = @Column(name = "quantityUnitId")),
			@AttributeOverride(name = "numericValue", column = @Column(name = "quantityNumericValue", precision = 19, scale = 3))
	})
	public QuantityInfo getQuantityInfo() {
		return quantityInfo;
	}

	/* marked 'protected' so concrete subclass methods are used by the code outside */
	protected void setQuantityInfo(QuantityInfo quantityInfo) {
		if (quantityInfo != null && quantityInfo.getUnitId() != null) {
			if (BigDecimal.ZERO.compareTo(quantityInfo.getNumericValue()) > 0) {
				throw new IllegalArgumentException("Trying to set negative record quantity: " + quantityInfo.getNumericValuePlainString());
			}
		}
		this.quantityInfo = quantityInfo;
	}

	@Override
	@Transient
	public Integer getUnitId() {
		return getQuantityInfo().getUnitId();
	}

	@Override
	@Transient
	public BigDecimal getNumericValue() {
		return getQuantityInfo().getNumericValue();
	}

	@Transient
	public QuantityInfo getQuantity() {
		return this.getQuantityInfo();
	}
	
	public void setQuantity(QuantityInfo quantityInfo) {
		Validate.notNull(quantityInfo, "Cannot assign null quantity to SubSample");
		this.setQuantityInfo(quantityInfo);
		// when copying, sample will be null
		if (sample != null) {
			sample.recalculateTotalQuantity();
		}
	}

	/**
	 * @return the list of extra fields of this SubSample, including deleted fields.
	 */
	@OneToMany(mappedBy = "subSample", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy(value = "id")
	@Override
	protected List<ExtraField> getExtraFields() {
		return extraFields;
	}

	protected void setExtraFields(List<ExtraField> extraFields) {
		this.extraFields = extraFields;
		refreshActiveExtraFields();
	}

	/**
	 * @return the list of barcodes of this SubSample, including deleted fields.
	 */
	@OneToMany(mappedBy = "subSample", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy(value = "id")
	@Override
	protected List<Barcode> getBarcodes() {
		return barcodes;
	}

	protected void setBarcodes(List<Barcode> barcodes) {
		this.barcodes = barcodes;
		refreshActiveBarcodes();
	}

	@OneToMany(mappedBy = "subSample", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy(value = "id")
	@Override
	protected List<DigitalObjectIdentifier> getIdentifiers() {
		return identifiers;
	}

	protected void setIdentifiers(List<DigitalObjectIdentifier> identifiers) {
		this.identifiers = identifiers;
		refreshActiveIdentifiers();
	}

	/**
	 * @return the list of files attached to this SubSample
	 */
	@OneToMany(mappedBy = "subSample", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy(value = "id")
	protected List<InventoryFile> getFiles() {
		return files;
	}

	protected void setFiles(List<InventoryFile> files) {
		this.files = files;
		refreshActiveAttachedFiles();
	}
	
	/**
	 * @return the list of notes saved for this SubSample
	 */
	@OneToMany(mappedBy = "subSample", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy(value = "id")
	public List<SubSampleNote> getNotes() {
		return notes;
	}
	
	public SubSampleNote addNote(String content, User creator) {
		SubSampleNote note = new SubSampleNote(content, creator);
		note.setSubSample(this);
		notes.add(note);
		return note;
	}
	
	public SubSampleNote addNote(SubSampleNote note) {
		note.setSubSample(this);
		notes.add(note);
		return note;
	}

	@ManyToOne(cascade = { CascadeType.MERGE })
	@JoinColumn(nullable = false)
	public SampleEntity getSample() {
		return sample;
	}

	@Override
	@Transient
	public SampleTemplate getLinkedSampleTemplate() {
		SampleEntity parent = getSample();
		return parent == null ? null : parent.getLinkedSampleTemplate();
	}

	@Transient
	@GenericField
	@IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "sample")))
	public Long getParentSampleId() {
		if (getSample() != null) {
			return getSample().getId();
		}
		return null;
	}

	@Transient
	@Override
	public GlobalIdPrefix getGlobalIdPrefix() {
		return GlobalIdPrefix.SS;
	}

	@Transient
	@Override
	public InventoryRecord.InventoryRecordType getType() {
		return InventoryRecordType.SUBSAMPLE;
	}
	
	SubSample shallowCopy() {
		SubSample copy = new SubSample();
		shallowCopyBasicFields(copy);
		if (getQuantityInfo() != null) {
			copy.setQuantityInfo(getQuantityInfo().copy());
		}
		return copy;
	}

	/**
	 * Copy using default name mapping which appends '_COPY' to the name of the copy
	 *
	 * @param currentUser user to set as a creator of the copy
	 */
	@Override
	public SubSample copy(User currentUser) {
		return copy(this::defaultNameCopy, this.getQuantity().copy(), currentUser);
	}
	
	/**
	 * 
	 * @param nameMapper A custom name-mapper to generate name for the new copy.
	 * @param newAmount new amount - must be comparable quantity to existing quantity
	 * @param currentUser user that will be marked as a creator of a subsample
	 * @return
	 */
	public SubSample copy(Function<SubSample, String> nameMapper, QuantityInfo newAmount, User currentUser) {
		SubSample copy = shallowCopy();
		getSample().addSubSample(copy);
		
		copy.setCreatedBy(currentUser.getUsername());
		copy.setModifiedBy(currentUser.getUsername());
		copy.setQuantity(newAmount);
		
		copy.setName(nameMapper.apply(this));
		copy.setImageFileProperty(getImageFileProperty());
		copy.setThumbnailFileProperty(getThumbnailFileProperty());
		for (SubSampleNote note: getNotes()) {
			SubSampleNote noteCopy = note.shallowCopy();
			noteCopy.setCreatedBy(note.getCreatedBy());
			copy.addNote(noteCopy);
		}
		return copy;
	}

	@PrePersist
	@PreUpdate
	public void validateBeforeSave() {
		if (getSample() == null) {
			throw new ConstraintViolationException("Cannot save SubSample that has no connected Sample", null);
		}

		/* subsamples have to be stored somewhere, with a few exceptions */
		if (getParentLocation() == null && !getSample().isTemplate() && !isDeleted()) {
			throw new ConstraintViolationException("Cannot save SubSample as it's not stored inside any container", null);
		}
	}

	static final Set<String> RESERVED_FIELD_NAMES =
			Collections.unmodifiableSet(
					Stream.concat(
									InventoryRecord.RESERVED_FIELD_NAMES.stream(),
									Stream.of("quantity", "sample", "notes"))
							.collect(Collectors.toSet()));

	@Override
	@Transient
	public Set<String> getReservedFieldNames() {
		return RESERVED_FIELD_NAMES;
	}

}
