package com.researchspace.model.comms;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.researchspace.model.User;

/**
 * Extension of MessageOrRequest to store information about requests concerning
 * create group. This is for RSPAC-245 uc2 where user is nominating another user
 * to be a PI of a group.
 */
@Entity
public class CreateGroupMessageOrRequest extends MessageOrRequest implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2026986015989808191L;
	private User creator;
	private User target;
	private String groupName;
	private List<String> emails = new ArrayList<>();

	protected CreateGroupMessageOrRequest() {
		super(MessageType.REQUEST_CREATE_LAB_GROUP);
	}

	@ManyToOne
	public User getCreator() {
		return creator;
	}

	public void setCreator(User creator) {
		this.creator = creator;
	}

	@ManyToOne
	public User getTarget() {
		return target;
	}

	public void setTarget(User target) {
		this.target = target;
	}

	public String getGroupName() {
		return groupName;
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}

	@ElementCollection
	public List<String> getEmails() {
		return emails;
	}

	public void setEmails(List<String> emails) {
		this.emails = emails;
	}

	@Override
	public String toString() {
		return "CreateGroupMessageOrRequest [creator=" + creator + ", target=" + target + ", groupName=" + groupName
				+ ", emails=" + emails + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((creator == null) ? 0 : creator.hashCode());
		result = prime * result + ((emails == null) ? 0 : emails.hashCode());
		result = prime * result + ((groupName == null) ? 0 : groupName.hashCode());
		result = prime * result + ((target == null) ? 0 : target.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		CreateGroupMessageOrRequest other = (CreateGroupMessageOrRequest) obj;
		if (creator == null) {
			if (other.creator != null) {
				return false;
			}
		} else if (!creator.equals(other.creator)) {
			return false;
		}
		if (emails == null) {
			if (other.emails != null) {
				return false;
			}
		} else if (!emails.equals(other.emails)) {
			return false;
		}
		if (groupName == null) {
			if (other.groupName != null) {
				return false;
			}
		} else if (!groupName.equals(other.groupName)) {
			return false;
		}
		if (target == null) {
			if (other.target != null) {
				return false;
			}
		} else if (!target.equals(other.target)) {
			return false;
		}
		return true;
	}
}
