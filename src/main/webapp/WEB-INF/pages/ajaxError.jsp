
<%@ include file="/common/taglibs.jsp"%>
<%-- A simple error format for returning from an Ajax call.--%>
<div id="ajaxError">
	<spring:message code="errors.ajaxFragment.notice"/> <br />
	<p />
	<span id="ajaxErrorMsg">${exceptionMessage}</span>
	<p id="ajaxErrorIdMsg">
		<span><spring:message code="errors.ajaxFragment.refLabel" arguments="${errorId}"/></span>
	</p>
	<%-- show contents of error list, which may not be set. --%>
	<c:if test="${not empty errors}">
		<c:forEach items="${errors.errorMessages}" var="error">
 			${error}
 		<p />
		</c:forEach>
	</c:if>
</div>