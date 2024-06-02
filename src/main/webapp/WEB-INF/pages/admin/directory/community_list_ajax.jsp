<%@ include file="/common/taglibs.jsp"%>
<h2 id="directoryListTitle" style="display: none;">
	<spring:message code="community.communities.title"/>
</h2>
<input type="hidden" id="noOfRows" value="${fn:length(communities.results)}">
<div class="panel">
	<table class="table table-striped table-hover mainTable noCheckboxes">
		<thead>
			<tr>
				<th style="width:25%">
					<a class="orderBy" id="orderByName" href="/directory/ajax/communitylist?orderBy=displayName&sortOrder=ASC&resultsPerPage=${pgCrit.resultsPerPage}">
						<spring:message code="table.name.header"/>
					</a>
				</th>
				<th style="width:35%">
					<spring:message code="table.admins.header"/>
				</th>
				<th style="width:40%">
					<spring:message code="table.about.header"/>
				</th>
			</tr>
		</thead>

		<tbody>
			<c:forEach items="${communities.results}" var="community">
				<tr>
					<td>
						<a href="directory/community/${community.id}">${community.displayName}</a>
					</td>
					<td>
						<c:forEach items="${community.admins}" var="admin">
              <c:choose>
                <c:when test="${admin.privateProfile and applicationScope['RS_DEPLOY_PROPS']['profileHidingEnabled'] and not subject.isConnectedToUser(admin)}">
                    <spring:message code="unknown.user.label" />
                </c:when>
                <c:otherwise>
                  <a href="/userform?userId=${admin.id}">${admin.fullName}</a>
                </c:otherwise>
              </c:choose>
						</c:forEach>
					</td>
					<td>
						${community.profileText}
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