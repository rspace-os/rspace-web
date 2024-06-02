<%@ include file="/common/taglibs.jsp"%>
<link href="<c:url value='/styles/preferences.css'/>" rel="stylesheet" />
<script src="<c:url value='/scripts/pages/rspace/preferences.js'/>"></script>

<head>
    <title><fmt:message key="userList.title"/></title>
    <meta name="heading" content="<fmt:message key='userList.heading'/>"/>
    <meta name="menu" content="AdminMenu"/>
</head>

<div style="font-size:1.5em; color: #444; margin-bottom: 10px;">
  <spring:message code="userProfile.message.title" />
</div>

<div class="col-xs-12">
  <form id ="messageSettingsForm" method='POST' action='/userform/ajax/messageSettings'>
  <div class="profileGreyBlocks">
  <div id="prefContainer">

  <fieldset id="notificationPrefs" class="profileGreyBlocks">
    <legend class="prefsGroupLabel"><spring:message code="userProfile.message.notify.label"/>:</legend>
    <c:forEach items="${preferences.prefs}" var="pref" >
      <c:if test="${(pref.preference.category eq 'MESSAGING') && (pref.numeric ne 'true')}">
      <input 
        class="cbox" 
        type="checkbox" 
        name="messageCheckboxes" 
        value="${pref.preference}"
        aria-label="${pref.preference.displayMessage}"
        ${pref.value == 'TRUE' or pref.value == 'true' ?'checked':'' }/
      > ${pref.preference.displayMessage} <br/>
      </c:if>
    </c:forEach>
  </fieldset>

  <fieldset id="broadcastPrefs" class="profileGreyBlocks" style="width:300px;">
    <legend class="prefsGroupLabel"><spring:message code="userProfile.message.delivery.label"/>:</legend>
    <c:forEach items="${preferences.prefs}" var="pref" >
      <c:if test="${(pref.preference.category eq 'MESSAGING_BROADCAST') && (pref.numeric ne 'true')}">
        <input 
          class="cbox" 
          type="checkbox" 
          name="messageCheckboxes" 
          value="${pref.preference}"
          aria-label="${pref.preference.displayMessage}"
          ${pref.value == 'TRUE' or pref.value == 'true' ?'checked':'' }
        /> ${pref.preference.displayMessage} <br/>
      </c:if>
    </c:forEach>
  </fieldset>

  <div class="col-xs-12">
    <div id="otherPreferences" class="profileGreyBlocks">
  
      <rst:hasDeploymentProperty name="profileHidingEnabled" value="true">
        <span class="prefsGroupLabel"><spring:message code="userProfile.otherPreferences.title"/>:</span><br/>
        <input class="cbox" type="checkbox" name="messageCheckboxes" value="PRIVATE_PROFILE"
            ${user.privateProfile ? 'checked': '' } />
        <spring:message code='userProfile.otherPreferences.hideProfile.label'/>
      </rst:hasDeploymentProperty>
    
      <div style="margin-top: 12px;">
        <button class="btn btn-primary" id="prefssubmit" name="submit" aria-disabled="false" role="button" type="submit">
          <span><spring:message code="userProfile.preferences.updateBtn.label"/></span>
        </button>
      </div>
    
    </div>
  </div>

  </div>
  </div>
  </form>
</div>
