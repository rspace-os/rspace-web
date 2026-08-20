package com.researchspace.model.comms;

import java.util.List;

import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;

public class CreateGroupMessageOrRequestCreationConfiguration extends MsgOrReqstCreationCfg {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7985237752727024346L;
	
	public CreateGroupMessageOrRequestCreationConfiguration(User creator, IPermissionUtils permUtils) {
		super(creator, permUtils);
	}
	
	private User creator;
	private User target;
	private String groupName;
	private List<String> emails;
	
	public User getCreator() {
		return creator;
	}

	public void setCreator(User creator) {
		this.creator = creator;
	}
	
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
	
	public List<String> getEmails() {
		return emails;
	}

	public void setEmails(List<String> emails) {
		this.emails = emails;
	}
}
