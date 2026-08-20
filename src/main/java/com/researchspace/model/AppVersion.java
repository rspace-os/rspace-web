package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.Embeddable;

import lombok.NoArgsConstructor;
import org.apache.commons.lang3.Validate;

import com.researchspace.core.util.version.SemanticVersion;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Class or encapsulating application versions.
 * <p/>
 * This is just a wrapper around the {@link SemanticVersion} utility class to
 * add persistent behaviour.<br/>
 * Otherwise delegates through to {@link SemanticVersion} for most behaviour and
 * validation Objects should be immutable once constructed, the private setters
 * are for Hibernate, as these objects can be embedded in tables.
 *
 */
@Embeddable
@Setter(value = AccessLevel.PRIVATE)
@Getter
@EqualsAndHashCode(of = { "major", "minor", "qualifier", "suffix" })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppVersion implements Comparable<AppVersion>, Serializable {

	private static final long serialVersionUID = 4613435214269341106L;

	private Integer major = null, minor = null, qualifier = null;
	private String suffix = null;

	@Override
	public String toString() {
		return getString();
	}

	/**
	 * Main public constructor
	 * @param version
	 */
	public AppVersion(SemanticVersion version) {
		Validate.notNull(version, "Version cannot be null");
		this.major = version.getMajor();
		this.minor = version.getMinor();
		this.qualifier = version.getQualifier();
		this.suffix = version.getSuffix();
	}

	/**
	 * Compares based on major, minor, qualifier, then suffix.
	 */
	@Override
	public int compareTo(AppVersion other) {
		return this.toSemanticVersion().compareTo(other.toSemanticVersion());
	}

	SemanticVersion toSemanticVersion() {
		return new SemanticVersion((major != null) ? major : 0, minor, qualifier, suffix);
	}

	/**
	 * Boolean test for whether this object is older (a lower version number)
	 * than the argument version.
	 * 
	 * @param version
	 * @return
	 */
	public boolean isOlderThan(AppVersion version) {
		return toSemanticVersion().isOlderThan(version.toSemanticVersion());
	}

	/**
	 * Boolean test for whether this object is newer (a higher version number)
	 * than the argument version.
	 * 
	 * @param version
	 * @return
	 */
	public boolean isNewerThan(AppVersion version) {
		return toSemanticVersion().isNewerThan(version.toSemanticVersion());
	}

	/**
	 * Generates a String version in major.minor.qualifier.suffix syntax
	 * 
	 * @return
	 */
	public String getString() {
		return toSemanticVersion().getString();
	}

	public boolean isSameOrNewerThan(AppVersion fromInclusive) {
		return toSemanticVersion().isSameOrNewerThan(fromInclusive.toSemanticVersion());
	}

}
