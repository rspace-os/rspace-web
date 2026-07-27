<%@ include file="/common/taglibs.jsp"%>

<head>
	<title>
		<spring:message code="userProfile.title"/>
	</title>
	<meta name="heading" content="<spring:message code='userProfile.heading'/>" />
	<meta name="menu" content="UserMenu" />
	<link rel="stylesheet" href="<rst:assetUrl value='/styles/userform.css'/>" />
	<script src="<rst:assetUrl value='/scripts/pages/messaging/messageCreation.js'/>"></script>
	<script src="<rst:assetUrl value='/scripts/pages/userform.js'/>"></script>
</head>
<jsp:include page="/WEB-INF/pages/admin/admin.jsp"></jsp:include>

<input type="hidden" name="from" value="<c:out value=" ${param.from}" />" />

<c:if test="${empty user.version}">
	<input type="hidden" name="encryptPass" value="true" />
</c:if>

<p style="visibility: hidden;">--hidden spacing--</p>

<spring:message code="userform.passwordCharsTitle" var="userformPasswordCharsTitle" htmlEscape="true"/>

<div style="font-size: 1.5em; margin: 15px 0px; display: inline-block;">
	<c:choose>
		<c:when test="${canEdit}"><spring:message code="userform.ownProfileHeading"/></c:when>
		<c:otherwise><spring:message code="userform.otherProfileHeading"/></c:otherwise>
	</c:choose>
	<span style="font-size: .7em;">
		<spring:message code="system:usersPage.columns.username"/>:
		<c:choose>
			<c:when test="${not empty user.usernameAlias}">
				<spring:message code="userform.usernameWithAlias">
					<spring:argument value="${user.username}"/>
					<spring:argument value="${user.usernameAlias}"/>
				</spring:message>
			</c:when>
			<c:otherwise>${user.username}</c:otherwise>
		</c:choose>
	</span>
</div>

