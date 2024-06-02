<%@ include file="/common/taglibs.jsp"%>

<head>
	<title>
		<fmt:message key="userProfile.title" />
	</title>
	<meta name="heading" content="<fmt:message key='userProfile.heading'/>" />
	<meta name="menu" content="UserMenu" />
	<link rel="stylesheet" href="<c:url value='/styles/userform.css'/>" />
	<script src="<c:url value='/scripts/pages/messaging/messageCreation.js'/>"></script>
	<script src="<c:url value='/scripts/pages/userform.js'/>"></script>
</head>
<jsp:include page="/WEB-INF/pages/admin/admin.jsp"></jsp:include>

<input type="hidden" name="from" value="<c:out value=" ${param.from}" />" />

<%-- commenting as seems unused (mk - 22/09/16) --%>
<%-- <c:if test="${cookieLogin == 'true'}"> --%>
<%-- 	<form:hidden path="password" /> --%>
<%-- 	<form:hidden path="confirmPassword" /> --%>
<%-- </c:if> --%>

<c:if test="${empty user.version}">
	<input type="hidden" name="encryptPass" value="true" />
</c:if>

<p style="visibility: hidden;">--hidden spacing--</p>

<div style="font-size: 1.5em; margin: 15px 0px; display: inline-block;">
	<c:if test="${canEdit == true}">My </c:if>
	Profile <span style="font-size: .7em;"> -
		<fmt:message key="user.username" />: ${user.username}</span>
</div>

