package com.researchspace.model.inventory.field;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.hibernate.envers.Audited;

import com.researchspace.model.core.GlobalIdPrefix;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@Audited
@Getter
@Setter
@EqualsAndHashCode(of = {"id"})
public class InventoryLink implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "target_globalid", nullable = false, length = 64)
	private String targetGlobalId;

	@Column(name = "target_prefix", nullable = false, length = 8)
	@Enumerated(EnumType.STRING)
	private GlobalIdPrefix targetPrefix;

	@Column(name = "target_db_id", nullable = false)
	private Long targetDbId;

	@Column(name = "version_pin")
	private Long versionPin;

	/**
	 * The Envers revision (REV) this link's pin resolved to, captured at pin time. Null means the
	 * link is "latest" and resolves dynamically to the newest revision in the target's audit table.
	 * Paired with {@link #versionPin}: versionPin is the user-facing version for display, this is the
	 * exact audit-row key used to load the snapshot.
	 */
	@Column(name = "target_revision_id")
	private Long targetRevisionId;

	@Column(name = "relation_type", nullable = false, length = 64)
	private String relationType;

	@Column(name = "created_at", nullable = false)
	@Temporal(TemporalType.TIMESTAMP)
	private Date createdAt;

	@Column(name = "modified_at", nullable = false)
	@Temporal(TemporalType.TIMESTAMP)
	private Date modifiedAt;

	@Column(nullable = false)
	private boolean deleted;

	@PrePersist
	void prePersist() {
		Date now = new Date();
		if (createdAt == null) {
			createdAt = now;
		}
		modifiedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		modifiedAt = new Date();
	}

	/**
	 * Creates a new, unpersisted copy of this link carrying the same target identity, version pin,
	 * relation type and deletion state. The copy has a null id and no timestamps (those are set on
	 * persist), so it can be attached to a copied field without sharing this managed instance.
	 */
	public InventoryLink shallowCopy() {
		InventoryLink copy = new InventoryLink();
		copy.setTargetGlobalId(targetGlobalId);
		copy.setTargetPrefix(targetPrefix);
		copy.setTargetDbId(targetDbId);
		copy.setVersionPin(versionPin);
		copy.setTargetRevisionId(targetRevisionId);
		copy.setRelationType(relationType);
		copy.setDeleted(deleted);
		return copy;
	}

}
