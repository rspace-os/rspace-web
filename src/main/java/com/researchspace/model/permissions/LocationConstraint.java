package com.researchspace.model.permissions;

import java.io.Serializable;
import java.util.Arrays;

public class LocationConstraint implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	static final String DELIMITER = "/";

	static final String WILDCARD = "*";

	private String locationConstraint;

	String getLocationConstraint() {
		return locationConstraint;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((locationConstraint == null) ? 0 : locationConstraint.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LocationConstraint other = (LocationConstraint) obj;
		if (locationConstraint == null) {
			if (other.locationConstraint != null)
				return false;
		} else if (!locationConstraint.equals(other.locationConstraint))
			return false;
		return true;
	}

	public LocationConstraint(String locationConstraint) {
		super();
		this.locationConstraint = locationConstraint;
	}

	public boolean satisfies(LocationConstraint pc) {

		String[] segments = locationConstraint.split(DELIMITER);
		String[] toTest = pc.getLocationConstraint().split(DELIMITER);
		if (toTest.length > segments.length && !locationConstraint.contains(WILDCARD)) {
			return false;
		}

		int toTestsz = toTest.length;
		int LAST = segments.length - 1;
		for (int indx = 0; indx <= LAST; indx++) {
			if (indx >= toTestsz) {
				return false;
			}
			if (segments[indx].equals(toTest[indx])) {
				continue;
			}
			if (indx == LAST && segments[indx].equals(WILDCARD)) {
				return true;
			}
			if (segments[indx].equals(WILDCARD)) {

				String[] remainder = Arrays.copyOfRange(segments, indx + 1, segments.length);
				return compareRemainder(remainder, toTest);
			}

		}
		return true;

	}

	private boolean compareRemainder(String[] remainder, String[] toTest) {
		int j = toTest.length;
		if (j < remainder.length) {
			return false;
		}
		for (int i = remainder.length - 1; i >= 0; i--, j--) {
			if (!remainder[i].equals(toTest[j - 1])) {
				return false;
			}
		}
		return true;
	}

	public String getString() {
		return ConstraintPermissionResolver.LOCATION_PARAM_PREFIX + "=" + locationConstraint;
	}

}