<div id="profileBlock" style="display:block; overflow: auto">
	<div class="bootstrap-custom-flat">
		<div class="col-xs-9">
			<div class="col-xs-12 profileGreyBlocks">
				<strong>
					<fmt:message key="user.firstName" />:
				</strong>
				<span id="firstName">${user.firstName}</span>
			</div>

			<div class="col-xs-12 profileGreyBlocks">
				<strong>
					<fmt:message key="user.lastName" />:</strong>
				<span id="lastName">${user.lastName}</span>
			</div>

			<rst:hasDeploymentProperty name="cloud" value="true">
				<div style="line-height: 1.5em;">
					<strong>
						<fmt:message key="Affiliation" />:</strong>
					<span id="affiliation">${user.affiliation}</span>
				</div>
			</rst:hasDeploymentProperty>

			<div class="col-xs-12 profileGreyBlocks" style="line-height: 1.5em;">
				<strong><span id="linkDescription">${profile.externalLinkDisplay}</span></strong>
				<a id="externalLink" href="${profile.externalLinkURL}" <c:if test="${empty profile.externalLinkURL}">style="display:
					none;"</c:if>>
					${profile.externalLinkURL}
				</a>
			</div>

			<div class="col-xs-12 profileGreyBlocks">
				<div id="additionalInfo" class="col-xs-7">
					${profile.profileText}
				</div>
				<c:if test="${canEdit == true}">
					<div class="profileEditBar pull-right col-xs-5">
						<a href="#" class="profileEditButton pull-right" id="userEditProfileButton">
							<fmt:message key="userProfile.pageLinks.editDetails" />
						</a>
					</div>
					<br><br>
				</c:if>
			</div>

			<c:if test="${canEditVerificationPassword == true && isVerificationPasswordSet == true}">
				<div class="profileGreyBlocks" style="text-align:right;padding:10px;">
					<a href="#" class="profileEditButton" id="userChangeVerificationPasswordButton">
						<fmt:message key="userProfile.pageLinks.changeVerificationPassword" />
					</a>
					<a href="#" class="profileEditButton" id="userForgotVerificationPasswordButton">
						<fmt:message key="userProfile.pageLinks.forgotVerificationPassword" />
					</a>
				</div>
			</c:if>

			<c:if test="${canEditVerificationPassword == true && isVerificationPasswordSet == false}">
				<div class="profileGreyBlocks" style="text-align:right;padding:10px;">
					<a href="#" class="profileEditButton" id="userSetVerificationPasswordButton">
						<fmt:message key="userProfile.pageLinks.setVerificationPassword" />
					</a>
				</div>
			</c:if>

			<div class="col-xs-12">
				<div class="profileGreyBlocks" style="display:flex">
					<strong style="padding-right: 5px">
						<fmt:message key="user.email" />:
					</strong>
					<span id="userEmail" style="flex-grow: 1"> ${user.email}</span>
					<c:if test="${canEditPassword == true}">
						<div class="pull-right change-password" style="text-align:right;">
							<a href="#" class="profileEditButton" id="userChangePasswordButton">
								<fmt:message key="userProfile.pageLinks.changePassword" />
							</a>
						</div>
					</c:if>
					<c:if test="${canEditEmail == true}">
						<div class="profileEditBar pull-right">
							<a href="#" class="profileEditButton" id="userChangeEmailButton">
								<fmt:message key="userProfile.pageLinks.changeEmail" /></a>
						</div>
					</c:if>
				</div>

				<c:if test="${user.signupSource == 'GOOGLE' || user.signupSource == 'LDAP' || user.signupSource == 'INTERNAL' || user.signupSource == 'SSO_BACKDOOR'}">
					<div class="profileSignupSource">
						<strong>
							<fmt:message key="user.signupSource" />:</strong>
						<span>
							<c:if test="${user.signupSource == 'GOOGLE'}">Google</c:if>
							<c:if test="${user.signupSource == 'LDAP'}">LDAP</c:if>
							<c:if test="${user.signupSource == 'INTERNAL'}">This is an RSpace internal account</c:if>
							<c:if test="${user.signupSource == 'SSO_BACKDOOR'}">This is an administrative SSO account used for RSpace maintenance</c:if>
						</span>
					</div>
				</c:if>

				<strong>Account Status: </strong><span>${user.enabled ? "Enabled" : "Disabled"}</span><br>
				<c:if test="${showLastLoginDate != null}">
				<strong>Last Login: </strong>
				<span>
					<fmt:formatDate pattern="yyyy-MM-dd HH:mm" value="${user.lastLogin}" />
				</span>
				</c:if>

				<c:if test="${user.signupSource == 'GOOGLE' || user.signupSource == 'LDAP'}">
					<div class="profileSignupSource">
						<strong>
							<fmt:message key="user.signupSource" />:
						</strong>
						<span>
							<c:if test="${user.signupSource == 'GOOGLE'}">Google</c:if>
							<c:if test="${user.signupSource == 'LDAP'}">LDAP</c:if>
						</span>
					</div>
				</c:if>
				<c:if test="${not empty user.sid}">
					<div class="profileSignupSource">
						<strong>
							<fmt:message key="user.sid" />:</strong>
						<span>${user.sid}</span>
					</div>
				</c:if>

				<rst:hasDeploymentProperty name="SSOSelfDeclarePiEnabled" value="true">
					<jsp:include page="/WEB-INF/pages/admin/userprofile/selfDeclarePiFragment.jsp" />
				</rst:hasDeploymentProperty>
				
				<br>
				<hr>
			</div>

			<c:if test="${orcidAvailable and (canEdit or not empty orcidId) }">
				<div class="profileGreyBlocks col-xs-12">
					<strong>
						<fmt:message key="user.orcid.label" />: </strong>
					<span id="orcidIdSpan" data-orcidid="${orcidId}" style="display:none">
						<img src="/images/integrations/orcid-small.png" style="vertical-align: text-bottom;" />
						<a id="userOrcidIdLink" href="#" target="_blank"></a>
					</span>
					<c:if test="${canEdit == true}">
						<div class="pull-right">
							<a href="#" id="setOrcidIdButton" class="myProfileButton"
								data-orcidclientid="${applicationScope['RS_DEPLOY_PROPS']['orcidClientId']}"
								data-orcidredirecturi="${applicationScope['RS_DEPLOY_PROPS']['urlPrefix']}/orcid/redirect_uri">
								<fmt:message key="userProfile.pageLinks.setOrcidId" />
							</a>
							<a href="#" id="deleteOrcidIdButton" class="myProfileButton" data-orcidoptionsid="${orcidOptionsId}"
								style="display:none">
								<fmt:message key="userProfile.pageLinks.deleteOrcidId" />
							</a>
						</div>
					</c:if>
					<br><br>
				</div>
				<div class="col-xs-12">
					<hr>
				</div>
			</c:if>

			<rst:hasDeploymentProperty name="fileStoreType" value="EGNYTE">
				<div style="font-size: 1.5em; margin-bottom: 15px; color: #444;">
					Egnyte filestore setup
				</div>
				<div class="profileGreyBlocks col-xs-12"">
								<div id=" egnyteConnectedDiv" style="display:none">
					<div class="col-xs-8">
						Your RSpace account is connected to Egnyte filestore.
					</div>
					<div class="pull-right">
						<a id="egnyteDisconnectBtn" href="#" class="myProfileButton">Disconnect</a>
					</div>
				</div>
				<div id="egnyteDisonnectedDiv" style="display:none">
					There is a problem with your connection to Egnyte filestore, please <a
						href="/egnyte/egnyteConnectionSetup">re-authenticate</a>.
				</div>
				</div>
				<div class="col-xs-12">
					<hr>
				</div>
			</rst:hasDeploymentProperty>

			<rst:hasCollaborationGroup user="${user}">
				<br />
				<div class="profileGreyBlocks col-xs-12">
					<strong>
						<fmt:message key="userProfile.sectionLabel.collaborationGroups" /> </strong>
					<br>
					<c:forEach var="grp" items="${user.groups}" varStatus="status">
						<c:if test="${grp.groupType == 'COLLABORATION_GROUP'}">
							<c:url value="/groups/view/${grp.id}" var="groupURL"></c:url>
							<div class="profileGreyBlocks" style="line-height: 2.5em !important; vertical-align: center; margin-top: 6px;">
								<div class="groupsListLink" style="margin: 8px 20px 0 20px;">
									<a href="${groupURL}"> ${grp.displayName}</a>
								</div>
								Role:
								<rst:roleInGroup group="${grp}" user="${user}" />
							</div>
						</c:if>
					</c:forEach>
					<hr class="col-xs-12">
				</div>
			</rst:hasCollaborationGroup>
			<rst:hasProjectGroup user="${user}">
            	<br />
                <div class="profileGreyBlocks col-xs-12">
                    <strong>
                        <fmt:message key="userProfile.sectionLabel.projectGroups" /> </strong>
                    <br>
                    <c:forEach var="grp" items="${user.groups}" varStatus="status">
                        <c:if test="${grp.groupType == 'PROJECT_GROUP'}">
                            <c:url value="/groups/view/${grp.id}" var="groupURL"></c:url>
                            <div class="profileGreyBlocks" style="line-height: 2.5em !important; vertical-align: center; margin-top: 6px;">
                                <div class="groupsListLink" style="margin: 8px 20px 0 20px;">
                                    <a href="${groupURL}"> ${grp.displayName}</a>
                                </div>
                                Role:
                                <rst:roleInGroup group="${grp}" user="${user}" />
                            </div>
                        </c:if>
                    </c:forEach>
                    <hr class="col-xs-12">
                </div>
            </rst:hasProjectGroup>
		</div>

		<div class="col-xs-3">
			<div id="profileBlockRight" class="pull-right">
				<div id="portraitHolder">
					<img id="profileImage" src="/userform/profileImage/${profile.id}/${profileImageId}">
				</div>
				<c:if test="${canEdit == true}">
					<div class="portraitEditBar">
						<a href="#" class="profileUploadButton" id="userUploadImageButton">
							<fmt:message key="userProfile.pageLinks.uploadImage" /></a>
					</div>
				</c:if>
			</div>
		</div>
	</div>
