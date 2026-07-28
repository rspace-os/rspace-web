package com.researchspace.model.comms;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import org.apache.commons.lang3.Validate;

import com.researchspace.Constants;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTargetFinderPolicy.TargetFinderPolicy;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;

import lombok.Data;

/**
 * Command object for UI for creating a new message or request.
 */
@Data
public class MsgOrReqstCreationCfg implements Serializable {

    private static final long serialVersionUID = -2534838719194886202L;
    
    private Long recordId;

    private String targetFinderPolicy = "STRICT"; // default value

	private Long groupId; // optional

	private String recipientnames;

	private MessageType messageType;

	private MessageType[] allMessageTypes = MessageType.values();

	private String optionalMessage;

	private String requestedCompletionDate;
	
	private String userRole = Constants.USER_ROLE;
	
	private IPermissionUtils permUtils;

	public MsgOrReqstCreationCfg() {
		init(null);
	}

	/**
	 * Will initialise this command with a list of messageTypes that this user
	 * has permission to create, chosen from ALL message types
	 * 
	 * @param user
	 *            authenticated subject
	 */
	public MsgOrReqstCreationCfg(User user, IPermissionUtils permUtils) {
		Validate.notNull(permUtils, "Permission Utils can't be null");
		setPermUtils(permUtils);
		init(user);
	}

	/**
	 * Will initialise this command with a list of messageTypes that this user
	 * has permission to create, chosen from the supplied list of message types
	 * 
	 * @param user
	 *            authenticated subject
	 * @param permissionUtils 
	 */
	public MsgOrReqstCreationCfg(User user, IPermissionUtils permissionUtils, MessageType... messageTypes) {
		this.permUtils = permissionUtils;
		if (user != null) {
			// perm filtering needs to use a collection that supports
			// iterator.remove()
			List<MessageType> mts = new ArrayList<>();
			for (MessageType mt : messageTypes) {
				mts.add(mt);
			}
			setAvailableMessageTypes(mts, user);
			this.userRole = user.getRoles().iterator().next().getName();
		}
	}

    public void setPermUtils(IPermissionUtils permUtils) {
        this.permUtils = permUtils;
    }

    /**
     * @return the userRole
     */
    public String getUserRole() {
        return userRole;
    }

    /**
     * @param userRole the userRole to set
     */
    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public String getRequestedCompletionDate() {
        return requestedCompletionDate;
    }

    public void setRequestedCompletionDate(String requestedCompletionDate) {
        this.requestedCompletionDate = requestedCompletionDate;
    }

    /**
     * 
     * @param targetFinderPolicy
     * @throws 
     *             if the String cannot be parsed into a
     *             {@link TargetFinderPolicy}.
     */
    public void setTargetFinderPolicy(String targetFinderPolicy) {
       CommunicationTargetFinderPolicy.TargetFinderPolicy.valueOf(targetFinderPolicy);
        this.targetFinderPolicy = targetFinderPolicy;
    }
	
	private void setAvailableMessageTypes(Collection<MessageType> mts, User authUser) {
		mts = permUtils.filter(mts, PermissionType.READ, authUser);
		this.allMessageTypes = new MessageType[mts.size()];
		this.allMessageTypes = mts.toArray(allMessageTypes);
	}

	/*
	 * filters request types by permissions.
	 */
	private void init(User authUser) {
		if (authUser != null) {
			EnumSet<MessageType> mts = EnumSet.allOf(MessageType.class);
			setAvailableMessageTypes(mts, authUser);		
		}
	}

}
