<%@ include file="/common/taglibs.jsp"%>
<%--form loaded over ajax to select new RSpace admins for a community --%>
<div id="adminForm">
<form:form modelAttribute="community" method="POST" action="/community/admin/addAdmin">
<div class="topGreyBar">
<spring:message code="community.addAdmin.chooseLabel"/>
<a id="addAdminSubmit" class="systemButton systemGoButton" href="#"><spring:message code="community.actions.go"/></a>
<a class="cancel systemButton systemCancelButton" id="addAdminCancel" href="#"><spring:message code="common:actions.cancel"/></a>
</div>
<div id="adminsList" class="communityViewInnerList">
<form:checkboxes class="admincbox" delimiter="<br/>" items="${community.availableAdmins}" path="adminIds" 
itemLabel="fullName" itemValue="id"/>
<p/>
</div>
<form:hidden path="id"/>
</form:form>
</div>