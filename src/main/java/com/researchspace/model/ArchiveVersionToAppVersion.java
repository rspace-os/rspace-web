package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Static lookup table for maintaining which archive schema versions are
 * compatible with which database versions.
 *
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchiveVersionToAppVersion implements Serializable {

	private static final long serialVersionUID = -1006132682146898592L;

	@Embedded
	@AttributeOverrides({ @AttributeOverride(name = "major", column = @Column(name = "toMajor", nullable = false)),
			@AttributeOverride(name = "minor", column = @Column(name = "toMinor")),
			@AttributeOverride(name = "qualifier", column = @Column(name = "toQualifier")),
			@AttributeOverride(name = "suffix", column = @Column(name = "toSuffix")), })
	private AppVersion toExclusive;
	// need this when embedding 2 instances of the same class to define new
	// column names
	@Embedded()
	@AttributeOverrides({ @AttributeOverride(name = "major", column = @Column(name = "fromMajor")),
			@AttributeOverride(name = "minor", column = @Column(name = "fromMinor")),
			@AttributeOverride(name = "qualifier", column = @Column(name = "fromQualifier")),
			@AttributeOverride(name = "suffix", column = @Column(name = "fromSuffix")), })
	private AppVersion fromInclusive;
	@Embedded()
	@AttributeOverride(name = "version", column = @Column(name = "schemaVersion", nullable = false))
	private Version schemaVersion;

	private String schemaName;

	@Override
	public String toString() {
		return "ArchiveVersionToAppVersion [fromInclusive=" + fromInclusive + ", toExclusive=" + toExclusive
				+ ", schemaVersion=" + schemaVersion + ", schemaName=" + schemaName + ", id=" + id + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fromInclusive == null) ? 0 : fromInclusive.hashCode());
		result = prime * result + ((schemaName == null) ? 0 : schemaName.hashCode());
		result = prime * result + ((schemaVersion == null) ? 0 : schemaVersion.hashCode());
		result = prime * result + ((toExclusive == null) ? 0 : toExclusive.hashCode());
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
		ArchiveVersionToAppVersion other = (ArchiveVersionToAppVersion) obj;
		if (fromInclusive == null) {
			if (other.fromInclusive != null) {
				return false;
			}
		} else if (!fromInclusive.equals(other.fromInclusive)) {
			return false;
		}

		if (schemaName == null) {
			if (other.schemaName != null) {
				return false;
			}
		} else if (!schemaName.equals(other.schemaName)) {
			return false;
		}

		if (schemaVersion == null) {
			if (other.schemaVersion != null) {
				return false;
			}
		} else if (!schemaVersion.equals(other.schemaVersion)) {
			return false;
		}

		if (toExclusive == null) {
			if (other.toExclusive != null) {
				return false;
			}
		} else if (!toExclusive.equals(other.toExclusive)) {
			return false;
		}
		return true;
	}

	@Column(nullable = false)
	public String getSchemaName() {
		return schemaName;
	}

	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	public Version getSchemaVersion() {
		return schemaVersion;
	}

	void setSchemaVersion(Version schemaVersion) {
		this.schemaVersion = schemaVersion;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	public Long getId() {
		return id;
	}

	void setId(Long id) {
		this.id = id;
	}

	/**
	 * Gets the version from which this archive version is compatible
	 * 
	 * @return
	 */
	public AppVersion getFromInclusive() {
		return fromInclusive;
	}

	public void setFromInclusive(AppVersion fromInclusive) {
		this.fromInclusive = fromInclusive;
	}

	/**
	 * Gets the version at which this archive version is incompatible
	 * 
	 * @return
	 */

	public AppVersion getToExclusive() {
		return toExclusive;
	}

	public void setToExclusive(AppVersion toExclusive) {
		this.toExclusive = toExclusive;
	}

}
