package com.researchspace.model.dtos;

import com.researchspace.model.comms.MessageType;
import com.researchspace.model.views.AbstractEnumFilter;
import java.util.EnumSet;

public class MessageTypeFilter extends AbstractEnumFilter<MessageType> {

  public MessageTypeFilter(EnumSet<MessageType> types) {
    super(types, true);
  }

  /** Default message types to display in message listing */
  public static final MessageTypeFilter DEFAULT_MESSAGE_LISTING =
      new MessageTypeFilter(MessageType.STANDARD_TYPES);

  /**
   * Message type to display just special messages/requests. In this case, create lab group request,
   * join a lab group request and share record request.
   */
  public static final MessageTypeFilter SPECIAL_MESSAGE_LISTING =
      new MessageTypeFilter(MessageType.SPECIAL_TYPES);

  /** All message types */
  public static final MessageTypeFilter ALL_TYPES =
      new MessageTypeFilter(EnumSet.allOf(MessageType.class));
}
