<%@ include file="/common/taglibs.jsp"%>
<h2 id="directoryListTitle" style="display: none;">
  <spring:message code="userProfile.sectionLabel.labGroups"/>
</h2>
<input type="hidden" id="noOfRows" value="${fn:length(groups.results)}">
<div class="panel">
  <table class="table table-striped table-hover mainTable noCheckboxes">
    <thead>
      <tr>
        <th style="width:25%">
          <a class="orderBy" id="orderByName" href="/directory/ajax/grouplist?orderBy=displayName&sortOrder=ASC&resultsPerPage=${pgCrit.resultsPerPage}">
            <spring:message code="group.labgroup.name"/>&nbsp;<spring:message code="table.name.header"/>
          </a>
        </th>
        <th style="width:25%">
          <spring:message code="community.name"/>&nbsp;<spring:message code="table.name.header"/>
        </th>
        <th style="width:20%">
          <a class="orderBy" id="orderByPi" href="/directory/ajax/grouplist?orderBy=owner.lastName&sortOrder=ASC&resultsPerPage=${pgCrit.resultsPerPage}">
            <spring:message code="groups.pi.label"/>
          </a>
        </th>
        <th style="width:30%">
          <spring:message code="table.about.header"/>
        </th>
      </tr>
    </thead>
    <tbody>
      <c:forEach items="${groups.results}" var="group" >
      	<tr>
          <td>
            <a href="/groups/view/${group.id}">
              ${group.displayName}
            </a>
          </td>
      		<td>
            ${group.community.displayName}
          </td>
          <td>
            <c:choose>
              <c:when test="${group.owner.privateProfile and applicationScope['RS_DEPLOY_PROPS']['profileHidingEnabled'] and not subject.isConnectedToUser(group.owner)}">
                <spring:message code="unknown.user.label" />
              </c:when>
              <c:otherwise>
                <c:forEach items="${group.piusers}" var="pi">
		          <a href="/userform?userId=${pi.id}"> ${pi.fullName}  </a> 
		        </c:forEach> 
              </c:otherwise>
            </c:choose>
          </td>
      		<td>
            ${group.profileText}
          </td>
       	</tr>
      </c:forEach>
    </tbody>
  </table>
</div>

<div class="tabularViewBottom bootstrap-custom-flat">
  <axt:paginate_new paginationList="${paginationList}"/>
  <axt:numRecords/>
</div>