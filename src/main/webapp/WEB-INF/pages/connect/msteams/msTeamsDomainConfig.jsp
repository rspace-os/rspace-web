<%@ include file="/common/taglibs.jsp"%>

<head>
    <title>Initial Tab configuration screen for MS Teams - Domain selection</title>
    
    <link href="/styles/pages/connect/msteams/msTeamsThemes.css" rel="stylesheet" />
    <link href="/styles/pages/connect/msteams/msTeamsDomainConfig.css" rel="stylesheet" />

    <script src="/scripts/global.js"></script>
    <script src="/scripts/bower_components/bootstrap/dist/js/bootstrap.js"></script>

    <script src="/scripts/pages/connect/msteams/MicrosoftTeams_sdk_v1.0.min.js"></script>
    <script src="/scripts/pages/connect/msteams/msTeamsInitialiser.js"></script>
    <script src="/scripts/pages/connect/msteams/msTeamsDomainConfig.js"></script>

</head>

<div class="bootstrap-custom-flat">
  <div id="msTeamsDomainConfigDiv" class="container">

    <h5>Connect to your RSpace server:</h5>

    <form>
        <div class="form-group">
            <input type="text" id="serverUrlInput" name="serverUrl"></input>
            <div id="changeServerBtn" class="btn btn-primary">Connect</div>
        </div>
    </form>

    <h5>Don't have an account yet?</h5>
    <p>Sign up for free on <a rel="noreferrer" href="https://community.researchspace.com/signup" target="_blank">https://community.researchspace.com</a></p>

  </div>
</div>

<hr id="footerHr"/>
