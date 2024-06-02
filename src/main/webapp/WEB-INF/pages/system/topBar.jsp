<%@ include file="/common/taglibs.jsp"%>

<div id="topBar" class="navbar navbar-inverse">
	<div class="container-fluid">
		<ul class="nav navbar-nav">
			<c:if test="${empty hasCommunity or hasCommunity eq 'true'}">
				<li>
					<a id="listUsersButton" href="/system"><spring:message code="system.usersList.button.label" /></a>
				</li>
				<li>
					<c:url value='/system/groups/list?resultsPerPage=20&orderBy=displayName&sortOrder=ASC' var="listgroups"/>
				</li>
				<li>
					<a id="listGroupButton" href="${listgroups}"><spring:message code="system.groupsList.button.label" /></a>
				</li>
				<li>
					<a id="listCommunityButton" href="/community/admin/list"><spring:message code="system.communityList.button.label" /></a>
				</li>
				<li class="divider"></li>
				<li>
					<a id="unpublishButton" href="/record/share/published/manage"><spring:message code="system.unpublish.button.label" /></a>
				</li>
				<li class="divider"></li>

				<li>
					<a id="createAccountLink" href="/system/createAccount"><spring:message code="system.createAccount.button.label" /></a>
				</li>
				<li>
					<a id="runAsLnk" class="reauthrequired" data-dlgId="runAsUserDlg" href="#"><spring:message code="system.operateAs.button.label" /></a>
				</li>
			</c:if>
			<shiro:hasRole name="ROLE_SYSADMIN">
				<li class="divider"></li>

				<li>
					<a id="performanceGraph" href="/monitoring" target="_blank">
						<spring:message code="system.performance.button.label" />
					</a>
				</li>
				<li>
					<a id="supportLink" href="/system/support"><spring:message code="system.support.button.label" /></a>
				</li>
				<li>
					<a id="configLink" href="/system/config"><spring:message code="system.config.button.label" /></a>
				</li>
			</shiro:hasRole>
		</ul>
	</div>
</div>

<%-- div for promote to PI user dialog --%>
<div id="runAsUserDlg" style="display: none">
	<div id="runAsUserDlgStatus"></div>
	<div id="runAsUserDlgContent"></div>
</div>

<axt:export/>
