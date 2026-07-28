package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.Embeddable;

import com.researchspace.core.util.version.SimpleVersion;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A simple value object to encapsulate versioning information for an object.<br/>
 * Equals, hashcode and ordering are all based on the underlying version value.<br/>
 * This object is effectively immutable for use in public API and is therefore thread-safe.
 */
@Embeddable
@Setter(value=AccessLevel.PRIVATE)
@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class Version implements Comparable<Version>, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7550485788061906267L;
	private Long version;

	/**
	 * Main constructor.
	 * 
	 * @param version
	 *            A non-null {@link Long}
	 * @throws 
	 *             if argument is <code>null</code>.
	 */
	public Version(Number version) {
		super();
		if (version == null) {
			throw new IllegalArgumentException();
		}
		this.version = version.longValue();
	}

	/**
	 * Returns a new {@link Version} object with the incremented
	 * version. Does not change the state of this object.
	 * 
	 * @return A <b>new </b> Version object with the incremented version number.
	 */
	public Version increment() {
		return new Version(new SimpleVersion(this.version).increment().getVersion());
	}

	@Override
	public int compareTo(Version other) {
		return this.version.compareTo(other.version);
	}

	/**
	 * Boolean test for relative ordering of two versions
	 * 
	 * @param other
	 *            A non-<code>null</code> Version
	 * @return <code>true</code> if this object's version is before the other's version.
	 */
	public boolean before(Version other) {
		return new SimpleVersion(this.version).before(new SimpleVersion(other.version));
	}

	/**
	 * Boolean test for relative ordering of two versions
	 * 
	 * @param other
	 *            A non-<code>null</code> Version
	 * @return <code>true</code> if this object's version is after the other's version.
	 */
	public boolean after(Version other) {
		return new SimpleVersion(this.version).after(new SimpleVersion(other.version));
	}

	/**
	 * Provides a String representation of the version for display purposes.
	 * 
	 * @return
	 */
	public String asString() {
		return version.toString();
	}


}
