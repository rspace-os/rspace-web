<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<div id="permissionChooser" style="display: none;">
    <h4><spring:message code="dialogs.permissionChooser.title"/></h4>
    <spring:message code="common:shareDialog.permissions.read" var="readPermission"/>
    <spring:message code="common:shareDialog.permissions.edit" var="editPermission"/>
    <spring:message code="dialogs.permissionChooser.selectAriaLabel" var="permissionAriaLabel"/>
    <c:set var="groupName"><strong><span class="selectedGroupName"></span></strong></c:set>
    <c:set var="permissionSelect"><select class="sharedIntoFolderPermission" aria-label="${permissionAriaLabel}"><option value="read" selected>${readPermission}</option><option value="write">${editPermission}</option></select></c:set>
    <p>
        <spring:message code="dialogs.permissionChooser.sentence">
            <spring:argument value="${groupName}"/>
            <spring:argument value="${permissionSelect}"/>
        </spring:message>
    </p>
</div>
