<%@ include file="/common/taglibs.jsp"%>
<%-- Table listing of groups for sysadmin pages  --%>
<table  class="systemList"  cellspacing="0" style="width:100% !important;margin-top:5px ">
<tr  class="table_headRow">
  <th width="5%">Options</th>
  <th width="15%" style="padding-left:8px;">
   <a class="orderBy" id="orderByName" href="/system/groups/ajax/list?orderBy=displayName&sortOrder=ASC&resultsPerPage=${pgCrit.resultsPerPage}">
     <spring:message code="table.name.header"/></a></th>
  <th width="5%">
   <a class="orderBy" id="orderBySize" href="/system/groups/ajax/list?orderBy=memberCount&sortOrder=ASC&resultsPerPage=${pgCrit.resultsPerPage}">
   Size </a> (enabled, disabled)</th>
  <th width="15%"><spring:message code="community.name"/></th>
  <th width="15%"><a class="orderBy" id="orderByPi" href="/system/groups/ajax/list?orderBy=owner.lastName&sortOrder=ASC&resultsPerPage=${pgCrit.resultsPerPage}">
    <spring:message code="groups.lead.label"/></a>
  </th>
  <th width=15%> <a class="orderBy" id="orderByUsage" href="/system/groups/ajax/list?orderBy=usage&sortOrder=DESC&resultsPerPage=${pgCrit.resultsPerPage}">File Usage (Mb)</a></th>
  <th width=15%>
  <a class="orderBy" id="orderByCreationDate" href="/system/groups/ajax/list?orderBy=creationDate&sortOrder=DESC&resultsPerPage=${pgCrit.resultsPerPage}"> <spring:message code="workspace.list.creationDate.header"/></a>
  </th>
    <th>
        <a class="orderBy" id="orderByType" href="/system/groups/ajax/list?orderBy=groupType&sortOrder=ASC&resultsPerPage=${pgCrit.resultsPerPage}"> <spring:message code="workspace.list.type.header"/></a>
    </th>
</tr>
<c:forEach items="${groupInfo}" var="grpInfo" >
	<tr class="table_listRow">
	    <td class="table_listCell action enabled"><input data-id="${grpInfo.group.id}" class="actionCbox" type="checkbox" name="groupAction"/></td>
	    <td class="table_listCell" style="text-align:left;padding-left:8px;"><a href="/groups/view/${grpInfo.group.id}">${grpInfo.group.displayName}</a></td>
		<td class="table_listCell" style="text-align:left;">
		     <span data-test-id="groupTotalSize"> ${grpInfo.group.size} </span>
		     (<span class="enabled" data-enabledMemberSize="${grpInfo.group.enabledMemberSize}" data-content="Enabled">${grpInfo.group.enabledMemberSize}</span>,
		    <span class="disabled" data-disabledMemberSize="${grpInfo.group.disabledMemberSize}">${grpInfo.group.disabledMemberSize}</span>)</td>
		<td class="table_listCell" style="text-align:left;"><a href="/system/community/${grpInfo.group.community.id}">${grpInfo.group.community.displayName}</a></td>
		<td class="table_listCell" style="text-align:left;">

		<a href="/userform?userId=${grpInfo.group.owner.id}"> ${grpInfo.group.owner.lastName}, ${grpInfo.group.owner.firstName} </a>

		</td>
 		<td class="table_listCell"> <fmt:formatNumber maxFractionDigits="1" >${grpInfo.fileUsage / 1000000} </fmt:formatNumber> </td>
 		<td class="table_listCell"> <rst:relDate input="${grpInfo.group.creationDate}" relativeForNDays="2"/></td>
        <td class="table_listCell" style="text-align:left;"> ${grpInfo.group.groupType.label}</td>
 	</tr>
</c:forEach>
</table>
<div class="tabularViewBottom bootstrap-custom-flat">
  <axt:paginate_new paginationList="${paginationList}"/>
</div>
