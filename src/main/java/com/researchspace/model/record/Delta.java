package com.researchspace.model.record;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;

/**
 * Encapsulates information regarding if an change has been made warranting an
 * update to the user version number, or if the revision history should be
 * updated. <br>
 * This object maintains a list of changes. Equality is based on the message.
 */
@Embeddable
public class Delta implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7896372951779080258L;
	private String deltaString;

	@Override
	public String toString() {
		return "Delta [getChanges()=" + getDeltaString() + "]";
	}

	// avoid duplicates but preserve insertion order
	private Set<String> deltaMsges = new LinkedHashSet<>();

	public Delta() {
		super();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((getDeltaMsg() == null) ? 0 : getDeltaMsg().hashCode());
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
		Delta other = (Delta) obj;
		if (getDeltaMsg() == null) {
			if (other.getDeltaMsg() != null) {
				return false;
			}
		} else if (!getDeltaMsg().equals(other.getDeltaMsg())) {
			return false;
		}
		return true;
	}

	@Transient
	Set<String> getDeltaMsg() {
		return deltaMsges;
	}

	/**
	 * Gets a concatenated String of the changes recorded in this object.
	 * 
	 * @return
	 */
	String _getChange() {

		StringBuffer sb = new StringBuffer();
		for (String msg : deltaMsges) {
			sb.append(msg).append(",");
		}
		String rc = sb.toString();
		if (rc.endsWith(",")) {
			rc = rc.substring(0, rc.lastIndexOf(","));
		}
		return rc;

	}

	public String getDeltaString() {
		return (deltaString == null) ? "" : deltaString;

	}

	void setDeltaString(String s) {
		if (!StringUtils.isEmpty(s)) {
			String[] m = s.split(",");
			deltaMsges = new LinkedHashSet<>(Arrays.asList(m));
		}
		this.deltaString = s;
	}

	void addDeltaMsg(String deltaMsg) {
		deltaMsges.add(deltaMsg);
		this.deltaString = _getChange();
	}

	/*
	 * Used for document copying.
	 * 
	 * @return
	 */
	Delta copy() {
		Delta cpy = new Delta();
		for (String msg : deltaMsges) {
			cpy.addDeltaMsg(msg);
		}
		return cpy;
	}

}
