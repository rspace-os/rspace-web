<%@ include file="/common/taglibs.jsp"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags"%>
<%@ taglib prefix="rst" uri="http://researchspace.com/tags"%>
<link rel="stylesheet" media="all" href="<c:url value='/styles/messages.css'/>" />
<%--
Reusable tag for incorporating a notifications section into a page.
 --%>
<axt:paginate paginationList="${paginationList}" />
<input type="hidden" id="timeOfListing" value="${timeOfListing}" />
<div id=notificationListContents>
	<c:choose>
		<c:when test="${empty notificationList}">
			There are no new notifications.
		</c:when>
		<c:otherwise>
			<h2 id="notificationsTitle">My Notifications</h2>
			<div style="padding-bottom: 10px" id="notification_linkMenu">
				<span style="float: right; text-align: right;"> <a
					style="position: relative; right: 10px;" id="deleteAllOnPageLink"
					href="#">Delete all displayed</a><br> <input type="hidden"
					id="timeOfListing" value="${timeOfListing}" /> <a
					style="position: relative; right: 10px;" id="deleteAllLink"
					href="#">Delete all</a>
				</span> <span style="position: relative; left: 10px;">Order By: </span> <a
					href="#" class="notifcnOrderBy" style="position: relative; left: 10px;"
					id="orderBy_communication.creationTime">Time Sent</a><br> <span
					style="position: relative; left: 10px;">Order By: </span> <a
					href="#" class="notifcnOrderBy" style="position: relative; left: 10px;"
					id="orderBy_originator.username">Sender</a>
				<%--	<a href="/admin/preferences" style="position:relative;left:10px;"
						>Configure... </a> --%>
			</div>

			<c:forEach items="${notificationList}" var="notification">
				<table class="messageTable" cellspacing="0" style="width: 100%">
					<tr class="notificationRow" id="notificnID_${notification.id}">
						<td width="120" valign="top" class="leftInfo"
							style="line-height: 1.1em;">
							<div>
								<span class="boldtext">Notification<br>Sent:
								</span>
								<rst:relDate input="${notification.creationTime}"></rst:relDate>
							</div>
						</td>
						<td id="mid" valign="top" class="mainMessage" style="word-wrap:break-word;">
						<div>
							<div class="msgContent" >
								<c:choose>
									<c:when test="${not empty notification.notificationMessage}">
						  ${notification.notificationMessage}
						</c:when>
									<c:otherwise>
							 ${notification.originator.username} generated a ${notification.notificationType} event.
						</c:otherwise>
								</c:choose>
							</div>
							<div>
							<c:if test="${not empty notification.record  and  not notification.ignoreRecordLinkInMessage}">
						          concerning 
						          <c:choose>
						 		  <c:when test = "${notification.record.notebook}">
						 		  <c:url var="recordURL"
										value="/notebookEditor/${notification.record.id}" />
						 		  </c:when>
						 		  <c:otherwise>
						 			<c:url var="recordURL"
										value="/workspace/editor/structuredDocument/${notification.record.id}" />
						 		  </c:otherwise>
						 	    </c:choose>				
								<a href="${recordURL}"> ${notification.record.name} (${notification.record.globalIdentifier}) </a>								
							</c:if>
						  
								<c:if test="${not empty notification.message}">
							 with message: <br />
							     ${notification.message}
							</c:if>
							</div>
							</div>
						</td>
						<td width="120" valign="top" class="leftInfo"
							style="vertical-align: center;"><input type="checkbox"
							class="deleteSingleNotificn" aria-label="Mark as read" /> Mark as read</td>
					</tr>
				</table>
			</c:forEach>
		</c:otherwise>
	</c:choose>
</div>