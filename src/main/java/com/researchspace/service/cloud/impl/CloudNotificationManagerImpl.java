package com.researchspace.service.cloud.impl;

import static com.researchspace.core.util.TransformerUtils.toList;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.model.comms.CreateGroupMessageOrRequestCreationConfiguration;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.comms.ShareRecordMessageOrRequestCreationConfiguration;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.MessageOrRequestCreatorManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.cloud.CloudNotificationManager;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import com.researchspace.service.impl.StrictEmailContentGenerator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("cloudNotificationManager")
public class CloudNotificationManagerImpl implements CloudNotificationManager {

  private static final String NOMINATE_PI_EMAIL_TITLE = "Create Group Invitation - Research Space";

  @Autowired
  @Qualifier("emailBroadcast")
  private EmailBroadcast emailer;

  private @Autowired MessageOrRequestCreatorManager requestCreatorManager;
  private @Autowired IPermissionUtils permUtils;
  private @Autowired IPropertyHolder propertyHolder;
  private @Autowired UserManager userMgr;
  private @Autowired AnalyticsManager analyticsManager;
  private @Autowired StrictEmailContentGenerator strictEmailContentGenerator;

  public void setAnalyticsManager(AnalyticsManager analyticsMgr) {
    this.analyticsManager = analyticsMgr;
  }

  @Override
  public void sendJoinGroupRequest(User subject, Group group) {
    MsgOrReqstCreationCfg cgf = new MsgOrReqstCreationCfg(subject, permUtils);
    cgf.setGroupId(group.getId());
    cgf.setMessageType(
        group.getGroupType() == GroupType.PROJECT_GROUP
            ? MessageType.REQUEST_JOIN_PROJECT_GROUP
            : MessageType.REQUEST_JOIN_LAB_GROUP);
    requestCreatorManager.createRequest(
        cgf, subject.getUsername(), new HashSet<>(group.getMemberString()), null, null);
  }

  @Override
  public void sendCreateGroupRequest(
      User source, User invitedTarget, List<String> emails, String groupName) {

    CreateGroupMessageOrRequestCreationConfiguration createGroupCfg =
        new CreateGroupMessageOrRequestCreationConfiguration(source, permUtils);
    createGroupCfg.setCreator(source);
    createGroupCfg.setTarget(invitedTarget);
    createGroupCfg.setEmails(emails);
    createGroupCfg.setGroupName(groupName);
    createGroupCfg.setMessageType(MessageType.REQUEST_CREATE_LAB_GROUP);
    requestCreatorManager.createRequest(
        createGroupCfg,
        source.getUsername(),
        new HashSet<>(Arrays.asList(invitedTarget.getUsername())),
        null,
        null);
  }

  @Override
  public void sendShareRecordRequest(User source, User target, Long recordId, String permission) {
    ShareRecordMessageOrRequestCreationConfiguration cgf =
        new ShareRecordMessageOrRequestCreationConfiguration();
    cgf.setTarget(target);
    cgf.setRecordId(recordId);
    cgf.setPermission(permission);
    cgf.setMessageType(MessageType.REQUEST_SHARE_RECORD);
    requestCreatorManager.createRequest(
        cgf, source.getUsername(), new HashSet<>(Arrays.asList(target.getUsername())), null, null);
  }

  private void assertTokenIncludedIfIsTempUser(TokenBasedVerification token, User user) {
    if (user.isTempAccount() && token == null) {
      throw new IllegalStateException("New user needs verification token in email!");
    }
  }

  @Override
  public void sendJoinGroupInvitationEmail(
      User creator, User invited, Group group, HttpServletRequest request) {

    TokenBasedVerification token = null;
    if (invited.isTempAccount()) {
      token = createVerificationToken(request, invited);
    }

    assertTokenIncludedIfIsTempUser(token, invited);
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("creator", creator);
    velocityModel.put("invited", invited);
    velocityModel.put("group", group);

    EmailContent content =
        mergeIntoVelocityTemplates(
            invited,
            token,
            velocityModel,
            "groupInvitationNewUser.vm",
            "groupInvitationExistingUser.vm");
    emailer.sendHtmlEmail(
        "Join Group Invitation - Research Space", content, toList(invited.getEmail()), null);

    if (invited.isTempAccount()) {
      analyticsManager.joinGroupInvitationSent(creator, invited, request);
    }
  }

