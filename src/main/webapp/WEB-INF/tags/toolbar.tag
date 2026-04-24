<%-- 
	Builds a toolbar with login button and enbedded search
--%>
<%@ attribute name="hideLogout" required="false" %>
<%@ attribute name="hideSearch" required="false" %>
<%@ attribute name="menu" fragment="true" %>

<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>

<div class="bootstrap-custom-flat">
  <div id="toolbar" class="navbar navbar-inverse">
  	<jsp:invoke fragment="menu"/>
  </div>
</div>