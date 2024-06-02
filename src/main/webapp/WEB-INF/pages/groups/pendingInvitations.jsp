<%@ include file="/common/taglibs.jsp"%>

<c:if test="${canEdit && not empty requests}">
    <div class="pending-invitations">
        <h3>Pending invitations</h3>
        <table id="invitedMembers" class="table">
            <tr>
                <th>Email</th>
                <th>Action</th>
            </tr>
            <tbody>
            <c:forEach items="${requests}" var="request">
                <c:forEach items="${request.recipients}" var="recipient">
                    <tr>
                        <td class="email">${recipient.email}</td>
                        <td class="action"><a href="#" class="cancel" data-requestid="${request.requestId}" data-recipientid="${recipient.recipientId}">Cancel</a></td>
                    </tr>
                </c:forEach>
            </c:forEach>
            </tbody>
        </table>
    </div>
</c:if>
