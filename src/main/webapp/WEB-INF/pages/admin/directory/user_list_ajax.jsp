<%@ include file="/common/taglibs.jsp"%>
<h2 id="directoryListTitle" style="display: none;">
	<spring:message code="directory.users.title" />
</h2>
<input type="hidden" id="noOfRows" value="${fn:length(users.results)}">
<c:choose>
	<c:when test="${not empty users.results}">
	<div class="panel">
		<table class="table table-striped table-hover mainTable noCheckboxes">
			<thead>
				<tr>
					<th style="width:15%">
						<a class="orderBy" id="orderByName" href="${orderByLastNameLink.link}">
							<spring:message code="user.fullname.label" />
						</a>
					</th>
					<th style="width:10%">
						<a class="orderBy" id="orderByUserName" href="${orderByUsernameLink.link}">
							<spring:message code="user.username.label" />
						</a>
					</th>
					<rst:hasDeploymentProperty name="cloud" value="true">
					<th style="width:10%">
						<a class="orderBy" id="orderByAffiliation" href="${orderByAffiliationLink.link}">
							<spring:message code="user.affiliation.label" />
						</a>
					</th>
					</rst:hasDeploymentProperty>
					<th style="width:10%">
						<spring:message code="group.labgroup.name" />
					</th>
					<th style="width:15%">
						<a class="orderBy" id="orderByEmail" href="${orderByEmailLink.link}">
							<spring:message code="user.email.label" />
						</a>
					</th>
					<th style="width:25%">
						<spring:message code="table.about.header" />
					</th>
				</tr>
			</thead>
			<tbody>
				<c:forEach items="${users.results}" var="user">
				<tr>
					<td>
						<a href="/userform?userId=${user.userInfo.id}">
							${user.userInfo.fullNameSurnameFirst}
						</a>
					</td>
					<td>
						<a href="/userform?userId=${user.userInfo.id}">
							${user.userInfo.username}
						</a>
					</td>
					<rst:hasDeploymentProperty name="cloud" value="true">
					<td>
						${user.userInfo.affiliation}
					</td>
					</rst:hasDeploymentProperty>
					<td>
						<c:forEach items="${user.groups}" var="group">
              <c:if test="${!(group.privateProfile and applicationScope['RS_DEPLOY_PROPS']['profileHidingEnabled'] and not subject.isConnectedToGroup(group))}">
              	<a href="/groups/view/${group.id}"> ${group.displayName} </a>
              </c:if>
						</c:forEach>
					</td>
					<td style="word-wrap: break-word;">
						${user.userInfo.email}
					</td>
					<td>
						<c:if test="${not empty user.orcidId}">
							<a class="orcidIdLink" target="_blank" href="http://orcid.org/${user.orcidId}">
								<img class="orcidIdImg" src="/images/integrations/orcid-small.png" style="vertical-align: text-bottom;" />
								http://orcid.org/${user.orcidId}
							</a>
							<br />
						</c:if> 
						<c:if test="${not empty user.shortProfileText}">
		          ${user.shortProfileText} 
		          <a href="/userform?userId=${user.userInfo.id}"> 
		          	<spring:message code="button.more" />
		          </a>
						</c:if>
					</td>
				</tr>
				</c:forEach>
			</tbody>
		</table>
	</div>
	<div class="tabularViewBottom bootstrap-custom-flat">
		<axt:paginate_new paginationList="${paginationList}" />
		<axt:numRecords/>
	</div>
	</c:when>
	<c:otherwise>
	<div class="panel">
		<%--  no results --%>
		<rst:hasDeploymentProperty name="cloud" value="true">
			<%--  if new pageload in cloud, we don't get a listing anyway --%>
			<c:choose>
				<c:when test="${not empty pageReload}">
					<span class="directoryMsg searchMessage"> <spring:message code="directory.cloud.help.msg" /></span>
				</c:when>
				<%--  otherwise the search is retrieving no results --%>
				<c:otherwise>
					<span class="directoryMsg searchError"><spring:message code="directory.noresults.msg" arguments="users" /></span>
				</c:otherwise>
			</c:choose>
		</rst:hasDeploymentProperty>
		<rst:hasDeploymentProperty name="cloud" value="true" match="false">
			<span class="directoryMsg searchError">
				<spring:message code="directory.noresults.msg" arguments="users" />
			</span>
		</rst:hasDeploymentProperty>
	</div>
	</c:otherwise>
</c:choose>