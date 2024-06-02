<%@ include file="/common/taglibs.jsp"%>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags"%>
<%@ taglib prefix="rst" uri="http://researchspace.com/tags"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<link rel="stylesheet" media="all" href="<c:url value='/styles/messages.css'/>" />

<style>
    a,a:link a:active {
        color: #1465b7;
        text-decoration: none;
    }
    a:visited {
        color: #1465b7;
        background-color: transparent;
    }
    a:hover {
        color: #cc0000;
        text-decoration: none;
    }
    a.morOrderBy {
        position: relative;
        color: blue;
        left: 10px;
    }
    .messageMoreInfo {
        margin: 10px 0px;
        white-space: normal;
    }
    .leftInfo {
        min-width: 200px;
    }
</style>

<%-- Reusable tag for incorporating a messages listing dialog into a page. --%>

<axt:paginate paginationList="${paginationList}" />
<input type="hidden" id="timeOfListing" value="${timeOfListing}" />
<div id="messageListContents">
  <c:choose>
    <c:when test="${empty messages}">
        There are no active messages or requests.
    </c:when>
    <c:otherwise>
        <h2 id="messagesTitle"> My Messages </h2>
        <div style="padding-bottom: 10px" id="notification_linkMenu">
            Order by
            <a href="#" class="morOrderBy" id="orderBy_communication.creationTime">Time</a>
            <a href="#" class="morOrderBy" id="orderBy_originator.username">| Sender </a>
            <a href="#" class="morOrderBy" id="orderBy_communication.requestedCompletionDate">| Completion date </a>
        </div>
        <div id="allMessages" style="width:100%;padding-top:10px;">
            <c:forEach items="${messages}" var="message">
                <table class="messageTable" cellspacing="0" style="width: 100%">
                <tr class="notificationRow" id="messageID_${message.id}">
                    <td valign="top" class="leftInfo">
                        <div class="messageText">
                          <strong>
                            <c:if test="${message.simpleMessage}">
                                <c:if test="${message.messageType == 'GLOBAL_MESSAGE'}">Global </c:if>Message
                            </c:if>
                            <c:if test="${not message.simpleMessage}">
                                ${message.messageType.label}
                              </c:if> request
                               <c:if test = "${fn:contains(message.messageType.label, 'Collaboration')}">
                              <a rel="noreferrer" href = "https://researchspace.helpdocs.io/article/l72tg5rzze-collaboration-between-labs" target="_blank">
                              <img src ="images/info.png" width=12 height=12/> </a>
                            </c:if>
                          </strong>
                          <strong>From:</strong>
                          <span
                            data-test-id="mini-profile-activator-${message.originator.id}"
                            class="user-details"
                            data-userid="${message.originator.id}"
                            data-uniqueid="${message.id}"
                            data-firstname="${message.originator.firstName}"
                            data-lastname="${message.originator.lastName}"
                            data-position="bottom_right"
                          ><a href="#" style="font-size: 14px; line-height:30px"><c:out value="${message.originator.firstName}" /> <c:out value="${message.originator.lastName}" /></a></span>
                            <c:choose>
                            <c:when test="${message.messageType.label == JOIN_LABGROUP_REQUEST}">
                                <p>To join: <strong>${message.group.displayName}</strong></p>
                                <span class = "warning">If you accept, the group PI will be able to see all of your data</span>
                            </c:when>
                            <c:when test="${message.messageType.label == JOIN_PROJECT_GROUP_REQUEST}">
                                <p>To join: <strong>${message.group.displayName}</strong></p>
                                <span class = "warning">Project Groups have no PI, your documents won't be visible <br>
                                    to other group members until you explicitly share them.</span>
                            </c:when>
                                <c:otherwise>
                                    <br/>
                                </c:otherwise>
                            </c:choose>
                          <c:if test="${not empty message.record}">
                              <strong>Concerning: </strong>
                              <a class="messageRecordLink" href="/workspace/editor/structuredDocument/${message.record.id}">${message.record.name}</a>
                              <br/>
                          </c:if>
                          <c:if test="${not message.simpleMessage}">
                              <c:if test="${not empty message.requestedCompletionDate }">
                                  <strong>Due for completion by: </strong>
                                  <fmt:formatDate pattern="E dd MMM yyyy HH:mm" value="${message.requestedCompletionDate}"></fmt:formatDate>
                                  <a href="/messaging/ical?id=${message.id}"><img src="/images/ics-icon.png" style="margin-top: -4px;"/></a>
                              </c:if>
                              <c:if test="${not empty message.messageType.moreInfo}">
                                  <div class="messageMoreInfo">${message.messageType.moreInfo}</div>
                              </c:if>
                          </c:if>
                        </div>

                        <br />

                        <div class="messageText">
                            <strong>Sent: </strong>
                            <rst:relDate input="${message.creationTime}"></rst:relDate>
	                        <%-- Add in recipient list if there is more than 1 recipient, or if is global message,
	                         to show user which other people can view the message --%>
	                        <c:choose>
	                          <c:when test="${message.messageType == 'GLOBAL_MESSAGE'}">
	                            <div>Recipients: All Users</div>
	                          </c:when>
	                          <c:otherwise>
	                            <c:set var="numRecipients" value="${fn:length(message.recipients)}" />
	                            <c:if test="${numRecipients > 1}">
	                              <div style="white-space: normal;">
	                                 ${numRecipients} recipients:
	                                 <rst:joinProperties property="recipient.fullName" maxSize="5" collection="${message.recipients}"></rst:joinProperties>
	                               </div>
	                            </c:if>
	                          </c:otherwise>
	                        </c:choose>
	                    </div>
                    </td>
                    <td id="mid" valign="top" class="mainMessage">
                        <div class="messageContent" style="font-size: 1em">
                            ${message.message}
                        </div>
                        <div style="width:100%;text-align:right;padding-top:20px;">
                          <c:choose>
                            <c:when test="${message.statefulRequest}">
                                <input type="hidden" class="currentStatus" value="${message.status}" />
                                <span style="color:#C1C1C1;">Set Request Status:</span>
                                <br/>
                                <select class="messageStatusChooser" name="messageStatus" style="margin-top:4px;">
                                    <option value="${message.status}" selected="selected">${message.status}</option>
                                    <c:forEach items="${message.messageType.validStatusesByRecipient}" var="stat">
                                        <c:if test="${message.status ne stat}">
                                            <c:choose>
                                                <c:when
                                                    test="${message.messageType.yesNoMessage and stat eq 'COMPLETED' and message.messageType.label != JOIN_LABGROUP_REQUEST}">
                                                    <option value="${stat}">ACCEPTED</option>
                                                </c:when>
                                                <c:when test="${message.messageType.label == JOIN_LABGROUP_REQUEST and stat eq 'COMPLETED'}">
                                                    <option value="${stat}">ACCEPTED - share data with PI</option>
                                                </c:when>
                                                <c:otherwise>
                                                    <option value="${stat}">${stat}</option>
                                                </c:otherwise>
                                            </c:choose>
                                        </c:if>
                                    </c:forEach>
                                </select>
                                <div class="updateDetails" style="display: none;width:100%;">
                                    <textarea class="replyMessageArea" placeholder="Add optional message" style="margin:3px 0 3px 0"></textarea><br/>
                                    <a href="#" class="updateReplyLink">Update & Reply</a>
                                </div>
                            </c:when>
                            <c:otherwise>
                                <input type="hidden" class="currentStatus" value="${message.status}" />
                                <a href="#" class="hideMessage">Dismiss</a>
                                <c:if test="${message.messageType != 'GLOBAL_MESSAGE'}">
                                    <c:forEach items="${message.recipients}" varStatus="i" var="recipient">
                                        <c:if test="${recipient.recipient.username eq user}">
                                            <c:if test="${recipient.status  eq 'REPLIED'}">
                                                <c:set var="isReplied" value="REPLIED" />
                                            </c:if>
                                        </c:if>
                                    </c:forEach>
                                </c:if>
                                <c:choose>
                                    <c:when test="${isReplied eq 'REPLIED'}">
                                        <span class='repliedMsg'>
                                            <img src="/images/tick-icon.png" style="height:18px;width:18px;" />
                                        </span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class='repliedMsg' style="display: none">
                                            <img src="/images/tick-icon.png" />
                                        </span>
                                    </c:otherwise>
                                </c:choose>
                                <c:if test="${message.messageType != 'GLOBAL_MESSAGE'}">
                                    <div class="updateDetails">
                                        <textarea class="replyMessageArea"></textarea>
                                        <a href="#" class="replyMsgLink">Reply</a>
                                    </div>
                                </c:if>
                            </c:otherwise>
                          </c:choose>
                        </div>
                    </td>
                </tr>
                </table>
            </c:forEach>
        </div>
    </c:otherwise>
  </c:choose>
</div>
