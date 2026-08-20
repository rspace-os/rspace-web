package com.researchspace.maintenance.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhiteListedSysAdminIPAddress {

	private String ipAddress;
	
	private String description ="";

	private Long id;

	public WhiteListedSysAdminIPAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	/**
	 * @param ipAddress
	 * @param description
	 */
	public WhiteListedSysAdminIPAddress(String ipAddress, String description) {
		this.ipAddress = ipAddress;
		this.description = description;
	}

	/**
	 * Human readable description for this IP address
	 * @return
	 */
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = StringUtils.abbreviate(description, 255);
	}

	@Override
	public String toString() {
		return "WhiteListedSysAdminIPAddress [ipAddress=" + ipAddress + ", description=" + description + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((ipAddress == null) ? 0 : ipAddress.hashCode());
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
		WhiteListedSysAdminIPAddress other = (WhiteListedSysAdminIPAddress) obj;
		if (ipAddress == null) {
			if (other.ipAddress != null)
				return false;
		} else if (!ipAddress.equals(other.ipAddress))
			return false;
		return true;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = StringUtils.abbreviate(ipAddress, 255);
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

}
