<%@ taglib uri="http://researchspace.com/tags" prefix="rst" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<div class="biggerLogoDiv" style="text-align: center">
    <rst:hasDeploymentProperty name="cloud" value="true">
    	<img src="<c:url value='/images/biggerLogoCloudN.png'/>">
    </rst:hasDeploymentProperty>
    <rst:hasDeploymentProperty name="cloud" value="false">
    	<img src="<c:url value='/images/biggerLogoEnterpriseN.png'/>">
    </rst:hasDeploymentProperty>
</div>