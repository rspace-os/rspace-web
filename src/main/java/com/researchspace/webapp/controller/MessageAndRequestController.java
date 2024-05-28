package com.researchspace.webapp.controller;

import static org.apache.commons.lang.StringUtils.isBlank;

import com.researchspace.comms.CommunicationTargetFinderPolicy;
import com.researchspace.comms.CommunicationTargetFinderPolicy.TargetFinderPolicy;
import com.researchspace.core.util.ObjectToStringPropertyTransformer;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.comms.CalendarEvent;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.ICSEventGenerator;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dto.UserBasicInfo;
import com.researchspace.model.dtos.UserSearchCriteria;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Record;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.ExternalMessageHandler;
import com.researchspace.service.MessageOrRequestCreatorManager;
import java.io.IOException;
import java.io.StringWriter;
import java.security.Principal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.validate.ValidationException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.servlet.ModelAndView;

/** Controller for creating new messages and requests */
@Controller
@RequestMapping("/messaging")
@SessionAttributes("request")
public class MessageAndRequestController extends BaseController implements ApplicationContextAware {

  static final String CALENDAR_FILE_BODY = "CALENDAR_FILE_BODY";
  private static final String RECIPIENT_NAMES = "recipientnames";
  private @Autowired CommunicationManager commMgr;
  private @Autowired ExternalMessageHandler externalMessageHandler;
  private @Autowired PropertyHolder propertyHolder;

