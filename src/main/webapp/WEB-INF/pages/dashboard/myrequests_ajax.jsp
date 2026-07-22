<%@ include file="/common/taglibs.jsp"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags"%>
<%@ taglib prefix="rst" uri="http://researchspace.com/tags"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<link rel="stylesheet" media="all" href="<rst:assetUrl value='/styles/messages.css'/>" />

<%-- Reusable tag for incorporating a 'my requests' dialog into a page. --%>
<axt:paginate paginationList="${paginationList}" />
<input type="hidden" id="timeOfListing" value="${timeOfListing}" />
<div id="myrequestListContents">
  <c:choose>
    <c:when test="${empty messages}">
        <spring:message code="myrequests.empty"/>
    </c:when>
    <c:otherwise>
        <h2 id="requestsTitle"> <spring:message code="myrequests.title"/> </h2>
        <div style="padding-bottom: 10px" id="myrequest_linkMenu">
            <span style="position:relative; left:10px;"><spring:message code="myrequests.orderBy.label"/> </span>
            <a href="#" class="orderBy" style="position: relative;left: 10px;"
                id="orderBy_creationTime"><spring:message code="myrequests.orderBy.timeSent"/> </a>
            <br>
            <span style="position:relative; left:10px;"><spring:message code="myrequests.orderBy.label"/> </span>
            <a href="#" class="orderBy" style="position: relative;left: 10px;"
                id="orderBy_requestedCompletionDate"><spring:message code="myrequests.orderBy.completionDate"/></a>
        </div>
        <c:forEach items="${messages}" var="message">
            <table class="table" style="width: 95%">
            <tr class="myrequestRow" id="myrequestID_${message.id}">
                <td width="120" valign="top" class="leftInfo" style="line-height:1.1em;">
                    <span style="display: block;" class="boldtext"><spring:message code="myrequests.request.sentLabel"/></span>
                    <rst:relDate input="${message.creationTime}"></rst:relDate>
                    <span style="display: block; padding-bottom:10px">
                      <c:choose>
                        <c:when test="${message.messageType == 'GLOBAL_MESSAGE'}">
                            <spring:message code="myrequests.sentToAllUsers"/>
                        </c:when>
                        <c:otherwise>
                            <c:set var="numRecipients" value="${fn:length(message.recipients)}" />
                            <%-- Add in recipient list if there is more than 1 recipient, to show user
                                which other people can view the message --%> 
                                ${numRecipients}
                            <rst:pluralize input="recipient" count="${numRecipients}"/>: 
                            <rst:joinProperties maxSize="10" property="recipient.username" collection="${message.recipients}"></rst:joinProperties>
                        </c:otherwise>
                      </c:choose>
                    </span>
                </td>
                <td id="midRequest" valign="top" class="mainMessage">
                    <div style="font-size: 1em;line-height:1.1em;">
                        <span class="messageText"> <spring:message code="myrequests.youSentA"/>
                            <span class="boldtext"> ${message.messageType.label}</span>
                                <spring:message code="myrequests.requestWord"/>
                            <c:if test="${not empty message.record}">
                                <c:choose>
                                    <c:when test="${message.record.notebook}">
                                        <c:url var="recordURL"
                                            value="/notebookEditor/${message.record.id}" />
                                    </c:when>
                                    <c:otherwise>
                                        <c:url var="recordURL"
                                            value="/workspace/editor/structuredDocument/${message.record.id}" />
                                    </c:otherwise>
                                </c:choose>
                                  <spring:message code="notifications.concerning"/> <a href="${recordURL}">${message.record.name}</a>
                            </c:if>
                            <c:if test="${not empty message.requestedCompletionDate }">
                               <spring:message code="myrequests.dueForCompletionBy"/>
                               <span class="boldtext">
                               <fmt:formatDate pattern="E dd MMM yyyy" value="${message.requestedCompletionDate}"></fmt:formatDate>
                            </span>
                            </c:if>
                            <c:if test="${not empty message.message }">
                                <spring:message code="notifications.withMessage"/> <br /> ${message.message}
                            </c:if>
                        </span>
                    </div>
                </td>
                <td width="120" valign="top" class="leftInfo" style="vertical-align:center;">
                    <input type="hidden" class="currentStatus" value="${message.status}" />
                    <span class="boldtext"><spring:message code="myrequests.status.label"/> </span>${message.status}<br/>
                    <a href="#" class="cancelRequestLnk"><spring:message code="myrequests.cancelRequest"/></a>
                </td>
            </tr>
            </table>
        </c:forEach>
    </c:otherwise>
  </c:choose>
</div>
