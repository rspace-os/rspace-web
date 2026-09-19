package com.researchspace.model.comms;

import com.researchspace.model.GroupType;
import com.researchspace.model.dto.UserPublicInfo;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MessageOrRequestView {

  private Date creationTime;

  private Long id;

  private String message;

  private MessageType messageType;

  private UserPublicInfo originator;

  private String recordName;

  private String groupName;

  private GroupType groupType;
}
