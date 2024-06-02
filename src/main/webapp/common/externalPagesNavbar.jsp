<%@ include file="/common/taglibs.jsp"%>

<c:set var="isLoggedAsNonAnonymousUser" value="${sessionScope.userInfo.username != null}"/>

<nav class="rs-navbar">
    <ul class="rs-navbar__list rs-navbar__list--pull-right">
      <li class="rs-navbar__item  rs-navbar__item--pull-left" id="branding">
          <a href="
            <c:if test="${isLoggedAsNonAnonymousUser}">
              ${applicationScope['RS_DEPLOY_PROPS']['bannerImageLink']}
            </c:if>
            <c:if test="${!isLoggedAsNonAnonymousUser}">
              ${applicationScope['RS_DEPLOY_PROPS']['bannerImageLoggedOutLink']}
            </c:if>
            ">
        <rst:hasDeploymentProperty name="cloud" value="true">
               <img src="<c:url value='/images/mainLogoCloudN2.png'/>" 
                    alt="RSpace" class="rs-navbar__icon" />
         </rst:hasDeploymentProperty>
         <rst:hasDeploymentProperty name="cloud" value="false">
               <img src="<c:url value='/public/banner'/>" 
                    alt="RSpace" class="rs-navbar__icon"
                    data-src="${applicationScope['RS_DEPLOY_PROPS']['bannerImageName']}" />
         </rst:hasDeploymentProperty>
        </a>
      </li>
      
      <c:if test="${fn:contains(pageContext.request.servletPath,'/signup')}">
      <c:if test="${fn:length(applicationScope['RS_DEPLOY_PROPS']['customSignupContent']) > 0}">
        <li class="rs-navbar__item rs-navbar__item--pull-left">
          <p class="rs-navbar__item rs-navbar__tab" style="border: 1px solid black">
           ${applicationScope['RS_DEPLOY_PROPS']['customSignupContent']}
          <p/>
        </li>
      </c:if>
      </c:if>

      <c:if test="${!isLoggedAsNonAnonymousUser}">
        <rst:hasDeploymentProperty name="userSignup" value="true">
        <rst:hasDeploymentProperty name="ldapAuthenticationEnabled" value="false">
          <li class="rs-navbar__item
            <c:if test="${fn:contains(pageContext.request.servletPath,'/signup')}"> rs-navbar__item--active </c:if>
          ">
            <a href="<c:url value='/signup' />" class="rs-navbar__tab">Sign up</a>
          </li>
        </rst:hasDeploymentProperty>
        </rst:hasDeploymentProperty>
      </c:if>
      
      <c:if test="${isLoggedAsNonAnonymousUser}">
        <li class="rs-navbar__item">
          <a href="/workspace" class="rs-navbar__tab">Workspace</a>
        </li>
      </c:if>
      
      <li class="rs-navbar__item
            <c:if test="${fn:contains(pageContext.request.servletPath,'published')}"> rs-navbar__item--active </c:if>
        " id="publishedDocuments">
        <a href="<c:url value='/public/publishedView/publishedDocuments' />" class="rs-navbar__tab">Published</a>
      </li>
      
      <c:if test="${!isLoggedAsNonAnonymousUser}">
        <li class="rs-navbar__item 
          <c:if test="${fn:contains(pageContext.request.servletPath,'/login') 
            || fn:contains(pageContext.request.servletPath,'/adminLogin')}"> rs-navbar__item--active </c:if>
          ">
          <a href="<c:url value='/login' />" class="rs-navbar__tab">Log in</a>
        </li>
      </c:if>
    </ul>

</nav>
