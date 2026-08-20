package com.researchspace.model.views;

import java.util.Set;

import com.researchspace.model.Group;
import com.researchspace.model.dto.UserPublicInfo;

/**
 * Basic POJO of user information for display in the UI, on Directory page.
 */
public class PublicUserList {

	private UserPublicInfo userInfo;
	private Set<Group> groups;
	private String shortProfileText;
	private String orcidId;

	/**
	 * Default constructor for frameworks
	 */
	public PublicUserList() {
	}

	public UserPublicInfo getUserInfo() {
		return userInfo;
	}

	public void setUserInfo(UserPublicInfo userInfo) {
		this.userInfo = userInfo;
	}

	public Set<Group> getGroups() {
		return groups;
	}

	public void setGroups(Set<Group> groups) {
		this.groups = groups;
	}

	public String getShortProfileText() {
		return shortProfileText;
	}

	public void setShortProfileText(String shortProfileText) {
		this.shortProfileText = shortProfileText;
	}

	public String getOrcidId() {
		return orcidId;
	}

	public void setOrcidId(String orcidId) {
		this.orcidId = orcidId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((userInfo == null) ? 0 : userInfo.hashCode());
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
		PublicUserList other = (PublicUserList) obj;
		if (userInfo == null) {
			if (other.userInfo != null)
				return false;
		} else if (!userInfo.equals(other.userInfo))
			return false;
		return true;
	}

}
