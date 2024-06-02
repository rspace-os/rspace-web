<%@ include file="/common/taglibs.jsp"%>

<script src="/scripts/pages/rspace/selfDeclarePi.js"></script>

<hr/>

<div style="font-size:1.5em; color: #444; margin-bottom: 10px;">
  Manage PI Role
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
      You are a regular user, without PI role.
    </span>
    <div class="pull-right" style="text-align:right;">
      <a href="#" class="profileEditButton ${user.allowedPiRole ? "" : "disabled"}" id="promoteToPiButton">
        Add PI role
      </a>
    </div>
  </c:if>

  <c:if test="${user.PI}">
    <span class="piRoleDescription">
      You have PI role. 
    </span>
    <div class="pull-right" style="text-align:right;">
      <a href="#" class="profileEditButton" id="demoteFromPiButton">
        Remove PI role
      </a>
    </div>
  </c:if>
  
  <c:if test="${user.allowedPiRole}">
    <div style="width: 100%">
      You can manage your PI role.
    </div>
  </c:if> 
    
    
  <div id="selfDeclarePi_institutionName" style="display:none">${applicationScope['RS_DEPLOY_PROPS']['customerNameShort']}</div>
  
</div>