  private ApplicationContext context;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.context = applicationContext;
  }

  @Autowired private MessageOrRequestCreatorManager requestCreateMgr;

  /**
   * Initiates message/request creation dialog. This takes a string [] of message types that is
   * suitable for the situation, filters by the users permissions, and returns a {@link
   * MsgOrReqstCreationCfg} that the user can choose from.
   *
   * @param model
   * @param principal
   * @param messageTypes this restricts the available request options that can be displayed.
   * @param grpId optional for when this is a group request
   * @return
   */
  @GetMapping("/ajax/create")
  public ModelAndView createRequest(
      Model model,
      Principal principal,
      @RequestParam(value = "messageTypes[]", required = false) String[] messageTypes,
      @RequestParam(value = "groupId", required = false) Long grpId) {
    User subject = userManager.getUserByUsername(principal.getName());
    MsgOrReqstCreationCfg mor = null;
    if (messageTypes == null) {
      mor = new MsgOrReqstCreationCfg(subject, permissionUtils);
    } else {
      MessageType[] types = getMessageTypesFromStringTypes(messageTypes);
      mor = new MsgOrReqstCreationCfg(subject, permissionUtils, types);
    }
    if (grpId != null) {
      mor.setGroupId(grpId);
    }
    model.addAttribute("request", mor);
    return new ModelAndView("dashboard/message_ajax");
  }

  /**
   * @param stringTypes
   * @return
   */
  MessageType[] getMessageTypesFromStringTypes(String[] stringTypes) {
    MessageType[] types = new MessageType[stringTypes.length];
    for (int i = 0; i < stringTypes.length; i++) {
      types[i] = MessageType.valueOf(stringTypes[i]);
    }
    return types;
  }

  /**
   * Gets potential recipients of message/request based on permissions/role of users and partial
   * search term.
   */
  @GetMapping("/ajax/recipients")
  @ResponseBody
  public AjaxReturnObject<List<UserBasicInfo>> getRecipients(
      Principal principal,
      @RequestParam(value = "recordId", required = false) Long recordId,
      @RequestParam(value = "messageType") MessageType messageType,
      @RequestParam(value = "targetFinderPolicy") String targetFinderPolicy,
      @RequestParam(value = "term") String term) {

    Record recordToNotifyAbout = null;
    if (recordId != null) {
      // this should not throw an error
      recordToNotifyAbout = recordManager.get(recordId);
    }

    User subject = userManager.getUserByUsername(principal.getName());
    if (properties.isProfileHidingEnabled()) {
      userManager.populateConnectedUserList(subject);
    }

    Set<User> users =
        commMgr.getPotentialRecipientsOfRequest(
            recordToNotifyAbout,
            messageType,
            subject.getUsername(),
            term,
            getPolicy(targetFinderPolicy));

    List<UserBasicInfo> userInfos = new ArrayList<>();
    for (User user : users) {
      if (shouldAddToRecipients(user, subject)) {
        userInfos.add(user.toBasicInfo());
      }
    }
    if (userInfos.isEmpty()) {
      ErrorList errors =
          ErrorList.of(getText("messages.norecipients.msg", new Object[] {messageType.getLabel()}));
      return new AjaxReturnObject<>(null, errors);
    }
    return new AjaxReturnObject<>(userInfos, null);
  }

  private boolean shouldAddToRecipients(User user, User subject) {
    return !(user.isPrivateProfile()
        && properties.isProfileHidingEnabled()
        && !subject.isConnectedToUser(user));
  }

  /**
   * @param policyName
   * @return
   */
  private CommunicationTargetFinderPolicy getPolicy(String policyName) {
    TargetFinderPolicy policyEnum = TargetFinderPolicy.valueOf(policyName);
    if (TargetFinderPolicy.ALL.equals(policyEnum)) {
      return context.getBean("allUserPolicy", CommunicationTargetFinderPolicy.class);
    } else if (TargetFinderPolicy.ALL_PIS.equals(policyEnum)) {
      return context.getBean("findAllPisPolicy", CommunicationTargetFinderPolicy.class);
    } else if (TargetFinderPolicy.STRICT.equals(policyEnum)) {
      return context.getBean("strictTargetFinderPolicy", CommunicationTargetFinderPolicy.class);
    }
    return null; // we'll use default
  }

  /**
   * @param requestID
   * @param response
   */
  @GetMapping("/ical")
  public void getICSForTimedRequest(
      @RequestParam("id") Long requestID, HttpServletResponse response) {
    User subject = userManager.getAuthenticatedUserInSession();
    Communication comm = commMgr.getIfOwnerOrTarget(requestID, subject);
    if (!comm.isMessageOrRequest()) {
      return;
    }
    MessageOrRequest mor = (MessageOrRequest) comm;
    if (mor.getRequestedCompletionDate() == null) {
      return;
    }

    response.setContentType("text/calendar");
    response.setHeader("Content-Disposition", "attachment; filename=rspace.ics");
    ICSEventGenerator icalGenerator = new ICSEventGenerator();

    net.fortuna.ical4j.model.Calendar ical = icalGenerator.createICalEventFor(mor);
    CalendarOutputter outputter = new CalendarOutputter();
    try {
      ical.validate();
      outputter.output(ical, response.getWriter());
    } catch (IOException | ValidationException e) {
      log.error("Error creating ical output.", e);
    }
  }

  // requires ISO8601 input from the browser
  DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME;

  @PostMapping("/create_calendar_event")
  @ResponseBody
  public AjaxReturnObject<Boolean> createCalendarEvent(
      CalendarEvent calendarEvent, HttpServletRequest request) throws ValidationException {
    User subject = userManager.getAuthenticatedUserInSession();

    if (calendarEvent.getStart() != null && !calendarEvent.getStart().isEmpty()) {
      Instant startI = dateTimeFormatter.parse(calendarEvent.getStart(), Instant::from);
      calendarEvent.setStartTime(new Date(startI.toEpochMilli()));
    }

    if (calendarEvent.getEnd() != null && !calendarEvent.getEnd().isEmpty()) {
      Instant endI = dateTimeFormatter.parse(calendarEvent.getEnd(), Instant::from);
      calendarEvent.setEndTime(new Date(endI.toEpochMilli()));
    }
    // If both times are specified, start time should not be after end time
    if (calendarEvent.getStartTime() != null && calendarEvent.getEndTime() != null) {
      Calendar startTimeCal = Calendar.getInstance();
      Calendar endTimeCalendar = Calendar.getInstance();
      startTimeCal.setTime(calendarEvent.getStartTime());
      endTimeCalendar.setTime(calendarEvent.getEndTime());

      if (startTimeCal.after(endTimeCalendar)) {
        return new AjaxReturnObject<>(
            null,
            ErrorList.createErrListWithSingleMsg("Event start time must be before end time."));
      }
    }

    // Add links to resources to the description field
    addAttachmentsToDescription(calendarEvent, subject);

    // Generate a calendar event
    ICSEventGenerator icalGenerator = new ICSEventGenerator();
    net.fortuna.ical4j.model.Calendar ical = icalGenerator.createICalEventFor(calendarEvent);
    ical.validate();

    // Store the calendar file in the http session
    StringWriter calendarEventFileContents = new StringWriter();
    CalendarOutputter outputter = new CalendarOutputter();
    try {
      outputter.output(ical, calendarEventFileContents);
      request.getSession().setAttribute(CALENDAR_FILE_BODY, calendarEventFileContents.toString());

      // Return a success response
      return new AjaxReturnObject<>(Boolean.TRUE, null);
    } catch (ValidationException | IOException e) {
      log.error("Error creating ical output.", e);
      return new AjaxReturnObject<>(null, ErrorList.createErrListWithSingleMsg(e.getMessage()));
    }
  }

  @GetMapping("/get_calendar_event")
  public void getCalendarEvent(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    // Get calendar file from the http session
    String calendarEventFileContents =
        (String) request.getSession().getAttribute(CALENDAR_FILE_BODY);

    // Show an error message if there's no calendar file available.
    if (calendarEventFileContents == null) {
      throw new IllegalStateException(
          "No calendar file available. Please try creating the calendar event again.");
    }

    // Output the calendar file
    response.setContentType("text/calendar");
    response.setHeader("Content-Disposition", "attachment; filename=rspace.ics");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().print(calendarEventFileContents);
  }

  @PostMapping("/ajax/sendExternalMessage")
  public @ResponseBody AjaxReturnObject<Boolean> sendExternalMessage(
      @RequestParam(value = "recordIds[]", required = false) Long[] recordIds,
      @RequestParam(value = "appConfigElementSetId") Long appConfigElementSetId,
      @RequestParam(value = "message") String message) {

    if (isBlank(message)) {
      ErrorList errors = getErrorListFromMessageCode("errors.required", "message");
      return new AjaxReturnObject<>(null, errors);
    }

    User user = userManager.getAuthenticatedUserInSession();
    ServiceOperationResult<ResponseEntity<String>> resp =
        externalMessageHandler.sendExternalMessage(
            message, appConfigElementSetId, Arrays.asList(ArrayUtils.nullToEmpty(recordIds)), user);
    if (!resp.isSucceeded()) {
      return new AjaxReturnObject<>(
          null, ErrorList.createErrListWithSingleMsg("Could not send message"));
    }

    return new AjaxReturnObject<>(Boolean.TRUE, null);
  }

  /**
   * Creates messages directly e.g. from 'mentions' RSPAC-1509
   *
   * @param mor
   * @return
   */
  @PostMapping("/ajax/createMention")
  public @ResponseBody AjaxReturnObject<Boolean> saveMessageDirect(MsgOrReqstCreationCfg mor) {
    BindingResult errors = new BeanPropertyBindingResult(mor, "mention");
    User subject = userManager.getAuthenticatedUserInSession();
    doCreateMsg(mor, errors, subject);
    if (errors.hasErrors()) {
      ErrorList el = new ErrorList();
      inputValidator.populateErrorList(errors, el);
      return new AjaxReturnObject<>(null, el);
    }

    return new AjaxReturnObject<>(true, null);
  }

  // return created message, or null if errors were popoulated.
  // calling code should check for errors.hasErrors
  private MessageOrRequest doCreateMsg(
      MsgOrReqstCreationCfg mor, BindingResult errors, User subject) {

    if (!isAdminSendToAll(subject, mor)) {
      ValidationUtils.rejectIfEmptyOrWhitespace(errors, RECIPIENT_NAMES, "errors.emptyString");
    }
    if (errors.hasErrors()) {
      return null;
    }
    CommunicationTargetFinderPolicy policy = getPolicy(mor.getTargetFinderPolicy());
    Set<User> potentialRecipients =
        commMgr.getPotentialRecipientsOfRequest(
            null, mor.getMessageType(), subject.getUsername(), null, policy);
    Set<String> userNames =
        getUsernamesFromInput(subject, mor, errors, policy, potentialRecipients);
    Date completionDate = calculateCompletionDate(mor, errors);

    if (errors.hasErrors()) {
      return null;
    }
    MessageOrRequest createdMsg =
        requestCreateMgr.createRequest(mor, subject.getUsername(), userNames, null, completionDate);
    publisher.publishEvent(new GenericEvent(subject, createdMsg, AuditAction.CREATE));
    return createdMsg;
  }

  /**
   * Saves the created message, after performing validation checks
   *
   * @param model
   * @param mor
   * @param errors
   * @return
   */
  @PostMapping("/ajax/create")
  public ModelAndView saveMessage(
      Model model, @ModelAttribute("request") MsgOrReqstCreationCfg mor, BindingResult errors) {

    User subject = userManager.getAuthenticatedUserInSession();
    MessageOrRequest createdMsg = doCreateMsg(mor, errors, subject);
    if (errors.hasErrors()) {
      return new ModelAndView("dashboard/message_ajax");
    }

    model.addAttribute("createdMessageId", (createdMsg != null) ? createdMsg.getId() : "");
    return new ModelAndView("empty");
  }

  /**
   * Package scoped for testing
   *
   * @param subject
   * @param mor
   * @param result
   * @param policy
   * @param potentialRecipients
   * @return
   */
  public Set<String> getUsernamesFromInput(
      User subject,
      MsgOrReqstCreationCfg mor,
      BindingResult result,
      CommunicationTargetFinderPolicy policy,
      Set<User> potentialRecipients) {

    Set<String> userNames = new HashSet<>();
    String[] userNamesArr = null;
    boolean sendToAll = false;
    if (isAdminSendToAll(subject, mor)) {
      sendToAll = true;
    } else if (MessageType.GLOBAL_MESSAGE.equals(mor.getMessageType()) && !subject.hasAdminRole()) {
      throw new AuthorizationException(" Non-admin trying to send a message to everyone!");
    }
    if (sendToAll) {
      PaginationCriteria<User> pgCrit =
          PaginationCriteria.createDefaultForClass(User.class).setGetAllResults();
      UserSearchCriteria searchCrit = new UserSearchCriteria();
      searchCrit.setOnlyEnabled(true);
      pgCrit.setSearchCriteria(searchCrit);
      List<User> allUsers = userManager.getViewableUsers(subject, pgCrit).getResults();
      userNamesArr = new String[allUsers.size()];
      allUsers.stream()
          .map(new ObjectToStringPropertyTransformer<>("username"))
          .collect(Collectors.toList())
          .toArray(userNamesArr);
    } else {
      userNamesArr = extractUserNamesFromCfg(mor);
    }
    try {
      for (String uname : userNamesArr) {
        if (uname.trim().length() == 0) {
          continue;
        }
        User toAdd = userManager.getUserByUsername(uname.trim());
        userNames.add(uname.trim());

        if (!potentialRecipients.contains(toAdd)) {
          if (!isAdminSendToAll(subject, mor)) {
            result.rejectValue(
                RECIPIENT_NAMES,
                "messages.invalidrecipient.msg",
                new String[] {toAdd.getFullName(), policy.getFailureMessageIfUserInvalidTarget()},
                null);
          } else {
            // if there's some users we can't send to, just log this
            log.warn(
                "Could not send msg to {} - {}",
                toAdd.getUsername(),
                policy.getFailureMessageIfUserInvalidTarget());
          }
        }
      }
    } catch (Exception ex) {
      result.rejectValue(RECIPIENT_NAMES, "errors.username");
    }
    return userNames;
  }

  /**
   * @param subject
   * @param mor
   * @return
   */
  private boolean isAdminSendToAll(User subject, MsgOrReqstCreationCfg mor) {
    return subject.hasAdminRole() && MessageType.GLOBAL_MESSAGE.equals(mor.getMessageType());
  }

  /**
   * @param mor
   * @return
   */
  private String[] extractUserNamesFromCfg(MsgOrReqstCreationCfg mor) {
    return User.getUsernameesFromMultiUser(mor.getRecipientnames());
  }

  /**
   * @param mor
   * @param result
   * @return
   */
  private Date calculateCompletionDate(MsgOrReqstCreationCfg mor, BindingResult result) {
    Date completionDate = null;
    // ignore if is a message - a message has no completion time associated with it.
    if (!StringUtils.isBlank(mor.getRequestedCompletionDate())
        && !mor.getMessageType().equals(MessageType.SIMPLE_MESSAGE)) {
      // this is the format of the datepicker
      String format = "yyyy-MM-dd hh:mm";
      DateFormat df = new SimpleDateFormat(format);
      try {
        completionDate = df.parse(mor.getRequestedCompletionDate());
        // set comparison date to start of today
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        // if this is true then the date passed in is in the future and should be disallowed
        if (completionDate.before(c.getTime())) {
          result.rejectValue(
              "requestedCompletionDate", null, "Completion date must be a date in the future");
        }
      } catch (ParseException e) {
        result.rejectValue(
            "requestedCompletionDate", null, "Invalid date format - must be [" + format + "]");
      }
    }
    return completionDate;
  }

  private void addAttachmentsToDescription(CalendarEvent calendarEvent, User user) {
    if (calendarEvent.getAttachments() != null && !calendarEvent.getAttachments().isEmpty()) {
      StringBuilder newDescription = new StringBuilder();
      newDescription.append(calendarEvent.getDescription());
      newDescription.append("\nAttached files:\n");
      for (String globalID : calendarEvent.getAttachments().split(",")) {
        newDescription.append(getAttachmentText(new GlobalIdentifier(globalID), user));
      }
      calendarEvent.setDescription(newDescription.toString());
    }
  }

  private String getAttachmentText(GlobalIdentifier attachmentGlobalId, User user) {
    String attachmentName;

    // Get name of the attachment
    if (recordManager.exists(attachmentGlobalId.getDbId())) {
      BaseRecord attachment = recordManager.get(attachmentGlobalId.getDbId());
      assertAuthorisation(user, attachment, PermissionType.READ);
      attachmentName = attachment.getName();
    } else {
      Folder folder = folderManager.getFolder(attachmentGlobalId.getDbId(), user);
      assertAuthorisation(user, folder, PermissionType.READ);
      attachmentName = folder.getName();
    }

    // Get URL for the resource
    String recordURL =
        propertyHolder.getUrlPrefix()
            + (propertyHolder.getUrlPrefix().endsWith("/") ? "" : "/")
            + "globalId/"
            + attachmentGlobalId.toString();

    return String.format("'%s' %s%n", attachmentName, recordURL);
  }
}
