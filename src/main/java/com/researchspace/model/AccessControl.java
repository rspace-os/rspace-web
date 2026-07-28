package com.researchspace.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import com.researchspace.model.permissions.PermissionType;

/**
 * Embeddable persistable class for entity objects needing a Unix-type access
 * control information. Equality is based on the values of the enums comprising
 * the access control.
 * 
 * Default settings are :
 * <ul>
 * <li>OWNER - WRITE
 * <li>GROUP - NONE
 * <li>WORLD - NONE
 * </ul>
 */
@Embeddable
public class AccessControl implements Serializable {

	@Override
	public String toString() {
		return "AccessControl [ownerPermissionType=" + ownerPermissionType + ", groupPermissionType="
				+ groupPermissionType + ", worldPermissionType=" + worldPermissionType + "]";
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 6433083589179270457L;

	private PermissionType ownerPermissionType = PermissionType.WRITE;

	private PermissionType groupPermissionType = PermissionType.NONE;

	private PermissionType worldPermissionType = PermissionType.NONE;

	private static List<PermissionType> types = Arrays
			.asList(new PermissionType[] { PermissionType.NONE, PermissionType.READ, PermissionType.WRITE });

	/*
	 * Empty constructor
	 */
	public AccessControl() {
		super();
	}

	/**
	 * Sets permission types for each of owner, group and world access.<br/>
	 * Currently this just handles NONE,READ, WRITE permissions.
	 * 
	 * @param owner
	 * @param group
	 * @param world
	 */
	public AccessControl(PermissionType owner, PermissionType group, PermissionType world) {
		this.ownerPermissionType = owner;
		this.groupPermissionType = group;
		this.worldPermissionType = world;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((groupPermissionType == null) ? 0 : groupPermissionType.hashCode());
		result = prime * result + ((ownerPermissionType == null) ? 0 : ownerPermissionType.hashCode());
		result = prime * result + ((worldPermissionType == null) ? 0 : worldPermissionType.hashCode());
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
		AccessControl other = (AccessControl) obj;
		if (groupPermissionType != other.groupPermissionType) {
			return false;
		}
		if (ownerPermissionType != other.ownerPermissionType) {
			return false;
		}
		if (worldPermissionType != other.worldPermissionType) {
			return false;
		}
		return true;
	}

	@Enumerated(value = EnumType.STRING)
	public PermissionType getOwnerPermissionType() {
		return ownerPermissionType;
	}

	public void setOwnerPermissionType(PermissionType ownerPermissionType) {
		this.ownerPermissionType = ownerPermissionType;
	}

	@Enumerated(value = EnumType.STRING)
	public PermissionType getGroupPermissionType() {
		return groupPermissionType;
	}

	public void setGroupPermissionType(PermissionType groupPermissionType) {
		this.groupPermissionType = groupPermissionType;
	}

	@Enumerated(value = EnumType.STRING)
	public PermissionType getWorldPermissionType() {
		return worldPermissionType;
	}

	public void setWorldPermissionType(PermissionType worldPermissionType) {
		this.worldPermissionType = worldPermissionType;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	/**
	 * Boolean test to compare 2 ordered Permission types
	 * 
	 * @param allowed
	 * @param requested
	 * @return
	 */
	public static boolean isAllowed(PermissionType allowed, PermissionType requested) {
		int allowedIndx = types.indexOf(allowed);
		int desiredIndx = types.indexOf(requested);
		if (desiredIndx == -1 || allowedIndx == -1) {
			return false;
		}
		return allowedIndx >= desiredIndx;

	}

}
