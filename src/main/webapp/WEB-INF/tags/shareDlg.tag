<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags" %>
<%@ taglib uri="http://researchspace.com/tags" prefix="rst" %>
<%@ taglib prefix="shiro" uri="http://shiro.apache.org/tags" %>
<%@ attribute name="shareDlgGroups" required="true" type="java.util.Set" %>
<%@ attribute name="shareDlgUsers" required="true" type="java.util.List" %>

<fmt:bundle basename="bundles.workspace.workspace">
	<!-- Share dialog, initially hidden -->
	<div id="share-dialog" style="display: none; padding-bottom: 0;">

		<div id="accordion" class="accordion" style="min-height: 434px;">
			<h3 id="shareGroupHeader"><fmt:message key="sharing.groups.title"/>
				<span class="shareGroupSelected" style="float:right;"></span>
			</h3>
			<div id="shareGroupContents">
				<div class="folderShare">
					<p>Select a group to share with</p>
					<c:forEach items="${shareDlgGroups}" var="grp">
						<div class="groupSelectContainer">
							<div class="radioContainer">
								<input type="radio"
									   id="${grp.uniqueName}"
									   class="shareRadio"
									   name="shareGroupSelect"
									   aria-label="Select group to share with"
									   value="${grp.id}"
									   data-sharedFolderId="${grp.communalGroupFolderId}"
									   data-sharedSnippetFolderId = "${grp.sharedSnippetGroupFolderId}"
									   data-sharedFolderName="${grp.displayName}">
							</div>
							<div class="labelContainer">
								<label for="${grp.uniqueName}">${grp.displayName}</label>
							</div>
						</div>
					</c:forEach>
				</div>
				<axt:folderChooser folderChooserId="-shareIntoFolder"/>

				<axt:permissionChooser/>

				<div class="bootstrap-custom-flat">
					<div class="resetContainer">
						<button id="resetShareIntoFolder" class="btn btn-primary">
							Go back
						</button>
					</div>
				</div>
			</div>

			<c:if test="${not empty shareDlgGroups}">
				<h3 id="shareUserHeader"><fmt:message key="sharing.users.title"/> <span class="selectedCount" style="display: none;float: right;">(<span></span> <fmt:message key="dialogs.share.instruction.users.selectedCount"/>)</span></h3>
				<div id="shareUserContents">
					<p><fmt:message key="dialogs.share.instruction.individuals"/></p>
					<div style="padding:0; margin:0; overflow:auto; height:140px;">
						<table>
							<tr><th><fmt:message key="dialogs.share.header.user"/></th><th>
							<fmt:message key="dialogs.share.header.select"/></th><th>
							<fmt:message key="dialogs.share.header.permission"/></th></tr>
							<c:forEach items="${shareDlgUsers}" var="user" >
								<tr>
									<td>${user.fullName}</td>
									<td><input type="checkbox" class="shareSelection" name="userId" value="${user.id}" aria-label="Select user to share with"></input</td>
									<td>
										<select aria-label="Share permissions">
											<option value="read" selected><fmt:message key="sharing.permission.read"/></option>
											<option value="write"><fmt:message key="sharing.permission.write"/></option>
										</select>
									</td>
								</tr>
							</c:forEach>
						</table>
					</div>
				</div>
			</c:if>
			<rst:hasDeploymentProperty name="cloud" value="true">
				<h3 id="shareEmailHeader"><fmt:message key="sharing.others.title"/></h3>
				<div style="height: 290px;">
					<p style="margin-bottom: 0px;"><fmt:message key="sharing.others.msg"/></p>
					<fieldset>
						<div style="padding:0; margin:0">
							<table class="share-table">
								<tr><th>E-Mail</th><th><fmt:message key="dialogs.share.header.permission"></fmt:message></th></tr>
								<tr class="emailRow row">
									<td><input type="text" class="shareSelection email" name="email"></td>
									<td><select>
										<option value="read" selected><fmt:message key="sharing.permission.read"/></option>
										<option value="write"><fmt:message key="sharing.permission.write"/></option>
									</select>
									</td>
									<td> <button type="button" class="remove-email"><fmt:message key="sharing.remove.label"/></button> </td>
								</tr>
							</table>
						</div>
						<button type="button" class="add-email" style="margin: -15px 5px 3px 3px;">
							<fmt:message key="sharing.add.label"/></button>
					</fieldset>
					<fieldset style="display: none">
						<div style="padding:0; margin:0">
							<table class="group-share-table">
								<tr><th>Group</th><th><fmt:message key="dialogs.share.header.permission"></fmt:message></th></tr>
								<tr class="groupRow row">
									<td><input type="text" class="shareSelection externalGroupId" name="externalGroupId" data-groupid></td>
									<td><select>
										<option value="read" selected><fmt:message key="sharing.permission.read"/></option>
										<option value="write"><fmt:message key="sharing.permission.write"/></option>
									</select>
									</td>
									<td> <button type="button" class="remove-group"><fmt:message key="sharing.remove.label"/></button> </td>
								</tr>
							</table>
						</div>
						<button type="button" class="add-group" style="margin: -15px 5px 3px 3px;">
							<fmt:message key="sharing.add.label"/></button>
					</fieldset>
				</div>
			</rst:hasDeploymentProperty>
		</div>

		<div id="sharingFooter" style="border: 1px solid #B6B6B6; padding: 5px; line-height: 1.4em">
			<img src="/images/notice.png" style="height: 14px; vertical-align: text-top;"/>
			<fmt:message key="sharing.notice.manage.shared.document"/>
		</div>
	</div>


</fmt:bundle>
