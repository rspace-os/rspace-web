package com.researchspace.model.comms;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;

/**
 * Factory class for instantiation of MessageOrRequests and subclasses.<br/>
 * This is insttantiated as a bean in BaseConfig (from rspace-web)
 */
public class RequestFactory {

	public RequestFactory() {
		super();
		this.htmlCleaner = new HTMLCleaner();
	}

	private HTMLCleaner htmlCleaner;

	public MessageOrRequest createMessageOrRequestObject(MsgOrReqstCreationCfg config, BaseRecord record, Group group,
			User originator) {
		MessageOrRequest mor = null;
		if (isJoinGroupRequest(config)) {
			GroupMessageOrRequest groupmor = new GroupMessageOrRequest(config.getMessageType());
			groupmor.setGroup(group);
			mor = groupmor;
		} else if (isCreateGroupRequest(config)) {

			CreateGroupMessageOrRequestCreationConfiguration createGroupMORConf = null;
			CreateGroupMessageOrRequest createGroupMOR = new CreateGroupMessageOrRequest();
			if (config instanceof CreateGroupMessageOrRequestCreationConfiguration) {

				createGroupMORConf = (CreateGroupMessageOrRequestCreationConfiguration) config;

				createGroupMOR.setCreator(createGroupMORConf.getCreator());
				createGroupMOR.setTarget(createGroupMORConf.getTarget());
				createGroupMOR.setEmails(createGroupMORConf.getEmails());
				createGroupMOR.setGroupName(createGroupMORConf.getGroupName());
			}
			mor = createGroupMOR;
		} else if (isShareRecordRequest(config)) {
			ShareRecordMessageOrRequestCreationConfiguration shareRecordMORConf = null;
			ShareRecordMessageOrRequest shareRecordMOR = new ShareRecordMessageOrRequest();
			if (config instanceof ShareRecordMessageOrRequestCreationConfiguration) {
				shareRecordMORConf = (ShareRecordMessageOrRequestCreationConfiguration) config;
				shareRecordMOR.setTarget(shareRecordMORConf.getTarget());
				shareRecordMOR.setPermission(shareRecordMORConf.getPermission());
			}

			mor = shareRecordMOR;

		} else {
			mor = new MessageOrRequest(config.getMessageType());
		}
		mor.setOriginator(originator);
		mor.setRecord(record);
		mor.setMessage(htmlCleaner.cleanHTMLStrict(config.getOptionalMessage(), true));
		return mor;
	}

	private boolean isJoinGroupRequest(MsgOrReqstCreationCfg config) {
		return MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP.equals(config.getMessageType())
				|| MessageType.REQUEST_JOIN_LAB_GROUP.equals(config.getMessageType())
				|| MessageType.REQUEST_JOIN_PROJECT_GROUP.equals(config.getMessageType());
	}

	private boolean isCreateGroupRequest(MsgOrReqstCreationCfg config) {
		return MessageType.REQUEST_CREATE_LAB_GROUP.equals(config.getMessageType());
	}

	private boolean isShareRecordRequest(MsgOrReqstCreationCfg config) {
		return MessageType.REQUEST_SHARE_RECORD.equals(config.getMessageType());
	}
}