  @Override
  public void sendPIInvitationEmail(
      User creator, User invited, String groupName, HttpServletRequest request) {

    TokenBasedVerification token = null;
    if (invited.isTempAccount()) {
      token = createVerificationToken(request, invited);
    }

    assertTokenIncludedIfIsTempUser(token, invited);
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("creator", creator);
    velocityModel.put("invited", invited);
    velocityModel.put("groupName", groupName);
    EmailContent msg =
        mergeIntoVelocityTemplates(
            invited,
            token,
            velocityModel,
            "groupPIInvitationNewUser.vm",
            "groupPIInvitationExistingUser.vm");
    emailer.sendHtmlEmail(NOMINATE_PI_EMAIL_TITLE, msg, toList(invited.getEmail()), null);

    if (invited.isTempAccount()) {
      analyticsManager.joinGroupInvitationSent(creator, invited, request);
    }
  }

  @Override
  public void sendShareRecordInvitationEmail(
      User creator, User invited, String recordName, HttpServletRequest request) {

    TokenBasedVerification token = null;
    if (invited.isTempAccount()) {
      token = createVerificationToken(request, invited);
    }

    assertTokenIncludedIfIsTempUser(token, invited);
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("creator", creator);
    velocityModel.put("invited", invited);
    velocityModel.put("recordName", recordName);
    EmailContent content =
        mergeIntoVelocityTemplates(
            invited,
            token,
            velocityModel,
            "shareRecordInvitationNewUser.vm",
            "shareRecordInvitationExistingUser.vm");
    emailer.sendHtmlEmail(
        "Shared Document - Research Space", content, toList(invited.getEmail()), null);

    if (invited.isTempAccount()) {
      analyticsManager.shareDocInvitationSent(creator, invited, request);
    }
  }

  private TokenBasedVerification createVerificationToken(
      HttpServletRequest request, User invitedUser) {
    String host = "unknown";
    if (request != null) {
      host = RequestUtil.remoteAddr(request);
    }
    return userMgr.createTokenBasedVerificationRequest(
        invitedUser, invitedUser.getEmail(), host, TokenBasedVerificationType.VERIFIED_SIGNUP);
  }

  private EmailContent mergeIntoVelocityTemplates(
      User invited,
      TokenBasedVerification token,
      Map<String, Object> velocityModel,
      String templateForNewUser,
      String templateForExistingUser) {
    EmailContent msg;
    if (invited.isTempAccount()) {
      msg = mergeTempUserTemplate(token, velocityModel, templateForNewUser);
    } else {
      msg = mergeExistingUserTemplate(velocityModel, templateForExistingUser);
    }
    return msg;
  }

  private EmailContent mergeExistingUserTemplate(
      Map<String, Object> velocityModel, String templateName) {

    velocityModel.put("acceptanceLink", createLoginLink());
    return strictEmailContentGenerator.generatePlainTextAndHtmlContent(templateName, velocityModel);
  }

  private String createLoginLink() {
    return propertyHolder.getServerUrl() + "/login";
  }

  private EmailContent mergeTempUserTemplate(
      TokenBasedVerification token, Map<String, Object> velocityModel, final String templateName) {

    velocityModel.put("token", token.getToken());
    String link = createTokenisedSignupLink(token);
    velocityModel.put("acceptanceLink", link);
    return strictEmailContentGenerator.generatePlainTextAndHtmlContent(templateName, velocityModel);
  }

  private String createTokenisedSignupLink(TokenBasedVerification token) {
    return propertyHolder.getServerUrl() + "/signup?token=" + token.getToken();
  }
}
