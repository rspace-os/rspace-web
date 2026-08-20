package com.researchspace.model.comms;

import java.util.Arrays;
import java.util.List;

import com.researchspace.model.User;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

//can't use @Data as we don't want to override equals
@Getter
@Setter
@AllArgsConstructor
public class ShareRecordMessageOrRequestCreationConfiguration extends MsgOrReqstCreationCfg {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3972775404937522041L;

	public ShareRecordMessageOrRequestCreationConfiguration() {
		super();
	}
	
	private User target;
	private String permission;
	private List<String> allowedOps = Arrays.asList(new String[] {"read", "write" });
	
	public void setPermission(String permission) {
		if (!allowedOps.contains(permission)) {
			throw new IllegalArgumentException("Argument must be an allowed operation");
		}
		this.permission = permission;
	}
	
}
