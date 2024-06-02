<%@ include file="/common/taglibs.jsp"%>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta charset="UTF-8">
    <title><spring:message code="system.communityList.button.label" /></title>
    <link rel="stylesheet" href="<c:url value='/styles/system.css'/>" />
    <script src="<c:url value='/scripts/pages/system/system.js'/>"></script>
    <script src="<c:url value='/scripts/pages/system/community.js'/>"></script>
    <script src="<c:url value='/scripts/pages/system/community_settings_mod.js'/>"></script>
    <script type="text/javascript">
        RS.communityId = ${community.id};
        var view = ${view};
    </script>
</head>

<div id="topSection" class="bootstrap-custom-flat">
    <jsp:include page="topBar.jsp"></jsp:include>
</div>

<%-- Community info page --%>
<div id="communityProps" style="margin-top: 20px;">
    <%--either the form is displayed (When editing) or the div 'propertyView' when viewing.. toggled in jquery --%>
    <form:form modelAttribute="community" style="display:none" id='editPropertyForm' method='POST' action='/community/admin/edit'>
        <span class="color:red"><form:errors path="*"></form:errors></span>
        <br />
        <table width="100%">
            <tr>
                <td width="12%"><label for='displayName'><spring:message code="community.displayName.label" /></label></td>
                <td><form:input path="displayName" />
                <td />
            </tr>
            <tr>
                <td width="12%" valign="top"><label
                    for='profileText'><spring:message code="community.profile.label" /></label></td>
                <td><form:textarea path="profileText"
                        placeholder="Optionally, enter a short text description of this community (max 255 chars)"
                        maxlength="255" />
                <td />
            </tr>
            <tr>
                <td width="12%"><form:hidden path="id" /></td>
                <td><input id='editProfileSubmit' type='submit' value='Save' class="systemButton systemSaveButton"> 
                    <a id='editProfileCancel' href='#' class="systemButton systemCancelButton">Cancel</a></td>
            </tr>
        </table>
    </form:form>
    <div id="propertyView">
        <h2>
            <span id="displayName" class="editableProperty">${community.displayName}</span>
            <span style="font-size: initial">(created: <fmt:formatDate type="date" value="${community.creationDate}" />)</span>
        </h2>
        <h3><spring:message code="community.profileHeading" /></h3>
        <span id="profileText" class="editableProperty"> <c:choose>
                <c:when test="${empty community.profileText}">
                    <spring:message code="community.noprofile.msg" />
                </c:when>
                <c:otherwise>
                    ${community.profileText}
                </c:otherwise>
            </c:choose>
        </span>
    </div>
</div>
<br>

<c:if test="${canEdit}">
    <a href="#" id="editCommunityProps" class="systemButton systemEditButton">Edit</a>
    <c:if test="${community.id != -1}"> <%-- i.e. not a default 'All Groups' community --%>
        <a href="#" id="editCommunityAppsSettings" class="systemButton systemEditButton">Apps Settings</a>
    </c:if>
</c:if>

<br>
<br>
<div style="width: 470px; float: left;">
    <h3><spring:message code="community.labGroups.header" /></h3>
    <div id="labGroupContainer">
        <div id="labGroupList">
            <div id="currentCommunityListing">
                <div class="topGreyBar">
                    <shiro:hasRole name="ROLE_SYSADMIN">
                        <c:if test="${community.id ne -1}">
                            <%--only sysadmin can remove a group from a community --%>
                            <a id="removeGroup" class="crudops systemButton systemRemoveButton" href="#">Remove group</a>
                            <a id="addGroup" class="systemButton systemAddLabGroupButton" href="#">Add group</a>
                        </c:if>
                        <a id="moveGroup" class="crudops systemButton systemMoveButton" href="#">Move group</a>
                    </shiro:hasRole>
                </div>
                <div class="communityViewInnerList">
                    <c:choose>
                        <c:when test="${empty community.labGroups}">
                            <spring:message code="community.nolabGroups.msg" />
                        </c:when>
                        <c:otherwise>
                            <table>
                                <c:forEach items="${community.labGroups}" var="group">
                                    <tr>
                                        <shiro:hasRole name="ROLE_SYSADMIN">
                                          <c:if test="${canEdit}">
                                            <td><input class="actionCbox" type="checkbox" id="group_${group.id}" /></td>
                                          </c:if>
                                        </shiro:hasRole>
                                        <td class="name"><a href="<c:url value='/groups/view/${group.id}'/>">${group.displayName}</a></td>
                                    </tr>
                                </c:forEach>
                            </table>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
            <jsp:include page="communityAddGroup.jsp"></jsp:include>
        </div>
    </div>
</div>

<div style="width: 470px; float: right;">
    <h3><spring:message code="community.admins.header" /></h3>
    <div id="adminContainer">
        <div id="adminsList">
            <div class="topGreyBar">
                <c:if test="${canEdit}">
                    <a href="#" id="addAdminLink" class="systemButton systemAddAdminButton">Add admin</a>
                </c:if>
            </div>
            <div class="communityViewInnerList">
                <table>
                    <c:forEach items="${community.admins}" var="admin">
                        <tr>
                            <td><a class="adminLink" href="<c:url value='/userform?userId=${admin.id}'/>">${admin.fullNameAndEmail}</a></td>
                            <td><c:if test="${canEdit}">
                                    <a id="remove_${admin.id}" href="#" class="removeAdminLink">Remove</a>
                                </c:if>
                            </td>
                        <tr>
                    </c:forEach>
                </table>
            </div>
        </div>
    </div>
</div>

<!-- Apps settings dialog -->
<div id="communityAppsSettingsDialog"
    title="Apps Settings for this Community">
    <div id="mainArea">test dialog</div>
</div>
<!-- End of apps settings dialog -->