<div id="profileBlock" style="display:block; overflow: auto">
	<div class="bootstrap-custom-flat">
		<div class="col-xs-9">
			<div class="col-xs-12 profileGreyBlocks">
				<strong>
					<spring:message code="system:usersPage.columns.firstName"/>:
				</strong>
				<span id="firstName">${user.firstName}</span>
			</div>

			<div class="col-xs-12 profileGreyBlocks">
				<strong>
					<spring:message code="system:usersPage.columns.lastName"/>:</strong>
				<span id="lastName">${user.lastName}</span>
			</div>

			<rst:hasDeploymentProperty name="cloud" value="true">
				<div style="line-height: 1.5em;">
					<strong>
						<spring:message code="userform.affiliationLabel"/>:</strong>
					<span id="affiliation">${user.affiliation}</span>
				</div>
			</rst:hasDeploymentProperty>

			<div class="col-xs-12 profileGreyBlocks">
				<c:if test="${canEdit == true}">
					<div class="profileEditBar pull-right col-xs-5">
						<a href="#" class="profileEditButton pull-right" id="userEditProfileButton">
							<spring:message code="common:actions.edit"/>
						</a>
					</div>
					<br><br>
				</c:if>
			</div>

			<c:if test="${canEditVerificationPassword == true && isVerificationPasswordSet == true}">
				<div class="profileGreyBlocks" style="text-align:right;padding:10px;">
					<a href="#" class="profileEditButton" id="userChangeVerificationPasswordButton">
						<spring:message code="userProfile.pageLinks.changeVerificationPassword"/>
					</a>
					<a href="#" class="profileEditButton" id="userForgotVerificationPasswordButton">
						<spring:message code="userProfile.pageLinks.forgotVerificationPassword"/>
					</a>
				</div>
			</c:if>

			<c:if test="${canEditVerificationPassword == true && isVerificationPasswordSet == false}">
				<div class="profileGreyBlocks" style="text-align:right;padding:10px;">
					<a href="#" class="profileEditButton" id="userSetVerificationPasswordButton">
						<spring:message code="userProfile.pageLinks.setVerificationPassword"/>
					</a>
				</div>
			</c:if>

			<div class="col-xs-12">
				<div class="profileGreyBlocks" style="display:flex">
					<strong style="padding-right: 5px">
						<spring:message code="user.email"/>:
					</strong>
					<span id="userEmail" style="flex-grow: 1"> ${user.email}</span>
					<c:if test="${canEditPassword == true}">
						<div class="pull-right change-password" style="text-align:right;">
							<a href="#" class="profileEditButton" id="userChangePasswordButton">
								<spring:message code="userProfile.pageLinks.changePassword"/>
							</a>
						</div>
					</c:if>
					<c:if test="${canEditEmail == true}">
						<div class="profileEditBar pull-right">
							<a href="#" class="profileEditButton" id="userChangeEmailButton">
								<spring:message code="common:actions.edit"/></a>
						</div>
					</c:if>
				</div>

				<c:if test="${user.signupSource == 'GOOGLE' || user.signupSource == 'LDAP' || user.signupSource == 'INTERNAL' || user.signupSource == 'SSO_BACKDOOR'}">
					<div class="profileSignupSource">
						<strong>
							<spring:message code="user.signupSource"/>:</strong>
						<span>
							<c:if test="${user.signupSource == 'GOOGLE'}"><spring:message code="userform.signupSource.google"/></c:if>
							<c:if test="${user.signupSource == 'LDAP'}"><spring:message code="userform.signupSource.ldap"/></c:if>
							<c:if test="${user.signupSource == 'INTERNAL'}"><spring:message code="userform.signupSource.internal"/></c:if>
							<c:if test="${user.signupSource == 'SSO_BACKDOOR'}"><spring:message code="userform.signupSource.ssoBackdoor"/></c:if>
						</span>
					</div>
				</c:if>

				<spring:message code="common:userDetails.accountEnabled" var="userformEnabledStatus"/>
				<spring:message code="common:userDetails.accountDisabled" var="userformDisabledStatus"/>
				<strong><spring:message code="userform.accountStatusLabel"/> </strong><span>${user.enabled ? userformEnabledStatus : userformDisabledStatus}</span><br>
				<c:if test="${showLastLoginDate != null}">
				<strong><spring:message code="userform.lastLoginLabel"/> </strong>
				<span>
					<fmt:formatDate pattern="yyyy-MM-dd HH:mm" value="${user.lastLogin}" />
				</span>
				</c:if>

				<c:if test="${user.signupSource == 'GOOGLE' || user.signupSource == 'LDAP'}">
					<div class="profileSignupSource">
						<strong>
							<spring:message code="user.signupSource"/>:
						</strong>
						<span>
							<c:if test="${user.signupSource == 'GOOGLE'}"><spring:message code="userform.signupSource.google"/></c:if>
							<c:if test="${user.signupSource == 'LDAP'}"><spring:message code="userform.signupSource.ldap"/></c:if>
						</span>
					</div>
				</c:if>
				<c:if test="${not empty user.sid}">
					<div class="profileSignupSource">
						<strong>
							<spring:message code="user.sid"/>:</strong>
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
						<spring:message code="user.orcid.label"/>: </strong>
					<span id="orcidIdSpan" data-orcidid="${orcidId}" style="display:none">
						<img src="/images/integrations/orcid-small.png" style="vertical-align: text-bottom;" />
						<a id="userOrcidIdLink" href="#" target="_blank"></a>
					</span>
					<c:if test="${canEdit == true}">
						<div class="pull-right">
							<a href="#" id="setOrcidIdButton" class="myProfileButton"
								data-orcidclientid="${applicationScope['RS_DEPLOY_PROPS']['orcidClientId']}"
								data-orcidredirecturi="${applicationScope['RS_DEPLOY_PROPS']['urlPrefix']}/orcid/redirect_uri">
								<spring:message code="userProfile.pageLinks.setOrcidId"/>
							</a>
							<a href="#" id="deleteOrcidIdButton" class="myProfileButton" data-orcidoptionsid="${orcidOptionsId}"
								style="display:none">
								<spring:message code="userProfile.pageLinks.deleteOrcidId"/>
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
					<spring:message code="userform.egnyte.setupHeading"/>
				</div>
				<div class="profileGreyBlocks col-xs-12"">
								<div id=" egnyteConnectedDiv" style="display:none">
					<div class="col-xs-8">
						<spring:message code="userform.egnyte.connectedNotice"/>
					</div>
					<div class="pull-right">
						<a id="egnyteDisconnectBtn" href="#" class="myProfileButton"><spring:message code="userform.egnyte.disconnectButton"/></a>
					</div>
				</div>
				<div id="egnyteDisonnectedDiv" style="display:none">
					<spring:message code="userform.egnyte.notConfiguredNotice"/>
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
						<spring:message code="userProfile.sectionLabel.collaborationGroups"/> </strong>
					<br>
					<c:forEach var="grp" items="${user.groups}" varStatus="status">
						<c:if test="${grp.groupType == 'COLLABORATION_GROUP'}">
							<c:url value="/groups/view/${grp.id}" var="groupURL"></c:url>
							<div class="profileGreyBlocks" style="line-height: 2.5em !important; vertical-align: center; margin-top: 6px;">
								<div class="groupsListLink" style="margin: 8px 20px 0 20px;">
									<a href="${groupURL}"> ${grp.displayName}</a>
								</div>
									<spring:message code="system:usersPage.columns.role"/>:
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
                        <spring:message code="userProfile.sectionLabel.projectGroups"/> </strong>
                    <br>
                    <c:forEach var="grp" items="${user.groups}" varStatus="status">
                        <c:if test="${grp.groupType == 'PROJECT_GROUP'}">
                            <c:url value="/groups/view/${grp.id}" var="groupURL"></c:url>
                            <div class="profileGreyBlocks" style="line-height: 2.5em !important; vertical-align: center; margin-top: 6px;">
                                <div class="groupsListLink" style="margin: 8px 20px 0 20px;">
                                    <a href="${groupURL}"> ${grp.displayName}</a>
                                </div>
                                <spring:message code="system:usersPage.columns.role"/>:
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
							<spring:message code="userProfile.pageLinks.uploadImage"/></a>
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
				<spring:message code="user.apiKey.label"/>
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

	<div id="uploadImageDialog" style="display: none;">
		<p>
			<spring:message code="dialogs.uploadImage.instruction"/>
		</p>

		<div id="uploadImageTabs">
			<ul>
				<li id="uploadImageTabLink"><a href="#uploadImageDiv"><spring:message code="userform.uploadImageTabLink"/></a></li>
				<li id="takePhotoTabLink" style="display:none"><a href="#takePhotoDiv"><spring:message code="userform.takePictureButton"/></a></li>
			</ul>

			<div id="uploadImageDiv">
				<input type="file" id="fileChooser" aria-label="<spring:message code='userform.chooseFileAriaLabel'/>" style="margin-bottom: 10px;" /><br>
				<output id="imagePreview"></output>
			</div>
			<div id="takePhotoDiv">
				<div style="display: flex; height: 150px;">
					<div style="width: 130px;">
						<span><spring:message code="userform.cameraViewLabel"/></span><br />
						<video id="profileImageVideo" width="120" height="120" autoplay></video>
					</div>
					<div style="width: 110px;">
						<button id="profileImageVideoSnap" style="margin: 70px 10px;">
							<spring:message code="userform.takePictureButton"/></button>
					</div>
					<div style="width: 130px;">
						<span><spring:message code="userform.pictureLabel"/></span><br />
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
							<spring:message code="system:usersPage.columns.firstName"/>
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
							<spring:message code="system:usersPage.columns.lastName"/>
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
						<td><label for="newAffiliation"><spring:message code="userform.affiliationLabel"/> </label></td>
						<td><input value="${user.affiliation}" name="newAffiliation" id="newAffiliation" class="form-control" />
						</td>
					</tr>
				</rst:hasDeploymentProperty>
			</table>
		</form>
		<div id="msgAreaProfile" class="msgArea"></div>
	</div>

	<div id="changePasswordDialog" style="display: none;">
		<form id="changePasswordForm">
			<table>
				<tr>
					<td>
						<label for="currentPasswordInput">
							<spring:message code="dialogs.changePassword.label.current"/>
						</label>
					</td>
					<td><input type="password" value="" id="currentPasswordInput" class="form-control" /></td>
				</tr>
				<tr>
					<td>
						<label for="newPasswordInput">
							<spring:message code="dialogs.changePassword.label.new"/>
						</label>
					</td>
					<td style="display: flex; flex-direction: column;">
						<input type="password" value="" id="newPasswordInput" class="form-control"
							   pattern="[ -~]{8,50}"
							   title="${userformPasswordCharsTitle}"
						/>
						<p><spring:message code="userform.passwordCharsHint"/></p>
					</td>
				</tr>
				<tr>
					<td>
						<label for="newPasswordConfirm">
							<spring:message code="dialogs.changePassword.label.confirm"/>
						</label>
					</td>
					<td><input type="password" value="" id="newPasswordConfirm" class="form-control" /></td>
				</tr>
			</table>
		</form>
		<div id="msgAreaPassword" class="msgArea"></div>
	</div>

	<div id="changeVerificationPasswordDialog" style="display: none;">
        <form id="changeVerificationPasswordForm">
            <table>
                <tr>
                   <td colspan="2" style="white-space: initial">
                     <p><spring:message code="dialogs.verificationPassword.description"/></p>
                   </td>
                <tr>
                <tr>
                    <td>
                        <label for="currentVerificationPasswordInput">
                            <spring:message code="dialogs.changeVerificationPassword.label.current"/>
                        </label>
                    </td>
                    <td><input type="password" value="" id="currentVerificationPasswordInput" class="form-control" /></td>
                </tr>
                <tr>
                    <td>
                        <label for="newVerificationPasswordInput">
                            <spring:message code="dialogs.changeVerificationPassword.label.new"/>
                        </label>
                    </td>
                    <td style="display: flex; flex-direction: column;">
                        <input type="password" value="" id="newVerificationPasswordInput" class="form-control"
                               pattern="[ -~]{8,50}"
                               title="${userformPasswordCharsTitle}"
                        />
                        <p><spring:message code="userform.passwordCharsHint"/></p>
                    </td>
                </tr>
                <tr>
                    <td>
                        <label for="newVerificationPasswordConfirm">
                            <spring:message code="dialogs.changeVerificationPassword.label.confirm"/>
                        </label>
                    </td>
                    <td><input type="password" value="" id="newVerificationPasswordConfirm" class="form-control" /></td>
                </tr>
            </table>
        </form>
		<div id="msgAreaVerificationPassword" class="msgArea"></div>
	</div>

	<div id="setVerificationPasswordDialog" style="display: none;">
		<table>
		    <tr>
		        <td colspan="2" style="white-space: initial">
		            <p>
		                <spring:message code="dialogs.verificationPassword.description"/>
                    </p>
                </td>
			<tr>
				<td style="width: 35%">
					<label for="newSetVerificationPasswordInput">
						<spring:message code="dialogs.setVerificationPassword.label.new"/>
					</label>
				</td>
                <td style="display: flex; flex-direction: column;">
                    <input type="password" value="" id="newSetVerificationPasswordInput" class="form-control"
                           pattern="[ -~]{8,50}"
                           title="${userformPasswordCharsTitle}"
                    />
                    <p><spring:message code="userform.passwordCharsHint"/></p>
                </td>
			</tr>
			<tr>
				<td>
					<label for="newSetVerificationPasswordConfirm">
						<spring:message code="dialogs.setVerificationPassword.label.confirm"/>
					</label>
				</td>
				<td><input type="password" value="" id="newSetVerificationPasswordConfirm" class="form-control" /></td>
			</tr>
		</table>
		<div id="msgAreaSetVerificationPassword" class="msgArea"></div>
	</div>

	<div id="pwdConfirmDialog" style="display: none;">
    <p>
      <strong><spring:message code="userform.apiKey.generateWarningTitle"/></strong>
    </p>
    <p>
    <spring:message code="userform.apiKey.generateWarningIntro"/>
    </p>
    <ul>
      <li><spring:message code="userform.apiKey.generateWarningRisk1"/></li>
      <li><spring:message code="userform.apiKey.generateWarningRisk2"/></li>
      <li><spring:message code="userform.apiKey.generateWarningRisk3"/></li>
    </ul>
    <p>
      <spring:message code="userform.apiKey.generatingConfirmPrompt"/>
    </p>
		<table>
			<tr>
				<td><label for="pwdConfirm"><spring:message code="userform.apiKey.confirmPasswordLabel"/> </label></td>
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
						<spring:message code="dialogs.changeEmail.label.new"/>
					</label>
				</td>
				<td><input value="${user.email}" id="newEmailInput" class="form-control" /></td>
			</tr>
			<tr>
				<td>
					<label for="newEmailConfirm">
						<spring:message code="dialogs.changeEmail.label.confirm"/>
					</label>
				</td>
				<td><input value="" id="newEmailConfirm" class="form-control" /></td>
			</tr>
			<tr>
				<td>
					<label for="emailPasswordInput">
						<spring:message code="dialogs.changeEmail.label.password"/>
					</label>
				</td>
				<td><input type="password" value="" id="emailPasswordInput" class="form-control" /></td>
			</tr>
		</table>
		<div id="msgAreaEmail" class="msgArea"></div>
	</div>


