<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>

<div id="permissionChooser" style="display: none;">
    <h4><spring:message code="dialogs.permissionChooser.title"/></h4>
    <p><spring:message code="dialogs.permissionChooser.intro"/>
        <strong><span class="selectedGroupName"></span></strong>
        <spring:message code="dialogs.permissionChooser.groupToBeAbleTo"/>
        <select class="sharedIntoFolderPermission"
                aria-label="<spring:message code='dialogs.permissionChooser.selectAriaLabel'/>">
            <option value="read" selected>
                <spring:message code="common:shareDialog.permissions.read"/>
            </option>
            <option value="write">
                <spring:message code="common:shareDialog.permissions.edit"/>
            </option>
        </select>
        <spring:message code="dialogs.permissionChooser.mySharedItems"/>
    </p>
</div>
