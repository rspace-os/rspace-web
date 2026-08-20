package com.researchspace.core.util.version;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class or encapsulating versions in format
 * <code>major.minor.qualifier-suffix</code> or
 * <code>major.minor.qualifier.suffix</code>.
 * <p/>
 * Objects should be immutable once constructed
 */
public class SemanticVersion implements Comparable<SemanticVersion>, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4613435214269341106L;

	private Integer major = null, minor = null, qualifier = null;
	private String suffix = null;

	@Override
	public String toString() {
		return getString();
	}

	/**
	 * Defines valid version strings, which can be of type:
	 * <ul>
	 * <li>3
	 * <li>3.2
	 * <li>3.4.5
	 * <li>3.4.5.string suffix
	 * <li>3.4.5-suffix
	 * </ul>
	 */
	public static final Pattern VERSION = Pattern.compile("(\\d+)(\\.(\\d+))?(\\.(\\d+))?(([\\.|\\-])(\\S+))?");

	/**
	 * 
	 */
	public static final SemanticVersion UNKNOWN_VERSION = new SemanticVersion("0");

	/**
	 * Boolean test for whether the argument string is a valid version string
	 * 
	 * @param versionString
	 * @return
	 */
	public static boolean isValid(String versionString) {
		Matcher m = VERSION.matcher(versionString);
		if (!m.matches()) {
			return false;
		}
		return true;
	}

	/**
	 * Creates an {@link SemanticVersion} object from the supplied string
	 * 
	 * @param version
	 *            A valid version string
	 */
	public SemanticVersion(String version) {

		final int MINOR = 3, QUALIFIER = 5, SUFFIX = 8;

		Matcher m = VERSION.matcher(version);
		if (!m.matches()) {
			/*
			 * if the supplied string is not a valid AppVersion, e.g., if
			 * isValid == <code>false</code>.
			 */
			throw new IllegalArgumentException(version + " isn't  a valid version string");
		}
		this.major = Integer.parseInt(m.group(1));
		if (m.group(MINOR) != null) {
			this.minor = Integer.parseInt(m.group(MINOR));
		} else {
			this.minor = null;
		}
		if (m.group(QUALIFIER) != null) {
			this.qualifier = Integer.parseInt(m.group(QUALIFIER));
		} else {
			this.qualifier = null;
		}
		if (m.group(SUFFIX) != null) {
			this.suffix = m.group(SUFFIX);
		} else {
			this.suffix = null;
		}

	}

	public SemanticVersion(Integer major, Integer minor, Integer qualifier, String suffix) {
		super();
		if (major == null) {
			throw new IllegalArgumentException("Major version cannot be null.");
		}
		if (minor == null && (qualifier != null || suffix != null)) {
			throw new IllegalArgumentException("minor version cannot be null if qualifier or suffix set..");
		}
		if (minor == null && qualifier == null && suffix != null) {
			throw new IllegalArgumentException("qualifier version cannot be null if  suffix set..");
		}
		this.major = major;
		this.minor = minor;
		this.qualifier = qualifier;
		this.suffix = suffix;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((major == null) ? 0 : major.hashCode());
		result = prime * result + ((minor == null) ? 0 : minor.hashCode());
		result = prime * result + ((qualifier == null) ? 0 : qualifier.hashCode());
		result = prime * result + ((suffix == null) ? 0 : suffix.hashCode());
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
		SemanticVersion other = (SemanticVersion) obj;
		if (major == null) {
			if (other.major != null) {
				return false;
			}
		} else if (!major.equals(other.major)) {
			return false;
		}

		if (minor == null) {
			if (other.minor != null) {
				return false;
			}
		} else if (!minor.equals(other.minor)) {
			return false;
		}

		if (qualifier == null) {
			if (other.qualifier != null) {
				return false;
			}
		} else if (!qualifier.equals(other.qualifier)) {
			return false;
		}

		if (suffix == null) {
			if (other.suffix != null) {
				return false;
			}
		} else if (!suffix.equals(other.suffix)) {
			return false;
		}
		return true;
	}

	public Integer getMajor() {
		return major;
	}

	public Integer getMinor() {
		return minor;
	}

	public Integer getQualifier() {
		return qualifier;
	}

	public String getSuffix() {
		return suffix;
	}

	/**
	 * Compares based on major, minor, qualifier, then suffix.
	 */
	@Override
	public int compareTo(SemanticVersion other) {
		int rc = major.compareTo(other.major);
		if (rc == 0 && minor != null && other.minor != null) {
			rc = minor.compareTo(other.minor);
		}
		if (rc == 0 && qualifier != null && other.qualifier != null) {
			rc = qualifier.compareTo(other.qualifier);
		}
		if (rc == 0 && suffix != null && other.suffix != null) {
			rc = suffix.compareTo(other.suffix);
		}
		return rc;
	}

	/**
	 * Boolean test for whether this object is older (a lower version number)
	 * than the argument version.
	 * 
	 * @param version
	 * @return
	 */
	public boolean isOlderThan(SemanticVersion version) {
		return this.compareTo(version) < 0;
	}

	/**
	 * Boolean test for whether this object is newer (a higher version number)
	 * than the argument version.
	 * 
	 * @param version
	 * @return
	 */
	public boolean isNewerThan(SemanticVersion version) {
		return this.compareTo(version) > 0;
	}

	/**
	 * Generates a String version in major.minor.qualifier.suffix syntax
	 * 
	 * @return
	 */
	public String getString() {
		StringBuffer sb = new StringBuffer();
		sb.append(major);
		if (minor != null) {
			sb.append("." + minor);
		}
		if (qualifier != null) {
			sb.append("." + qualifier);
		}
		if (suffix != null) {
			sb.append("." + suffix);
		}
		return sb.toString();
	}

	public boolean isSameOrNewerThan(SemanticVersion fromInclusive) {
		return this.compareTo(fromInclusive) >= 0;
	}

}