</div>

<c:if test="${not empty user.groups}">
	<div id="labgroups-table" data-canedit="${canEdit}" data-username="${user.username}" data-userid="${user.id}"></div>
</c:if>

<div class="bootstrap-custom-flat">
	<div class="col-xs-9">
		<hr />
		<c:if test="${canEdit == true}">
			<jsp:include page="/WEB-INF/pages/admin/userprofile/preferences.jsp" />
			<hr class="col-xs-12" />
		</c:if>
	</div>
</div>

<div id="group-activity" data-userid="${user.id}"></div>
<div id="account-activity" data-userid="${user.id}"></div>
<c:if test="${canEdit}">
	<div id="oAuthApps"></div>
	<div id="connected-apps"></div>
</c:if>

<div class="bootstrap-custom-flat">
	<div class="col-xs-9" style="padding: 0px">
		<hr />
		<c:if test="${canEdit}">
			<div class="api-menu__header col-xs-12">
				<fmt:message key="user.apikey.label" />
			</div>
			<br>
			<div id="apiKeyInfo" class="api-menu__content col-xs-12" style="padding: 0px;"></div>
		</c:if>
	</div>
</div>

<%-- Dialog for creating request --%>
<div id="createRequestDlg" style="display: none">
	<div id="createRequestDlgContent"></div>
