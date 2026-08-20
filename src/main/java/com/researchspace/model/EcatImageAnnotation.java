package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.hibernate.envers.Audited;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.Record;

@Entity
@Table(name = "ecatImageAnnotation")
@XmlRootElement
@Audited
public class EcatImageAnnotation implements Serializable, IFieldLinkableElement {

	/** */
	private static final long serialVersionUID = -80925793441933791L;
	private Long id;
	private Long imageId;
	private Long fieldId;
	private byte[] data;
	private String annotations;
	private String textAnnotations;

	private Record record;

	private int width;
	private int height;

	/**
	 * Default constructor
	 */
	public EcatImageAnnotation() {
	}

	/**
	 * Constructor to use to set required fields for object.
	 * 
	 * @param fieldId
	 * @param decodedBytes
	 * @param annotations
	 */
	public EcatImageAnnotation(long fieldId, Record record, byte[] decodedBytes, String annotations) {
		setParentId(fieldId);
		setRecord(record);
		setData(decodedBytes);
		setAnnotations(annotations);
	}

	public EcatImageAnnotation shallowCopy() {
		EcatImageAnnotation copy = new EcatImageAnnotation();
		copy.setImageId(getImageId());
		copy.setData(getData());
		copy.setTextAnnotations(getTextAnnotations());
		copy.setAnnotations(getAnnotations());

		return copy;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "ecat_image_annotation_gen")
	@TableGenerator(name = "ecat_image_annotation_gen", table = "hibernate_sequences",
			pkColumnName = "sequence_name", valueColumnName = "next_val", allocationSize = 50)
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getImageId() {
		return imageId;
	}

	public void setImageId(Long imageId) {
		this.imageId = imageId;
	}

	/**
	 * id of the field containing this annotation
	 * 
	 * @return
	 */
	public Long getParentId() {
		return fieldId;
	}

	public void setParentId(Long parentId) {
		this.fieldId = parentId;
	}

	/**
	 * Unidirectional relation to a record holding this annotated image
	 * 
	 * @return
	 */
	@ManyToOne
	@JsonIgnore
	public Record getRecord() {
		return record;
	}

	public void setRecord(Record record) {
		this.record = record;
	}

	/**
	 * Boolean tests for whether this annotation is a sketch or not; currently
	 * just tests for nullity of imageID.
	 * 
	 * @return <code>true</code>if this annotation is a sketch.
	 */
	@Transient
	@JsonIgnore
	public boolean isSketch() {
		return imageId == null;
	}

	@Lob
	public byte[] getData() {
		return data == null ? null : data;
	}

	public void setData(byte[] data) {
		this.data = data == null ? null : data;
	}

	@Override
	public String toString() {
		return "EcatImageAnnotation [id=" + id + ", imageId=" + imageId + ", fieldId=" + fieldId + "]";
	}

	@Lob
	public String getAnnotations() {
		return annotations;
	}

	public void setAnnotations(String annotations) {
		this.annotations = annotations;
	}

	public String getTextAnnotations() {
		return textAnnotations;
	}

	public void setTextAnnotations(String textAnnotations) {
		this.textAnnotations = textAnnotations;
	}

	@Override
	@Transient
	@JsonIgnore
	public GlobalIdentifier getOid() {
		return new GlobalIdentifier(GlobalIdPrefix.IA, getId());
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

}
