<%@ include file="/common/taglibs.jsp"%>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags"%>
<%@ taglib prefix="rst" uri="http://researchspace.com/tags"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib prefix="f" uri="http://researchspace.com/functions" %>
<link rel="stylesheet" media="all" href="<rst:assetUrl value='/styles/messages.css'/>" />

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
        <spring:message code="messages.empty"/>
    </c:when>
    <c:otherwise>
        <h2 id="messagesTitle"> <spring:message code="messages.title"/> </h2>
        <div style="padding-bottom: 10px" id="notification_linkMenu">
            <spring:message code="messages.orderBy.label"/>
            <a href="#" class="morOrderBy" id="orderBy_communication.creationTime"><spring:message code="messages.orderBy.time"/></a>
            <a href="#" class="morOrderBy" id="orderBy_originator.username"><spring:message code="messages.orderBy.sender"/></a>
            <a href="#" class="morOrderBy" id="orderBy_communication.requestedCompletionDate"><spring:message code="messages.orderBy.completionDate"/></a>
        </div>
        <div id="allMessages" style="width:100%;padding-top:10px;">
            <c:forEach items="${messages}" var="message">
                <table class="messageTable" cellspacing="0" style="width: 100%">
                <tr class="notificationRow" id="messageID_${message.id}">
                    <td valign="top" class="leftInfo">
                        <div class="messageText">
                          <strong>
                            <c:if test="${message.simpleMessage}">
                                <c:choose>
                                  <c:when test="${message.messageType == 'GLOBAL_MESSAGE'}">
                                    <spring:message code="messages.type.globalMessageRequest"/>
                                  </c:when>
                                  <c:otherwise>
                                    <spring:message code="messages.type.messageRequest"/>
                                  </c:otherwise>
                                </c:choose>
                            </c:if>
                            <c:if test="${not message.simpleMessage}">
                                <spring:message code="messages.type.request" arguments="${message.messageType.label}"/>
                            </c:if>
                               <c:if test = "${fn:contains(message.messageType.label, 'Collaboration')}">
                              <spring:message code="common:help.collaborationBetweenLabs" var="collaborationHelpSlug"/>
                              <a rel="noreferrer" href="${f:helpDocsUrl(collaborationHelpSlug)}" target="_blank">
                              <img src ="images/info.png" width=12 height=12/> </a>
                            </c:if>
                          </strong>
                          <strong><spring:message code="messages.from.label"/></strong>
                          <span
                            data-test-id="mini-profile-activator-${message.originator.id}"
                            class="user-details"
                            data-user-id="${message.originator.id}"
                            data-unique-id="${message.id}"
                            data-first-name="${message.originator.firstName}"
                            data-last-name="${message.originator.lastName}"
                            data-position="bottom_right"
                          ><a href="#" style="font-size: 14px; line-height:30px"><c:out value="${message.originator.firstName}" /> <c:out value="${message.originator.lastName}" /></a></span>
                            <c:choose>
                            <c:when test="${message.messageType.label == JOIN_LABGROUP_REQUEST}">
                                <p><spring:message code="messages.joinRequest.toJoin"/> <strong>${message.group.displayName}</strong></p>
                                <span class = "warning"><spring:message code="messages.joinLabGroup.warning"/></span>
                            </c:when>
                            <c:when test="${message.messageType.label == JOIN_PROJECT_GROUP_REQUEST}">
                                <p><spring:message code="messages.joinRequest.toJoin"/> <strong>${message.group.displayName}</strong></p>
                                <span class = "warning"><spring:message code="messages.joinProjectGroup.warning"/></span>
                            </c:when>
                                <c:otherwise>
                                    <br/>
                                </c:otherwise>
                            </c:choose>
                          <c:if test="${not empty message.record}">
                              <strong><spring:message code="messages.concerning.label"/> </strong>
                              <a class="messageRecordLink" href="/workspace/editor/structuredDocument/${message.record.id}">${message.record.name}</a>
                              <br/>
                          </c:if>
                          <c:if test="${not message.simpleMessage}">
                              <c:if test="${not empty message.requestedCompletionDate }">
                                  <strong><spring:message code="messages.completionBy.label"/> </strong>
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
                            <strong><spring:message code="messages.sent.label"/> </strong>
                            <rst:relDate input="${message.creationTime}"></rst:relDate>
	                        <%-- Add in recipient list if there is more than 1 recipient, or if is global message,
	                         to show user which other people can view the message --%>
	                        <c:choose>
	                          <c:when test="${message.messageType == 'GLOBAL_MESSAGE'}">
	                            <div><spring:message code="messages.recipients.allUsers"/></div>
	                          </c:when>
	                          <c:otherwise>
	                            <c:set var="numRecipients" value="${fn:length(message.recipients)}" />
	                            <c:if test="${numRecipients > 1}">
	                              <div style="white-space: normal;">
	                                 <spring:message code="messages.recipients.count" arguments="${numRecipients}"/>
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
                                <span style="color:#C1C1C1;"><spring:message code="messages.setStatus.label"/></span>
                                <br/>
                                <select class="messageStatusChooser" name="messageStatus" style="margin-top:4px;">
                                    <option value="${message.status}" selected="selected"><spring:message code="${rst:communicationStatusMessageKey(message.status)}"/></option>
                                    <c:forEach items="${message.messageType.validStatusesByRecipient}" var="stat">
                                        <c:if test="${message.status ne stat}">
                                            <c:choose>
                                                <c:when
                                                    test="${message.messageType.yesNoMessage and stat eq 'COMPLETED' and message.messageType.label != JOIN_LABGROUP_REQUEST}">
                                                    <option value="${stat}"><spring:message code="messages.status.accepted"/></option>
                                                </c:when>
                                                <c:when test="${message.messageType.label == JOIN_LABGROUP_REQUEST and stat eq 'COMPLETED'}">
                                                    <option value="${stat}"><spring:message code="messages.status.acceptedShareWithPi"/></option>
                                                </c:when>
                                                <c:otherwise>
                                                    <option value="${stat}"><spring:message code="${rst:communicationStatusMessageKey(stat)}"/></option>
                                                </c:otherwise>
                                            </c:choose>
                                        </c:if>
                                    </c:forEach>
                                </select>
                                <div class="updateDetails" style="display: none;width:100%;">
                                    <textarea class="replyMessageArea" placeholder="<spring:message code='messages.reply.placeholder'/>" style="margin:3px 0 3px 0"></textarea><br/>
                                    <a href="#" class="updateReplyLink"><spring:message code="messages.updateReply.label"/></a>
                                </div>
                            </c:when>
                            <c:otherwise>
                                <input type="hidden" class="currentStatus" value="${message.status}" />
                                <a href="#" class="hideMessage"><spring:message code="common:actions.dismiss"/></a>
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
                                        <a href="#" class="replyMsgLink"><spring:message code="messages.reply.label"/></a>
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
