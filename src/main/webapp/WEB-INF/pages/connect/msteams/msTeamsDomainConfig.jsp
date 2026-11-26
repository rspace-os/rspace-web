<%@ include file="/common/taglibs.jsp"%>

<head>
    <title><spring:message code="connect.msteams.domainConfig.title"/></title>
    
    <axt:stylesheet href="/styles/pages/connect/msteams/msTeamsThemes.css"/>
    <axt:stylesheet href="/styles/pages/connect/msteams/msTeamsDomainConfig.css"/>

    <script src="<rst:assetUrl value='/scripts/global.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/bower_components/bootstrap/dist/js/bootstrap.js'/>"></script>

    <script src="<rst:assetUrl value='/scripts/pages/connect/msteams/MicrosoftTeams_sdk_v1.0.min.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/connect/msteams/msTeamsInitialiser.js'/>"></script>
    <script src="<rst:assetUrl value='/scripts/pages/connect/msteams/msTeamsDomainConfig.js'/>"></script>

</head>

<div class="bootstrap-custom-flat">
  <div id="msTeamsDomainConfigDiv" class="container">

    <h5><spring:message code="connect.msteams.domainConfig.connectHeading"/></h5>

    <form>
        <div class="form-group">
            <input type="text" id="serverUrlInput" name="serverUrl"></input>
            <div id="changeServerBtn" class="btn btn-primary"><spring:message code="apps:actions.connect"/></div>
        </div>
    </form>

    <h5><spring:message code="connect.msteams.domainConfig.noAccountHeading"/></h5>
    <spring:message code="connect.msteams.domainConfig.signupLinkText" var="msTeamsSignupLinkText"/>
    <p><spring:message code="connect.msteams.domainConfig.signupPrompt">
      <spring:argument value='<a rel="noreferrer" href="https://community.researchspace.com/signup" target="_blank">${msTeamsSignupLinkText}</a>'/>
    </spring:message></p>

  </div>
</div>

<hr id="footerHr"/>
