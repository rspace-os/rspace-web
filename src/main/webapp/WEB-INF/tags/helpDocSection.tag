<%@ taglib uri="http://researchspace.com/tags" prefix="rst"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="shiro" uri="http://shiro.apache.org/tags"%>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>
<%@ attribute name="urlTarget" required="true" type="java.lang.String" %>
<c:set var="url" value="http://www.researchspace.com"></c:set>
<table id="helpDocumentation" class="table" cellspacing="0">
	<tr>
		<th>
      <a href='<c:url value="${url}${urlTarget}" />' target='_blank'>
        <spring:message code="help.documentation.label"/>
      </a>
    </th>
	</tr>
	<tr>
		<td><spring:message code="help.byrole.msg"/></td>
	</tr>
</table>