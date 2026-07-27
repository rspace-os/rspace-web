<%@ include file="/common/taglibs.jsp"%>

<c:if test="${canEdit && not empty requests}">
    <div class="pending-invitations">
        <h3><spring:message code="pendingInvitations.heading"/></h3>
        <table id="invitedMembers" class="table">
            <tr>
                <th><spring:message code="common:userDetails.email"/></th>
                <th><spring:message code="pendingInvitations.table.action"/></th>
            </tr>
            <tbody>
            <c:forEach items="${requests}" var="request">
                <c:forEach items="${request.recipients}" var="recipient">
                    <tr>
                        <td class="email">${recipient.email}</td>
                        <td class="action"><a href="#" class="cancel" data-requestid="${request.requestId}" data-recipientid="${recipient.recipientId}"><spring:message code="common:actions.cancel"/></a></td>
                    </tr>
                </c:forEach>
            </c:forEach>
            </tbody>
        </table>
    </div>
</c:if>
