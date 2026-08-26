package com.researchspace.model.comms;

import com.researchspace.model.permissions.AbstractEntityPermissionAdapter;
import com.researchspace.model.permissions.MessageTypePermissionsAdapter;
import com.researchspace.model.record.PermissionsAdaptable;
import java.util.EnumSet;

/**
 * Enum for classification of message or request.
 *
 * <p>
 *
 * <h4>Maintenance notes</h4>
 *
 * String representations of these enums are used in the UI, so if renaming these enums be aware to
 * look through the UI code and update strings.
 *
 * <p>Also, these enums are stored in the DB using the their 0-based indexes, so add new Enums to
 * the end of the list.
 */
public enum MessageType implements PermissionsAdaptable {

  /** A message, with no implied request for the recipient to act. */
  SIMPLE_MESSAGE("messages.types.basic", false, null),

  /** A message that is sent to everyone, from an admin */
  GLOBAL_MESSAGE("messages.types.global", false, null),

  /** Sender requests recipient to view a record */
  REQUEST_RECORD_REVIEW(
      "messages.types.reviewDocument",
      new CommunicationStatus[] {
        CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED, CommunicationStatus.ACCEPTED
      },
      false,
      null),

  /** Sender asks PI to join in the creation of a collaboration group */
  REQUEST_EXTERNAL_SHARE(
      "messages.types.createCollaborationGroup",
      new CommunicationStatus[] {CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED},
      true,
      null),

  /** Sender asks user to join an existing LabGroup */
  REQUEST_JOIN_LAB_GROUP(
      "messages.types.joinLabGroup",
      new CommunicationStatus[] {CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED},
      true,
      null),

  /**
   * Sender asks user to create a new Lab Group as new PI. This is a PublicCloud specific request
   * sent as part of RSPAC-245-use case2
   */
  REQUEST_CREATE_LAB_GROUP(
      "messages.types.createLabGroupAsPi",
      new CommunicationStatus[] {CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED},
      true,
      null),

  /** Sender asks user to agree to the sender sharing a record with user. */
  REQUEST_SHARE_RECORD(
      "messages.types.shareDocument",
      new CommunicationStatus[] {CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED},
      true,
      null),

  /** Sender asks someone to witness to a signed-document */
  REQUEST_RECORD_WITNESS(
      "messages.types.witnessDocumentSigning",
      new CommunicationStatus[] {CommunicationStatus.REJECTED},
      true,
      "messages.moreInfo.witnessDocumentSigning"),

  /** Sender asks PI to join existing collaboration group. */
  REQUEST_JOIN_EXISTING_COLLAB_GROUP(
      "messages.types.joinExistingCollaborationGroup",
      new CommunicationStatus[] {CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED},
      true,
      null),

  /** Sender asks user to join an existing Project Group. */
  REQUEST_JOIN_PROJECT_GROUP(
      "messages.types.joinProjectGroup",
      new CommunicationStatus[] {CommunicationStatus.COMPLETED, CommunicationStatus.REJECTED},
      true,
      null);

  public static final EnumSet<MessageType> SPECIAL_TYPES =
      EnumSet.of(
          REQUEST_CREATE_LAB_GROUP,
          REQUEST_JOIN_LAB_GROUP,
          REQUEST_SHARE_RECORD,
          GLOBAL_MESSAGE,
          REQUEST_JOIN_PROJECT_GROUP);

  public static final EnumSet<MessageType> STANDARD_TYPES = EnumSet.complementOf(SPECIAL_TYPES);

  private final String labelKey;
  private final boolean isYesNoMessage;
  private final String moreInfoKey;

  public String getMoreInfoKey() {
    return moreInfoKey;
  }

  private CommunicationStatus[] validUpdateStatusesByRecipient = CommunicationStatus.values();

  MessageType(String labelKey, boolean isYesNoMesg, String moreInfoKey) {
    this.labelKey = labelKey;
    this.isYesNoMessage = isYesNoMesg;
    this.moreInfoKey = moreInfoKey;
  }

  MessageType(
      String labelKey,
      CommunicationStatus[] validStatuses,
      boolean isYesNoMesg,
      String moreInfoKey) {
    this(labelKey, isYesNoMesg, moreInfoKey);
    this.validUpdateStatusesByRecipient = validStatuses;
  }

  /**
   * Whether this msg type just requires a yes/no response to accept (true) and complete the
   * request, or whether there is period of time between accepted and completed (false).
   *
   * @return
   */
  public boolean isYesNoMessage() {
    return isYesNoMessage;
  }

  /**
   * Boolean test for whether this message type is a standard type that should be included in
   * message/dashboard listings.
   *
   * @return
   */
  public boolean isStandardType() {
    return STANDARD_TYPES.contains(this);
  }

  /**
   * Gets the valid statuses for this {@link MessageType} that a recipient can alter to.
   *
   * @return
   */
  public CommunicationStatus[] getValidStatusesByRecipient() {
    return validUpdateStatusesByRecipient;
  }

  public String getLabelKey() {
    return labelKey;
  }

  @Override
  public AbstractEntityPermissionAdapter getPermissionsAdapter() {
    return new MessageTypePermissionsAdapter(this);
  }
}
