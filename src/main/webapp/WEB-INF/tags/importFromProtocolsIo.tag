<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags"%>

<script src="<c:url value='/scripts/tags/importFromProtocolsIo.js'/>"></script>

<style>
.seamless {
    padding: 0px;
    border: 0px none transparent;
}
</style>

<div id="protocolsIoChooserDlg" style="display: none">
	<iframe id="protocolsIoChooserDlgIframe" style="width:100%;height:1400px;" class="seamless" scrolling="no" frameborder="0"></iframe>
</div>
