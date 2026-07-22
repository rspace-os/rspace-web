<%@ include file="/common/taglibs.jsp"%>
<%@ taglib prefix="f" uri="http://researchspace.com/functions" %>
<div style="font-size: 1.3em;line-height:1.3em; margin-bottom:1.5em">
	<c:if test="${role eq 'ROLE_USER'}">
		<spring:message code="system:usersPage.roleLabels.user" />
	</c:if>
	<c:if test="${role eq 'ROLE_PI'}">
		<spring:message code="system:usersPage.roleLabels.pi" />
	</c:if>
	<c:if test="${role eq 'ROLE_ADMIN'}">
		<spring:message code="system.createAccount.newAdmin.label" />
	</c:if>
	<c:if test="${role eq 'ROLE_SYSADMIN'}">
		<spring:message code="system.createAccount.newSysAdmin.label" />
	</c:if>
</div>

<div id="warningDisplay" style="display:none" class="warning">
	<p>
		<span class="ui-icon ui-icon-alert" style="float: left; margin-right: .3em;"></span>
		<strong style="font-size:14px; margin: 1%;"></strong>
	</p>
</div>

<form id="createUserAccountForm">

	<!-- Common section -->
	<table class="bootstrap-custom-flat form" style="width:100%">
		<rst:hasDeploymentProperty name="standalone" value="false">
			<script type="text/javascript">
				alwaysShowPasswordFields = false;
				togglePasswordField(false);
			</script>
		</rst:hasDeploymentProperty>
		<rst:hasDeploymentProperty name="ldapAuthenticationEnabled" value="true">
			<script type="text/javascript">
				alwaysShowPasswordFields = false;
				togglePasswordField(false);
			</script>
		</rst:hasDeploymentProperty>
		<c:if test="${backdoorSysadminAccountCreationEnabled eq true}">
			<c:if test="${(role eq 'ROLE_SYSADMIN') or (role eq 'ROLE_ADMIN')}">
				<tr>
					<td colspan="3">
						<label>
							<spring:message code="system.createAccountForm.backdoorAccountLinkText" var="backdoorAccountLinkText"/>
							<spring:message code="common:help.multipleAccountsSameSso" var="multipleAccountsHelpSlug"/>
							<spring:message code="system.createAccountForm.backdoorAccountInfo">
								<spring:argument value='<a rel="noreferrer" href="${f:helpDocsUrl(multipleAccountsHelpSlug)}" target="_blank">${backdoorAccountLinkText}</a>'/>
							</spring:message>
						</label>
					</td>
					<td>
						<input class="ssoBackdoorAccountRadioInput" type="radio" value="false" name="ssoBackdoorAccount" required checked style="margin-left: 10px;" />
						<label><spring:message code="common:actions.no"/></label>
						<input class="ssoBackdoorAccountRadioInput" type="radio" value="true" name="ssoBackdoorAccount" required style="margin-left: 10px;" />
						<label><spring:message code="common:actions.yes"/></label>
					</td>
				</tr>
				<tr>
					<td colspan="4" style="padding-top: 0px; padding-bottom: 0px;"><hr></td>
				</tr>
			</c:if>
		</c:if>
		<tr>
			<td width="20%">
				<label>
					<spring:message code="system.createAccountForm.firstName.label" />
				</label>
			</td>
			<td width="30%">
				<input type="text" name="firstName" placeholder="<spring:message code='system.createAccountForm.firstName.label'/>" class="accountsInputs" required />
			</td>
			<td width="20%">
				<label>
					<spring:message code="system.createAccountForm.lastName.label" />
				</label>
			</td>
			<td width="30%">
				<input type="text" name="lastName" placeholder="<spring:message code='system.createAccountForm.lastName.label'/>" class="accountsInputs" required />
			</td>
		</tr>
		<tr>
			<td width="20%">
				<label>
					<spring:message code="system:usersPage.columns.username" />
					<div class="backdoorAccountAdditionalText" style="font-size: 7pt; display: none;">
						<spring:message code="system.createAccountForm.backdoorUsernameWarning"/>
					</div>
				</label>
			</td>
			<td width="30%">
				<input
					type="text"
					name="username"
					placeholder="<spring:message code='system:usersPage.columns.username'/>"
					class="accountsInputs"
					pattern="${usernamePattern}"
					title="${usernamePatternTitle}"
					required
				/>
			</td>
			<td width="20%">
				<label>
					<spring:message code="system.createAccountForm.email.label" />
				</label>
			</td>
			<td width="30%">
				<input
					type="email"
					name="email"
					autocomplete="off"
					placeholder="<spring:message code='system.createAccountForm.email.label'/>"
					class="accountsInputs"
					required
				/>
			</td>
		</tr>
		<c:if test="${affiliationRequired eq true }">
			<tr>
				<td width="20%">
					<label>
						<spring:message code="user.affiliation.label" />
					</label>
				</td>
				<td width="30%">
					<input
						type="text"
						name="affiliation"
						placeholder="<spring:message code='user.affiliation.label'/>"
						class="accountsInputs"
						required
					/>
				</td>
				<td></td>
				<td></td>
			</tr>
		</c:if>
		<rst:hasDeploymentProperty name="ldapAuthenticationEnabled" value="true">
			<tr>
				<td colspan="1">
					<label>
						<spring:message code="system.createAccountForm.ldapAuthQuestion"/>
					</label>
				</td>
				<td>
					<input class="ldapAuthChoice" id="ldapAuthYes" type="radio" value="true" name="ldapAuthChoice"
						   required checked style="margin-left: 10px;"/>
					<label><spring:message code="common:actions.yes"/></label>
					<input class="ldapAuthChoice" id="ldapAuthNo" type="radio" value="false" name="ldapAuthChoice"
						   required style="margin-left: 10px;"/>
					<label><spring:message code="common:actions.no"/></label>
				</td>
			</tr>
		</rst:hasDeploymentProperty>
		<tr class="createPasswordRow">
			<td width="20%">
				<label for="password">
					<spring:message code="system.createAccountForm.generatePasswordLinkLabel" var="generatePasswordLinkLabel"/>
					<spring:message code="system.createAccountForm.generatePassword">
						<spring:argument value='<a id="generatePasswordButton" href="#">${generatePasswordLinkLabel}</a>'/>
					</spring:message>
				</label>
                <p><spring:message code="system.createAccountForm.password.helpText"/></p>
			</td>
			<td width="30%">
				<input type="password" name="password" placeholder="<spring:message code='system.createAccountForm.password.placeholder'/>" class="accountsInputs" pattern="[ -~]{8,50}" title="<spring:message code='system.createAccountForm.password.title'/>" required />

			</td>
			<td width="20%">
				<label for="passwordConfirmation">
					<spring:message code="system.createAccountForm.confirmPassword.label" />
				</label>
			</td>
			<td width="30%">
				<input type="password" name="passwordConfirmation" placeholder="<spring:message code='system.createAccountForm.passwordConfirmation.placeholder'/>" class="accountsInputs" pattern="[ -~]{8,50}" title="<spring:message code='system.createAccountForm.passwordConfirmation.title'/>" required />
			</td>
		</tr>
		<tr class="createPasswordRow" >
			<td width="20%">
				<label>
					<spring:message code="system.createAccountForm.showHidePassword.label" />
					&nbsp;
					<input id="showPassword" type="checkbox" />
				</label>
			</td>
			<td colspan="3">
				<label style="color:darkblue;">
					<spring:message code="system.createAccountForm.notePassword.label" />
				</label>
			</td>
		</tr>
		<tr>
			<c:if test="${role eq 'ROLE_PI'}">
				<td width="20%">
					<label>
						<spring:message code="system.createAccountForm.labGroupName.label" />
					</label>
				</td>
				<td>
					<input type="text" name="newLabGroupName" placeholder="<spring:message code='system.createAccountForm.newLabGroupName.placeholder'/>" class="accountsInputs" required />
				</td>
			</c:if>
			<td>
				<input type="hidden" name="role" value="${role}" />
			</td>
		

		<!-- Conditionals section depending of the role (option previously selected or clicked) -->
		<c:if test="${role eq 'ROLE_USER'}">
			<tr>
				<td colspan="1"><spring:message code="system.createAccountForm.communitySelectionLabel"/></td>
				<td colspan="1">
					<input id="searchLabGroup" type="text" placeholder="<spring:message code='system.createAccountForm.labGroupFilterPlaceholder'/>" class="accountsInputs" />
				</td>
			</tr>
			<tr>
				<td colspan="1" style="display:flex">
					<div id="communitiesList">
						<input class="communitiesListOption" type="radio" value="-10" name="communityId" required />
						<label><spring:message code="common:actions.none"/></label><br>
						<c:forEach items="${communities}" var="community">
							<input class="communitiesListOption" type="radio" value="${community.id}" name="communityId" required />
							<label>${community.displayName}</label>
							<br>
						</c:forEach>
					</div>
				</td>
				<td colspan="3">
					<div id="groupsList">
						<table>
							<thead>
								<tr>
									<th width="30px"></th>
									<th><span style="font-weight: bold;" id="sortGroupsByName"><spring:message code="system.createAccountForm.groupsTable.name"/></span></th>
									<th><span style="font-weight: bold;" id="sortGroupsByPI"><spring:message code="system.createAccountForm.groupsTable.pi"/></span></th>
									<th><span style="font-weight: bold;" id="sortGroupsBySize"><spring:message code="system.createAccountForm.groupsTable.size"/></span></th>
								</tr>
							</thead>
							<tbody>
								<!-- Gets filled in with groups -->
							</tbody>
						</table>
					</div>
				</td>
			</tr>
		</c:if>

		<c:if test="${role eq 'ROLE_PI'}">
			<shiro:hasRole name="ROLE_SYSADMIN">
				<tr>
					<td width="20%" valign="top"><br><label>
							<spring:message code="system.createAccountForm.selectPiCommunity.label" /> </label></td>
					<td>
						<div id="communitiesList">
							<c:forEach items="${communities}" var="community">
								<input type="radio" value="${community.id}" name="communityId"
									required /><label>${community.displayName}</label><br>
							</c:forEach>
						</div>
					</td>
					<td></td>
					<td></td>
				</tr>
			</shiro:hasRole>
			<shiro:hasRole name="ROLE_ADMIN">
				<c:forEach items="${communities}" var="community">
					<input type="hidden" value="${community.id}" name="communityId" />
				</c:forEach>
			</shiro:hasRole>
		</c:if>

		<c:if test="${role eq 'ROLE_ADMIN'}">
			<shiro:hasRole name="ROLE_SYSADMIN">
				<tr>
					<td width="20%" valign="top"><br>
						<label>
							<spring:message code="system.createAccountForm.selectAdminCommunity.label" />
						</label>
					</td>
					<td>
						<div id="communitiesList">
							<input type="radio" value="-10" name="communityId" required />
							<label><spring:message code="common:actions.none"/></label>
							<br>
							<c:forEach items="${communities}" var="community">
								<input type="radio" value="${community.id}" name="communityId" required />
								<label>${community.displayName}</label>
								<br>
							</c:forEach>
						</div>
					</td>
					<td></td>
					<td></td>
				</tr>
			</shiro:hasRole>
			<shiro:hasRole name="ROLE_ADMIN">
				<c:forEach items="${communities}" var="community">
					<input type="hidden" value="${community.id}" name="communityId" />
				</c:forEach>
			</shiro:hasRole>
		</c:if>
	</table>

	<table class="bootstrap-custom-flat" width="100%">
		<tr width="100%">
			<td colspan="2">
				<c:if test="${ldapLookupRequired eq true}">
					<button id="getLdapDetails" class="btn btn-default" style="width: 12em">
						<span class="ui-button-text">
							<spring:message code="system.createAccountForm.ldapDetails.label" />
						</span>
					</button>
				</c:if>
			</td>
			<td align="right" style="padding-right: 20px">
				<input type="checkbox" id="checkCreateRepeatUserAccount" value="Create & Repeat" /> <spring:message code="system.createAccountForm.repeatCheckboxLabel"/>
			</td>
			<td align="right" width="60px">
				<button class="btn btn-primary" type="submit" id="submitCreateUserAccount">
					<span class="ui-button-text">
						<spring:message code="common:actions.create"/>
					</span>
				</button>
			</td>
		</tr>
	</table>

</form>