</div>

<fmt:bundle basename="bundles.admin.admin">
	<div id="uploadImageDialog" style="display: none;">
		<p>
			<fmt:message key="dialogs.uploadImage.instruction"></fmt:message>
		</p>

		<div id="uploadImageTabs">
			<ul>
				<li id="uploadImageTabLink"><a href="#uploadImageDiv">Upload Image</a></li>
				<li id="takePhotoTabLink" style="display:none"><a href="#takePhotoDiv">Take a Picture</a></li>
			</ul>

			<div id="uploadImageDiv">
				<input type="file" id="fileChooser" aria-label="Choose a file" style="margin-bottom: 10px;" /><br>
				<output id="imagePreview"></output>
			</div>
			<div id="takePhotoDiv">
				<div style="display: flex; height: 150px;">
					<div style="width: 130px;">
						<span>Camera view:</span><br />
						<video id="profileImageVideo" width="120" height="120" autoplay></video>
					</div>
					<div style="width: 110px;">
						<button id="profileImageVideoSnap" style="margin: 70px 10px;">
							Take a Picture</button>
					</div>
					<div style="width: 130px;">
						<span>Picture:</span><br />
						<canvas id="profileImageVideoCanvas" width="120" height="120"></canvas>
					</div>
				</div>
			</div>
		</div>
		<div id="msgAreaImage" class="msgArea"></div>
	</div>

	<div id="editProfileDialog" style="display: none;">
		<form id="userProfileForm">
			<table>
				<tr>
					<td>
						<label for="firstNameInput">
							<fmt:message key="dialogs.editProfile.label.firstName"></fmt:message>
						</label>
					</td>
					<td>
						<input
							value="${user.firstName}"
							id="firstNameInput"
							name="firstNameInput"
							class="form-control" <c:if
							test="${not canEditName}"> readonly </c:if>
						/>
					</td>
				</tr>
				<tr>
					<td>
						<label for="surnameInput">
							<fmt:message key="dialogs.editProfile.label.lastName"></fmt:message>
						</label>
					</td>
					<td>
						<input
							value="${user.lastName}"
							name="surnameInput"
							id="surnameInput"
							class="form-control" <c:if
							test="${not canEditName}"> readonly </c:if>
						/>
					</td>
				</tr>
				<rst:hasDeploymentProperty name="cloud" value="true">
					<tr>
						<td><label for="newAffiliation">Affiliation </label></td>
						<td><input value="${user.affiliation}" name="newAffiliation" id="newAffiliation" class="form-control" />
						</td>
					</tr>
				</rst:hasDeploymentProperty>
				<tr>
					<td>
						<label for="externalLinkInput">
							<fmt:message key="dialogs.editProfile.label.link"></fmt:message>
						</label>
					</td>
					<td><input value="" id="externalLinkInput" name="externalLinkInput" class="form-control" /></td>
				</tr>
				<tr>
					<td>
						<label for="linkDescriptionInput">
							<fmt:message key="dialogs.editProfile.label.linkDescription"></fmt:message>
						</label>
					</td>
					<td><input value="" id="linkDescriptionInput" name="linkDescriptionInput" class="form-control" /></td>
				</tr>
				<tr>
					<td>
						<label for="additionalInfoArea">
							<fmt:message key="dialogs.editProfile.label.additional"></fmt:message>
						</label>
					</td>
					<td><textarea rows="5" id="additionalInfoArea" name="additionalInfoArea"
							class="form-control"> ${profile.profileText} </textarea></td>
				</tr>
			</table>
		</form>
		<div id="msgAreaProfile" class="msgArea"></div>
	</div>

	<div id="changePasswordDialog" style="display: none;">
		<table>
			<tr>
				<td>
					<label for="currentPasswordInput">
						<fmt:message key="dialogs.changePassword.label.current"></fmt:message>
					</label>
				</td>
				<td><input type="password" value="" id="currentPasswordInput" class="form-control" /></td>
			</tr>
			<tr>
				<td>
					<label for="newPasswordInput">
						<fmt:message key="dialogs.changePassword.label.new"></fmt:message>
					</label>
				</td>
				<td><input type="password" value="" id="newPasswordInput" class="form-control" /></td>
			</tr>
			<tr>
				<td>
					<label for="newPasswordConfirm">
						<fmt:message key="dialogs.changePassword.label.confirm"></fmt:message>
					</label>
				</td>
				<td><input type="password" value="" id="newPasswordConfirm" class="form-control" /></td>
			</tr>
		</table>
		<div id="msgAreaPassword" class="msgArea"></div>
	</div>

	<div id="changeVerificationPasswordDialog" style="display: none;">
		<table>
		    <tr>
               <td colspan="2">
                 <div style="max-width: 600px; white-space: normal;"><fmt:message key="dialogs.verificationPassword.description"/></div>

               </td>
            <tr>
			<tr>
				<td>
					<label for="currentVerificationPasswordInput">
						<fmt:message key="dialogs.changeVerificationPassword.label.current"></fmt:message>
					</label>
				</td>
				<td><input type="password" value="" id="currentVerificationPasswordInput" class="form-control" /></td>
			</tr>
			<tr>
				<td>
					<label for="newVerificationPasswordInput">
						<fmt:message key="dialogs.changeVerificationPassword.label.new"></fmt:message>
					</label>
				</td>
				<td><input type="password" value="" id="newVerificationPasswordInput" class="form-control" /></td>
			</tr>
			<tr>
				<td>
					<label for="newVerificationPasswordConfirm">
						<fmt:message key="dialogs.changeVerificationPassword.label.confirm"></fmt:message>
					</label>
				</td>
				<td><input type="password" value="" id="newVerificationPasswordConfirm" class="form-control" /></td>
			</tr>
		</table>
		<div id="msgAreaVerificationPassword" class="msgArea"></div>
	</div>

	<div id="setVerificationPasswordDialog" style="display: none;">
		<table>
		    <tr>
		     <td colspan="2">
		      <div style="max-width: 600px; white-space: normal;">
		      <fmt:message key="dialogs.verificationPassword.description"/>
		       </div>
		     </td>
			<tr>
				<td>
					<label for="newSetVerificationPasswordInput">
						<fmt:message key="dialogs.setVerificationPassword.label.new"></fmt:message>
					</label>
				</td>
				<td><input type="password" value="" id="newSetVerificationPasswordInput" class="form-control" /></td>
			</tr>
			<tr>
				<td>
					<label for="newSetVerificationPasswordConfirm">
						<fmt:message key="dialogs.setVerificationPassword.label.confirm"></fmt:message>
					</label>
				</td>
				<td><input type="password" value="" id="newSetVerificationPasswordConfirm" class="form-control" /></td>
			</tr>
		</table>
		<div id="msgAreaSetVerificationPassword" class="msgArea"></div>
	</div>

	<div id="pwdConfirmDialog" style="display: none;">
		<table>
			<tr>
				<td><label for="pwdConfirm">Please confirm your password </label></td>
				<td><input type="password" value="" id="pwdConfirm" class="form-control" /></td>
			</tr>
		</table>
		<div id="msgAreaPwdConfirm" class="msgArea"></div>
	</div>

	<div id="changeEmailDialog" style="display: none;">
		<table>
			<tr>
				<td>
					<label for="newEmailInput">
						<fmt:message key="dialogs.changeEmail.label.new"></fmt:message>
					</label>
				</td>
				<td><input value="${user.email}" id="newEmailInput" class="form-control" /></td>
			</tr>
			<tr>
				<td>
					<label for="newEmailConfirm">
						<fmt:message key="dialogs.changeEmail.label.confirm"></fmt:message>
					</label>
				</td>
				<td><input value="" id="newEmailConfirm" class="form-control" /></td>
			</tr>
			<tr>
				<td>
					<label for="emailPasswordInput">
						<fmt:message key="dialogs.changeEmail.label.password"></fmt:message>
					</label>
				</td>
				<td><input type="password" value="" id="emailPasswordInput" class="form-control" /></td>
			</tr>
		</table>
		<div id="msgAreaEmail" class="msgArea"></div>
	</div>

