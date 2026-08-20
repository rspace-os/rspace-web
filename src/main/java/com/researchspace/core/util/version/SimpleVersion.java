package com.researchspace.core.util.version;

import java.io.Serializable;

/**
 * A simple value object to encapsulate versioning information for an object.
 * <br/>
 * Equals, hashcode and ordering are all based on the underlying version value.
 * <br/>
 * This object is effectively immutable for use in public API and is therefore
 * thread-safe.
 */

public class SimpleVersion implements Comparable<SimpleVersion>, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7550485788061906267L;
	private final Long version;
	/**
	 * Representation of an unkonwn version.
	 */
	public static final SimpleVersion UNKNOWN_VERSION = new SimpleVersion(Long.MIN_VALUE);

	/**
	 * Main constructor.
	 * 
	 * @param version
	 *            A non-null {@link Long}
	 * @throws if
	 *             argument is <code>null</code>.
	 */
	public SimpleVersion(Number version) {
		super();
		if (version == null) {
			throw new IllegalArgumentException();
		}
		this.version = version.longValue();
	}

	public SimpleVersion() {
		super();
		this.version = 0L;
	}

	public Long getVersion() {
		return version;
	}

	/**
	 * Increments the version number and returns a new {@link SimpleVersion}
	 * object with the incremented version.
	 * 
	 * @return A <b>new </b> Version object with the incremented version number.
	 */
	public SimpleVersion increment() {
		Long newversion = version + 1;
		return new SimpleVersion(newversion);
	}

	@Override
	public int compareTo(SimpleVersion other) {
		return this.version.compareTo(other.version);
	}

	/**
	 * Boolean test for relative ordering of two versions
	 * 
	 * @param other
	 *            A non-<code>null</code> Version
	 * @return <code>true</code> if this object's version is before the other's
	 *         version.
	 */
	public boolean before(SimpleVersion other) {
		return this.version < other.version;
	}

	/**
	 * Boolean test for relative ordering of two versions
	 * 
	 * @param other
	 *            A non-<code>null</code> Version
	 * @return <code>true</code> if this object's version is after the other's
	 *         version.
	 */
	public boolean after(SimpleVersion other) {
		return this.version > other.version;
	}

	@Override
	public String toString() {
		return "Version [version=" + version + "]";
	}

	/**
	 * Provides a String representation of the version for display purposes.
	 * 
	 * @return
	 */
	public String asString() {
		return version.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((version == null) ? 0 : version.hashCode());
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
		SimpleVersion other = (SimpleVersion) obj;
		if (version == null) {
			if (other.version != null) {
				return false;
			}
		} else if (!version.equals(other.version)) {
			return false;
		}
		return true;
	}

}