<script type="text/template" id="apiKeyDetailsTemplate">
	{{#enabled}}
    <div class="api-menu__key col-xs-8">
      {{#key}}
        <spring:message code="userform.apiKey.keyLabel"/>
        <br>
        <spring:message code="userform.apiKey.keyNoteWarning"/>
      {{/key}}
      {{^key}}
        {{#revokable}}
          <spring:message code="userform.apiKey.keyGeneratedAgo"/>
        {{/revokable}}
        {{^revokable}}
          <spring:message code="userform.apiKey.noKeySet"/>
        {{/revokable}}
      {{/key}}

      <br />
      <spring:message code="userform.apiKey.docsLinkPrefix"/>
      <spring:message code="userform.apiKey.docsLinkSuffix"/>
    </div>

    <div class="api-menu__buttons pull-right">
       {{#regenerable}}
         <div class="api-menu__edit">
           <a class="profileEditButton api-menu__button pull-right" href="#" id="apiKeyRegenerateBtn">
            {{#revokable}}<spring:message code="userform.apiKey.regenerateKeyButton"/>{{/revokable}}{{^revokable}}<spring:message code="userform.apiKey.generateKeyButton"/>{{/revokable}}</a>
         </div>
         <br>
       {{/regenerable}}
       {{#revokable}}
         <div class="api-menu__edit">
           <a class="profileEditButton api-menu__button pull-right" href="#" id="apiKeyRevokeBtn"><spring:message code="userform.apiKey.revokeKeyButton"/></a>
         </div>
       {{/revokable}}
    </div>
  {{/enabled}}
  {{^enabled}}
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
				<spring:message code="common:actions.remove"/>
			</button>
		</td>
	</tr>
</script>

<rst:bundle bundle="accountActivity" />
<rst:bundle bundle="groupActivity" />
<rst:bundle bundle="oAuth" />
<rst:bundle bundle="connectedApps" />
<rst:bundle bundle="labgroupsTable" />
