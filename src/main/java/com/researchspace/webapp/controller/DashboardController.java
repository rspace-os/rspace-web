package com.researchspace.webapp.controller;

import com.researchspace.core.util.DefaultURLPaginator;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.core.util.PaginationUtil;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageOrRequestView;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.dtos.MessageTypeFilter;
import com.researchspace.model.dtos.NotificationStatus;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.IMessageAndNotificationTracker;
import com.researchspace.service.RSpaceRequestManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import java.security.Principal;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Handles URL requests for the Dashboard page, including messaging and notifications. */
@Controller
@RequestMapping("/dashboard")
public class DashboardController extends BaseController {

  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;
  private @Autowired RSpaceRequestManager reqStatusUpdateMgr;
  private @Autowired CommunicationManager commService;
  private @Autowired IMessageAndNotificationTracker tracker;

  @GetMapping
  public String dashboard(Model model, Principal principal) {
    PaginationCriteria<CommunicationTarget> pgCrit =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    doNotificationListingAndPrepareView(model, principal, pgCrit);
    doMessageListingAndPrepareView(model, principal, pgCrit);
    User user = getUserByUsername(principal.getName());
    setPublicationAllowed(model, user);
    return "dashboard/dashboard";
  }

  private void setPublicationAllowed(Model model, User user) {
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(user, "public_sharing"));
  }

  /**
   * Polls for new notifications and messages; this is called frequently and should not access DB or
   * do any processing.
   *
   * @return
   */
  @IgnoreInLoggingInterceptor(ignoreAll = true)
  // this method would just fill up log with useless info
  @GetMapping("/ajax/poll")
  @ResponseBody
  public AjaxReturnObject<NotificationStatus> poll() {
    User subjectUser = userManager.getAuthenticatedUserInSession();
    // handle NPE following server restart when user was logged on or browser open and hence there
    // is no longer a session
    if (subjectUser == null || subjectUser.getId() == null) {
      return new AjaxReturnObject<NotificationStatus>(null, ErrorList.of("No valid session"));
    }
    Integer notificationCount = tracker.getNotificationCountFor(subjectUser.getId());
    Integer messageCount = tracker.getMessageCountFor(subjectUser.getId());
    Integer specialmessageCount = tracker.getSpecialMessageCountFor(subjectUser.getId());
    NotificationStatus ns =
        new NotificationStatus(notificationCount, messageCount, specialmessageCount);
    return new AjaxReturnObject<NotificationStatus>(ns, null);
  }

  /**
   * Handles the cancellation of a request by the request's originator
   *
   * @param principal
   * @param messageOrRequestId
   * @param quiet optional parameter, default is <code>false</code> - recipients will be notified of
   *     the cancellation.
   * @return
   */
  @PostMapping("/ajax/cancelRequest")
  @ResponseBody
  public AjaxReturnObject<String> cancelRequest(
      Principal principal,
      @RequestParam("messageOrRequestId") Long messageOrRequestId,
      @RequestParam(value = "quiet", required = false, defaultValue = "false") boolean quiet) {
    commService.cancelRequest(principal.getName(), messageOrRequestId, quiet);
    return new AjaxReturnObject<>("Success", null);
  }

  /**
   * Handles the cancellation of a recipient by the request's originator
   *
   * @param principal
   * @param requestId
   * @param recipientId
   * @return
   */
  @ResponseBody
  @PostMapping("/ajax/cancelRecipient")
  public AjaxReturnObject<ServiceOperationResult<String>> cancelRecipient(
      Principal principal,
      @RequestParam("requestId") Long requestId,
      @RequestParam("recipientId") Long recipientId) {
    ServiceOperationResult<String> result =
        commService.cancelRecipient(principal.getName(), requestId, recipientId);
    return new AjaxReturnObject<>(result, null);
  }

  @ResponseBody
  @PostMapping("/ajax/cancelSharedRecordRequest")
  public AjaxReturnObject<ServiceOperationResult<String>> cancelSharedRecordRequest(
      Principal principal, @RequestParam("requestId") Long requestId) {
    ServiceOperationResult<String> result =
        commService.cancelSharedRecordRequest(principal.getName(), requestId);
    return new AjaxReturnObject<>(result, null);
  }

  /**
   * Alters message status
   *
   * @param principal
   * @param messageOrRequestId
   * @param status must be parsable into a CommunicationStatus
   * @param optionalMessage
   * @return
   */
  @PostMapping("/ajax/messageStatus")
  @ResponseBody
  public AjaxReturnObject<String> updateMessageStatus(
      Principal principal,
      @RequestParam("messageOrRequestId") Long messageOrRequestId,
      @RequestParam("status") String status,
      @RequestParam(value = "optionalMessage", required = false) String optionalMessage) {
    reqStatusUpdateMgr.updateStatus(
        principal.getName(),
        CommunicationStatus.valueOf(status),
        messageOrRequestId,
        optionalMessage);
    return new AjaxReturnObject<>("Success", null);
  }

  @GetMapping("/updateMessageStatus")
  public String updateMessageStatusGet(
      Principal principal,
      Model model,
      @RequestParam("messageOrRequestId") Long messageOrRequestId,
      @RequestParam("status") String status) {
    CommunicationStatus statusEnum = CommunicationStatus.valueOf(status);
    Communication comm = commService.get(messageOrRequestId);
    model.addAttribute("comm", comm);
    switch (statusEnum) {
      case ACCEPTED:
      case COMPLETED:
        model.addAttribute("requestStatus", "accepted");
        break;
      case REJECTED:
        model.addAttribute("requestStatus", "declined");
        break;
      default:
        return "error";
    }
    updateMessageStatus(principal, messageOrRequestId, status, null);
    return "requestFeedback";
  }

  /**
   * Handles a user responding to an original message.
   *
   * @param principal
   * @param messageOrRequestId
   * @param message
   * @return
   */
  @PostMapping("/ajax/messageReply")
  @ResponseBody
  public AjaxReturnObject<String> replyToMessage(
      Principal principal,
      @RequestParam("messageOrRequestId") Long messageOrRequestId,
      @RequestParam(value = "message") String message) {
    try {
      reqStatusUpdateMgr.replyToMessage(principal.getName(), messageOrRequestId, message);
    } catch (Exception e) {
      log.error("Error replying to message: " + e.getMessage());
      ErrorList el = ErrorList.of("Could not save message reply, this has been logged");
      return new AjaxReturnObject<>(null, el);
    }
    return new AjaxReturnObject<>("Success", null);
  }

  /**
   * Marks notifications as read
   *
   * @param principal
   * @param ids A Collection of notification Ids
   * @param refresh boolean refresh from DB, or not; optional, defaults to <code>true</code>.
   * @return A JSP view
   */
  @PostMapping("/ajax/markAsRead")
  public String markAsRead(
      Principal principal,
      @RequestParam("id[]") Collection<Long> ids,
      @RequestParam(value = "refresh", required = false, defaultValue = "true") Boolean refresh) {
    Set<Long> toMark = new HashSet<Long>(ids);
    commService.markNotificationsAsRead(toMark, principal.getName());
    if (refresh) {
      // reload listing of notifications
      return "redirect:/dashboard/ajax/listNotifications";
    } else {
      return "empty"; // returns no content
    }
  }

  /**
   * Marks all notifications as read since a given date-time.
   *
   * @param principal
   * @param since the date after which all notifications will be marked as read
   */
  @PostMapping("/ajax/markAllAsRead")
  public String markAllAsRead(Principal principal, @RequestParam(value = "since") Long since) {
    commService.markAllNotificationsAsRead(principal.getName(), new Date(since));
    return "redirect:/dashboard/ajax/listNotifications";
  }

  /**
   * Gets notifications where the subject is the recipient of the notification.
   *
   * @param model
   * @param principal
   * @param pgCrit
   * @return
   */
  @GetMapping("/ajax/listNotifications")
  public String listNotifications(
      Model model, Principal principal, PaginationCriteria<CommunicationTarget> pgCrit) {
    doNotificationListingAndPrepareView(model, principal, pgCrit);
    return "dashboard/notifications_ajax";
  }

  private void doNotificationListingAndPrepareView(
      Model model, Principal principal, PaginationCriteria<CommunicationTarget> pgCrit) {
    configurePagination(pgCrit);
    Date timeOfListing = new Date();
    ISearchResults<Notification> notificns =
        commService.getNewNotificationsForUser(principal.getName(), pgCrit);
    List<PaginationObject> paginationList =
        PaginationUtil.generatePagination(
            notificns.getTotalPages(),
            notificns.getPageNumber(),
            new DefaultURLPaginator("/dashboard/ajax/listNotifications", pgCrit),
            "ntfcn_pagelink");

    model.addAttribute("paginationList", paginationList);
    model.addAttribute("notificationList", notificns.getResults());
    // this is a timestamp on the search; any subsequent request from the
    // client to delete all notifications will only delete those earlier
    // than this date,
    model.addAttribute("timeOfListing", timeOfListing.getTime());
  }

  /**
   * Gets messages and open requests where the subject is the recipient.
   *
   * @param model
   * @param principal
   * @param pgCrit
   * @return
   */
  @GetMapping("/ajax/allMessages")
  public String listAllMessages(
      Model model, Principal principal, PaginationCriteria<CommunicationTarget> pgCrit) {
    doMessageListingAndPrepareView(model, principal, pgCrit);
    return "dashboard/messages_ajax";
  }

  private void doMessageListingAndPrepareView(
      Model model, Principal principal, PaginationCriteria<CommunicationTarget> pgCrit) {

    ISearchResults<MessageOrRequest> messages = getMessages(principal, pgCrit, null);
    Date timeOfListing = new Date();

    List<PaginationObject> paginationList =
        PaginationUtil.generatePagination(
            messages.getTotalPages(),
            messages.getPageNumber(),
            new DefaultURLPaginator("/dashboard/ajax/allMessages", pgCrit),
            "mor_pagelink");

    model.addAttribute("paginationList", paginationList);
    model.addAttribute("messages", messages.getResults());
    model.addAttribute("JOIN_LABGROUP_REQUEST", MessageType.REQUEST_JOIN_LAB_GROUP.getLabel());
    model.addAttribute(
        "JOIN_PROJECT_GROUP_REQUEST", MessageType.REQUEST_JOIN_PROJECT_GROUP.getLabel());
    // this is a timestamp on the search; any subsequent request from the
    // client to delete all notifications will only delete those earlier
    // than this date,
    model.addAttribute("timeOfListing", timeOfListing.getTime());
    model.addAttribute("user", principal.getName());
  }

  /**
   * @param principal
   * @param pgCrit
   * @return
   */
  @ResponseBody
  @GetMapping("/ajax/specialMessages")
  public AjaxReturnObject<List<MessageOrRequestView>> listSpecialMessages(
      Principal principal, PaginationCriteria<CommunicationTarget> pgCrit) {
    ISearchResults<MessageOrRequest> messages =
        getMessages(principal, pgCrit, MessageTypeFilter.SPECIAL_MESSAGE_LISTING);
    List<MessageOrRequestView> messageViews = MessageOrRequest.toView(messages);
    return new AjaxReturnObject<>(messageViews, null);
  }

  private ISearchResults<MessageOrRequest> getMessages(
      Principal principal,
      PaginationCriteria<CommunicationTarget> pgCrit,
      MessageTypeFilter msgTypeFilter) {
    configurePagination(pgCrit);

    ISearchResults<MessageOrRequest> messages = null;
    if (msgTypeFilter != null) {
      messages =
          commService.getActiveMessagesAndRequestsForUserTargetByType(
              principal.getName(), pgCrit, msgTypeFilter);
    } else {
      messages = commService.getActiveMessagesAndRequestsForUserTarget(principal.getName(), pgCrit);
    }
    return messages;
  }

  /**
   * Lists open requests made by the subject
   *
   * @param model
   * @param principal
   * @param pgCrit
   * @return
   */
  @GetMapping("/ajax/listMyRequests")
  public String listMyRequests(
      Model model, Principal principal, PaginationCriteria<MessageOrRequest> pgCrit) {
    doMyRequestListingAndPrepareView(model, principal, pgCrit);
    return "dashboard/myrequests_ajax";
  }

  private void doMyRequestListingAndPrepareView(
      Model model, Principal principal, PaginationCriteria<MessageOrRequest> pgCrit) {

    configurePaginationForMoR(pgCrit);
    Date timeOfListing = new Date();
    ISearchResults<MessageOrRequest> messages =
        commService.getSentRequests(principal.getName(), pgCrit);
    List<PaginationObject> paginationList =
        PaginationUtil.generatePagination(
            messages.getTotalPages(),
            messages.getPageNumber(),
            new DefaultURLPaginator("/dashboard/ajax/listMyRequests", pgCrit));

    model.addAttribute("paginationList", paginationList);
    model.addAttribute("messages", messages.getResults());
    // this is a timestamp on the search; any subsequent request from the
    // client to delete all notifications will only delete those earlier
    // than this date.
    model.addAttribute("timeOfListing", timeOfListing.getTime());
    model.addAttribute("user", principal.getName());
  }

  private void configurePagination(PaginationCriteria<CommunicationTarget> pgCrit) {
    pgCrit.setClazz(CommunicationTarget.class);
    // set defaults if need be
    if (pgCrit.setOrderByIfNull("lastStatusUpdate")) {
      pgCrit.setSortOrder(SortOrder.DESC);
    }
  }

  private void configurePaginationForMoR(PaginationCriteria<MessageOrRequest> pgCrit) {
    pgCrit.setClazz(MessageOrRequest.class);
    // set defaults if need be
    if (pgCrit.setOrderByIfNull("creationTime")) {
      pgCrit.setSortOrder(SortOrder.DESC);
    }
  }
}
