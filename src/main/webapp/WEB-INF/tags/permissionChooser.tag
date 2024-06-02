<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<div id="permissionChooser" style="display: none;">
    <h4>Specify permissions</h4>
    <fmt:bundle basename="bundles.workspace.workspace">
        <p>I want members of the
            <strong><span class="selectedGroupName"></span></strong>
            group to be able to
            <select class="sharedIntoFolderPermission"
                    aria-label="Share privileges">
                <option value="read" selected>
                    <fmt:message key="sharing.permission.read"/>
                </option>
                <option value="write">
                    <fmt:message key="sharing.permission.write"/>
                </option>
            </select>
            my shared item(s).
        </p>
    </fmt:bundle>
</div>
