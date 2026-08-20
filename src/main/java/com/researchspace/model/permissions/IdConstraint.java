package com.researchspace.model.permissions;

import com.researchspace.model.utils.Utils;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class IdConstraint implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Set<Long> ids = new TreeSet<>();

	public boolean satisfies(Long id) {
		return ids.contains(id);
	}

	/**
	 * Gets aunmodifiable ordered set of Ids
	 * 
	 * @return
	 */
	public Set<Long> getId() {
		return Collections.unmodifiableSet(ids);
	}

	/*
	 * MAkes a copy of the passed in set.
	 * 
	 * @param ids
	 */
	void setId(Set<Long> ids) {
		this.ids = new TreeSet<>(ids);
	}

	/**
	 * 
	 * @param ids
	 * @throws IllegalArgumentException
	 *             if the set is null.
	 */
	public IdConstraint(Set<Long> ids) {
		super();
		if (ids == null) {
			throw new IllegalArgumentException("id set is null");
		}
		setId(ids);
	}

	/**
	 * 
	 * @param id
	 *            A non-null database ID
	 */
	public IdConstraint(Long id) {
		ids = new TreeSet<>();
		ids.add(id);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((ids == null) ? 0 : ids.hashCode());
		return result;
	}

	public boolean isEquivalentTo(IdConstraint other) {
		for (Long id : other.ids) {
			if (!ids.contains(id)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IdConstraint other = (IdConstraint) obj;
		if (ids == null) {
			if (other.ids != null)
				return false;
		} else if (!ids.equals(other.ids))
			return false;
		return true;
	}

	public String toString() {
		return getString();
	}

	/**
	 * Gets a String representation for the permission string format.
	 * 
	 * @return
	 */
	public String getString() {
		StringBuffer sb = new StringBuffer();
		if (ids.isEmpty()) {
			return "";
		}
		sb.append(ConstraintPermissionResolver.IDS_PREFIX).append("=");
		for (Long l : ids) {
			sb.append(l).append(ConstraintPermissionResolver.LIST_SEPARATOR);

		}
		Utils.replaceTRailingSeparator(sb, ConstraintPermissionResolver.LIST_SEPARATOR);
		return sb.toString();
	}

}
