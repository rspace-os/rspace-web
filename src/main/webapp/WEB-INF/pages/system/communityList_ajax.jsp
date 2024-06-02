<%@ include file="/common/taglibs.jsp"%>
<%--ajax-loaded table content for lists of communities --%>
<table cellspacing="0" class="systemList">
    <tr class="table_headRow">
      <th width="5%">Option</th>
      <th width="15%"><spring:message code="system.communityList.name.label"/></th>
      <th width="20%"><spring:message code="system.communityList.uniqueName.label"/></th>
      <th><spring:message code="system.communityList.admins.label"/></th>
      <th>Created</th>
     </tr>
     <c:forEach items="${communities.results}" var="comm">
      <tr class="commRow table_listRow">
        <td class="table_listCell" style="text-align:center;"><input id="comm_${comm.id}" data-name="${comm.uniqueName}" type="checkbox" class="commactionCbox"></td>
        <td class="table_listCell"><a class='cmmLink' href="<c:url value='/system/community/${comm.id}'/>">${comm.displayName}</a>
        <td class="table_listCell">${comm.uniqueName}</td>
        <td class="table_listCell">
          <rst:joinProperties property="fullNameAndEmail" collection="${comm.admins}"></rst:joinProperties>
        </td>
        <td class="table_listCell">${comm.creationDate}</td>
      </tr>
     </c:forEach>  
  </table>
  
<div class="tabularViewBottom bootstrap-custom-flat">
  <axt:paginate_new paginationList="${paginationList}"></axt:paginate_new> 
</div>