</fmt:bundle>

<script type="text/template" id="apiKeyDetailsTemplate">
	{{#enabled}}
    <div class="api-menu__key col-xs-8">
      {{#key}}
        <strong>Key</strong>: <span id="api-menu__keyValue">{{key}}</span>
          <a href="#" id="api-menu__showKey" onclick="return false;">Show Key</a>
          <a href="#" id="api-menu__hideKey" onclick="return false;">Hide Key</a>
      {{/key}}
      {{^key}}
        <strong>Key</strong>: Empty.
      {{/key}}

      <br />
      See <a href="/public/apiDocs" target="_blank">API Documentation</a>.
      For more examples, check out our <a href="https://github.com/rspace-os" target="_blank">GitHub</a>.
    </div>

    <div class="api-menu__buttons pull-right">
       {{#regenerable}}
         <div class="api-menu__edit">
           <a class="profileEditButton api-menu__button pull-right" href="#" id="apiKeyRegenerateBtn">Regenerate key</a>
         </div>
         <br>
       {{/regenerable}}
       {{#revokable}}
         <div class="api-menu__edit">
           <a class="profileEditButton api-menu__button pull-right" href="#" id="apiKeyRevokeBtn">Revoke key</a>
         </div>
       {{/revokable}}
    </div>
  {{/enabled}}
  {{^enabled}}
    {{#key}}
    	<div class="api-menu__key api-menu__key--disabled">
    		<strong>Key</strong>: {{key}}
      </div>
    {{/key}}
    <div class="api-menu__description">
    	{{message}}
    </div>
  {{/enabled}}
</script>

<script type="text/template" id="oauthDetailsTemplate">
	<tr>
		<td>{{clientName}}</td>
		<td>{{clientId}}</td>
		<td>{{scope}}</td>
		<td class="text-right" style="padding-right: 0px">
			<button
				class="profileEditButton pull-right"
				style="color: #1465b7"
				data-id='{{clientId}}'
			>
				Remove
			</button>
		</td>
	</tr>
</script>

<script src="<c:url value='/ui/dist/accountActivity.js'/>"></script>
<script src="<c:url value='/ui/dist/groupActivity.js'/>"></script>
<script src="<c:url value='/ui/dist/oAuth.js'/>"></script>
<script src="<c:url value='/ui/dist/connectedApps.js'/>"></script>
<script src="<c:url value='/ui/dist/labgroupsTable.js'/>"></script>
