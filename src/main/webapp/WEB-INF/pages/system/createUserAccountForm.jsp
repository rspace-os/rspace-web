<%@ include file="/common/taglibs.jsp"%>
<div style="font-size: 1.3em;line-height:1.3em; margin-bottom:1.5em">
	<c:if test="${role eq 'ROLE_USER'}">
		<spring:message code="system.createAccount.newUser.label" />
	</c:if>
	<c:if test="${role eq 'ROLE_PI'}">
		<spring:message code="system.createAccount.newPi.label" />
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
							Should this be a backdoor login account? Backdoor accounts don't use SSO for authentication, 
							can be only logged into through Admin Login screen 
							- see <a rel="noreferrer" href="https://researchspace.helpdocs.io/article/bk9ap372vv-setting-up-multiple-rspace-accounts-with-the-same-sso-identity" 
							target="_blank">RSpace Documentation</a>.
						</label>
					</td>
					<td>
						<input class="ssoBackdoorAccountRadioInput" type="radio" value="false" name="ssoBackdoorAccount" required checked style="margin-left: 10px;" />
						<label>No</label>
						<input class="ssoBackdoorAccountRadioInput" type="radio" value="true" name="ssoBackdoorAccount" required style="margin-left: 10px;" />
						<label>Yes</label>
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
				<input type="text" name="firstName" placeholder="First name" class="accountsInputs" required />
			</td>
			<td width="20%">
				<label>
					<spring:message code="system.createAccountForm.lastName.label" />
				</label>
			</td>
			<td width="30%">
				<input type="text" name="lastName" placeholder="Last name" class="accountsInputs" required />
			</td>
		</tr>
		<tr>
			<td width="20%">
				<label>
					<spring:message code="system.createAccountForm.userName.label" />
					<div class="backdoorAccountAdditionalText" style="font-size: 7pt; display: none;">
						Backdoor account - pick a username that won't conflict with potential SSO user!
					</div> 
				</label>
			</td>
			<td width="30%">
				<input 
					type="text" 
					name="username" 
					placeholder="Username" 
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
					placeholder="E-mail" 
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
						placeholder="Affiliation" 
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
						User will authenticate with LDAP?
					</label>
				</td>
				<td>
					<input class="ldapAuthChoice" id="ldapAuthYes" type="radio" value="true" name="ldapAuthChoice"
						   required checked style="margin-left: 10px;"/>
					<label>Yes</label>
					<input class="ldapAuthChoice" id="ldapAuthNo" type="radio" value="false" name="ldapAuthChoice"
						   required style="margin-left: 10px;"/>
					<label>No</label>
				</td>
			</tr>
		</rst:hasDeploymentProperty>
		<tr class="createPasswordRow">
			<td width="20%">
				<label>
					Enter or <a id="generatePasswordButton" href="#"> Generate </a> password
				</label>
			</td>
			<td width="30%">
				<input type="password" name="password" placeholder="Password" class="accountsInputs" pattern=".{8,}"
					title="Minimum 8 characters" required />
			</td>
			<td width="20%">
				<label>
					<spring:message code="system.createAccountForm.confirmPassword.label" />
				</label>
			</td>
			<td width="30%">
				<input type="password" name="passwordConfirmation" placeholder="Password Confirmation" class="accountsInputs"
					pattern=".{8,}" title="Minimum 8 characters" required />
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
						<spring:message code="system.createAccountForm.groupoName.label" />
					</label>
				</td>
				<td>
					<input type="text" name="newLabGroupName" placeholder="New LabGroup name" class="accountsInputs" required />
				</td>
			</c:if>
			<td>
				<input type="hidden" name="role" value="${role}" />
			</td>
		

		<!-- Conditionals section depending of the role (option previously selected or clicked) -->
		<c:if test="${role eq 'ROLE_USER'}">
			<tr>
				<td colspan="1">Select the new user's Community</td>
				<td colspan="1">
					<input id="searchLabGroup" type="text" placeholder="Filter and choose a LabGroup" class="accountsInputs" />
				</td>
			</tr>
			<tr>
				<td colspan="1" style="display:flex">
					<div id="communitiesList">
						<input class="communitiesListOption" type="radio" value="-10" name="communityId" required />
						<label>None</label><br>
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
									<th><span style="font-weight: bold;" id="sortGroupsByName">Group name</span></th>
									<th><span style="font-weight: bold;" id="sortGroupsByPI">PI</span></th>
									<th><span style="font-weight: bold;" id="sortGroupsBySize">Group size</span></th>
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
							<spring:message code="system.createAccountForm.selectPICommunity.label" /> </label></td>
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
							<label>None</label>
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
				<input type="checkbox" id="checkCreateRepeatUserAccount" value="Create & Repeat" /> Check to repeat
			</td>
			<td align="right" width="60px">
				<button class="btn btn-primary" type="submit" id="submitCreateUserAccount">
					<span class="ui-button-text">
						Create
					</span>
				</button>
			</td>
		</tr>
	</table>

</form>