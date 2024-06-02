<title><spring:message code="apps.title"/> </title>
<head>
    <meta name="heading" content="Apps" />

    <%-- styles --%>
    <link rel="stylesheet" href="<c:url value='/scripts/bootstrap-namespace/bootstrap-namespace.min.css'/>" />
    <link rel="stylesheet" href="<c:url value='/styles/pages/apps/apps.css'/>" />
    <link rel="stylesheet" href="<c:url value='/styles/pages/apps/apps-items.css'/>" />

</head>
<h1>H1: The quick brown fox jumps over the lazy dog.</h1>
<h2>H2: The quick brown fox jumps over the lazy dog.</h2>
<h3>H3: The quick brown fox jumps over the lazy dog.</h3>
<h4>H4: The quick brown fox jumps over the lazy dog.</h4>
<h5>H5: The quick brown fox jumps over the lazy dog.</h5>
<h6>H6: The quick brown fox jumps over the lazy dog.</h6>
<p>P: The quick brown fox jumps over the lazy dog.</p>

<div style="width:20%; height:100px; display:inline-block">
  <div style="background-color:#00aeef; width:100%; height: 50px;">
  </div>
  <p>Hex: #00aeef</p>
  <p>RGB: rgb(0, 174, 239) </p>
</div>

<div style="width:20%; height:100px; display:inline-block">
  <div style="background-color:#ed1165; width:100%; height: 50px;">
  </div>
  <p>Hex: #00aeef</p>
  <p>RGB: rgb(237,17,101) </p>
</div>

<div style="width:20%; height:100px; display:inline-block">
  <div style="background-color:#d8df20; width:100%; height: 50px;">
  </div>
  <p>Hex: #00aeef</p>
  <p>RGB: rgb(216,223,32)</p>
</div>

<div style="width:20%; height:100px; display:inline-block">
  <div style="background-color:#575c70; width:100%; height: 50px;">
  </div>
  <p>Hex: #575c70</p>
  <p>RGB: rgb(87,92,112)</p>
</div>


<div>
  <button>Button</button>
  <button Disabled>Disabled</button>
</div>

<div class="bootstrap-custom-flat">
  <div id="toolbar" class="navbar navbar-inverse">
    Toolbar
  </div>
</div>

<div id="notebook">
<div id="journalPage" style="display: block;">
  <button id="prevEntryButton_mobile" class="bootstrap-custom-flat" title="Previous entry" style="top: 235px;">
    <span class="glyphicon glyphicon-chevron-left"></span>
  </button>
</div>
</div>

<input type="text">
<input type="textarea">
<input type="number">
<input type="date">

<div class="folderShare">

  <div class="groupSelectContainer">
    <div class="radioContainer">
      <input type="radio" id="radio1" class="shareRadio" name="" value="">
    </div>
    <div class="labelContainer">
      <label for="radio1">Radio label</label>
    </div>
  </div>

  <div class="groupSelectContainer active">
    <div class="radioContainer">
      <input type="radio" id="radio1" class="shareRadio" name="" value="">
    </div>
    <div class="labelContainer">
      <label for="radio1">Radio label active</label>
    </div>
  </div>
</div>

<script src="<c:url value='/scripts/bower_components/requirejs/require.js'/>" 
        data-main="<c:url value='/scripts/pages/apps/apps-main.js'/>"></script>