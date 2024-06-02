<%@ include file="/common/taglibs.jsp"%>
<%--ajax-loaded table content for lists of communities --%>

<div id="labGroupForm">
<form id="moveCommunityForm">
<div class="topGreyBar"> <spring:message code="community.choose.label"/>
  <a id="moveSubmit" class="systemButton systemGoButton" href="#">Go</a>
  <a class="cancel systemButton systemCancelButton" id="moveCancel" href="#">Cancel</a>
  <input  id="groupIds" type="hidden" name="ids" value=""/>
</div>
<div id="communityList" class="communityViewInnerList">
  <table cellspacing="0" >
    <c:forEach items="${communities.results}" var="comm">
      <tr class="commRow table_listRow">
        <td class="table_listCell" style="text-align:center;" width="5%"><input  value="${comm.id}" name="to" type="radio" ></td>
        <td class="table_listCell"><a  class='cmmLink' href="<c:url value='/system/community/${comm.id}'/>">${comm.displayName}</a>
        <td class="table_listCell">${comm.uniqueName}</td>
      </tr>
    </c:forEach>
  </table>
</div>
</form>
</div>
