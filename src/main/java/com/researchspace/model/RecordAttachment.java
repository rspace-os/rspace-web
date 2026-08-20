package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

import com.researchspace.model.record.Record;

/**
 * Similar to FieldAttachment, this is a join table for Record/EcatMedia
 * associations. This needs to be an entity, rather than defined as ManyToMAny
 * since otherwise the revision history mechanism fails.
 */
@Entity
@Audited
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordAttachment implements Serializable {

	private static final long serialVersionUID = 5952786176086693144L;

	private Long id;

	private Record record;

	private EcatMediaFile mediaFile;

	public RecordAttachment(Record record, EcatMediaFile mediaFile) {
		this.record = record;
		this.mediaFile = mediaFile;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "record_attachment_gen")
	@TableGenerator(name = "record_attachment_gen", table = "hibernate_sequences",
			pkColumnName = "sequence_name", valueColumnName = "next_val", allocationSize = 50)
	public Long getId() {
		return id;
	}

	void setId(Long id) {
		this.id = id;
	}

	@ManyToOne
	public Record getRecord() {
		return record;
	}

	public void setRecord(Record record) {
		this.record = record;
	}

	@ManyToOne
	public EcatMediaFile getMediaFile() {
		return mediaFile;
	}

	public void setMediaFile(EcatMediaFile mediaFile) {
		this.mediaFile = mediaFile;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((record == null) ? 0 : record.hashCode());
		result = prime * result + ((mediaFile == null) ? 0 : mediaFile.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		RecordAttachment other = (RecordAttachment) obj;
		if (record == null) {
			if (other.record != null) {
				return false;
			}
		} else if (!record.equals(other.record)) {
			return false;
		}
		if (mediaFile == null) {
			if (other.mediaFile != null) {
				return false;
			}
		} else if (!mediaFile.equals(other.mediaFile)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "RecordAttachment [id=" + id + ", record=" + record + ", mediaFile=" + mediaFile + "]";
	}

}
