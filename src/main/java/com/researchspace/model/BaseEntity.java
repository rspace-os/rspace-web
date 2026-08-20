package com.researchspace.model;

/* BaseEntity as atomic entity type.
 *@ sunny
 */

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

public abstract class BaseEntity implements Serializable {
	public static final long serialVersionUID = 3832626162173359411L;

	String uid;
	String name;
	String desc;
	String updateId;
	Date createDate, lastUpdate, deleteDate;

	public String getUID() {
		return uid;
	}

	public String getName() {
		return name;
	}

	public String getDesc() {
		return desc;
	}

	public String getUpdateId() {
		return updateId;
	}

	public Date getCreateDate() {
		return createDate == null ? new Date() : new Date(createDate.getTime());
	}

	public Date getLastUpdate() {
		return lastUpdate == null ? new Date() : new Date(lastUpdate.getTime());
	}

	public Date getDeleteDate() {
		return deleteDate == null ? new Date() : new Date(deleteDate.getTime());
	}

	public void setUID(String s) {
		uid = s;
	}

	public void setName(String s) {
		name = s;
	}

	public void setDesc(String s) {
		desc = s;
	}

	public void setUpdateId(String s) {
		updateId = s;
	}

	public void setCreateDate(Date dt) {
		this.createDate = dt == null ? null : new Date(dt.getTime());
	}

	public void setLastUpdate(Date dt) {
		this.lastUpdate = dt == null ? null : new Date(dt.getTime());
	}

	public void setDeleteDate(Date dt) {
		this.deleteDate = dt == null ? null : new Date(dt.getTime());
	}

	abstract public String createBespokeUID();

	// support method
	public String createRandomUUID() {
		return UUID.randomUUID().toString();
	}

	public String createStringUUID(String s) {
		return UUID.fromString(s).toString();
	}

	public String createByteUUID(byte[] bys) {
		return UUID.nameUUIDFromBytes(bys).toString();
	}
}
