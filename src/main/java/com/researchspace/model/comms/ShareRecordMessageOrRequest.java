package com.researchspace.model.comms;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.researchspace.model.User;

/**
 * Extension of MessageOrRequest to store information about requests concerning
 * share records. <br/>
 * Currently for use only on PublicCloud.
 */
@Entity
public class ShareRecordMessageOrRequest extends MessageOrRequest implements Serializable {

	private static final long serialVersionUID = 1558243965799375646L;
	private User target;
	private String permission;

	protected ShareRecordMessageOrRequest() {
		super(MessageType.REQUEST_SHARE_RECORD);
	}

	@ManyToOne
	public User getTarget() {
		return target;
	}

	public void setTarget(User target) {
		this.target = target;
	}

	/**
	 * The permission (read/write) of the document that is being shared
	 * 
	 * @return
	 */
	public String getPermission() {
		return permission;
	}

	public void setPermission(String permission) {
		this.permission = permission;
	}

	@Override
	public String toString() {
		return "ShareRecordMessageOrRequest [target=" + target + ", permission=" + permission + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((permission == null) ? 0 : permission.hashCode());
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
		ShareRecordMessageOrRequest other = (ShareRecordMessageOrRequest) obj;
		if (permission == null) {
			if (other.permission != null) {
				return false;
			}
		} else if (!permission.equals(other.permission)) {
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
