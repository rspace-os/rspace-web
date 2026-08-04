<%@ include file="/common/taglibs.jsp"%>

<script src="<rst:assetUrl value='/scripts/pages/rspace/selfDeclarePi.js'/>"></script>

<hr/>

<div style="font-size:1.5em; color: #444; margin-bottom: 10px;">
  <spring:message code="userProfile.selfDeclarePi.heading"/>
</div>

<style>
  .piRoleDescription {
    flex-grow: 1;
  }
  
</style>

<div class="profileGreyBlocks" style="display:flex; flex-wrap: wrap">
  <script>
    var isUserAllowedPiRole = ${user.allowedPiRole};
    var isUserAPi = ${user.PI};
    var isUserAPiOfSomeGroup = ${user.primaryLabGroupWithPIRole != null};
  </script>
  
  <c:if test="${not user.PI}">
    <span class="piRoleDescription">
      <spring:message code="userProfile.selfDeclarePi.notPi"/>
    </span>
    <div class="pull-right" style="text-align:right;">
      <a href="#" class="profileEditButton ${user.allowedPiRole ? "" : "disabled"}" id="promoteToPiButton">
        <spring:message code="userProfile.selfDeclarePi.addPiRole"/>
      </a>
    </div>
  </c:if>

  <c:if test="${user.PI}">
    <span class="piRoleDescription">
      <spring:message code="userProfile.selfDeclarePi.isPi"/>
    </span>
    <div class="pull-right" style="text-align:right;">
      <a href="#" class="profileEditButton" id="demoteFromPiButton">
        <spring:message code="userProfile.selfDeclarePi.removePiRole"/>
      </a>
    </div>
  </c:if>

  <c:if test="${user.allowedPiRole}">
    <div style="width: 100%">
      <spring:message code="userProfile.selfDeclarePi.canManage"/>
    </div>
  </c:if>
    
    
  <div id="selfDeclarePi_institutionName" style="display:none">${applicationScope['RS_DEPLOY_PROPS']['customerNameShort']}</div>
  
</div>
