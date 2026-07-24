<%@ taglib prefix="c" uri="jakarta.tags.core"%>
<%@ taglib prefix="rst" uri="http://researchspace.com/tags"%>
<%@ taglib prefix="axt" tagdir="/WEB-INF/tags"%>

<%@ attribute name="parentId" required="true" type="java.lang.Long"%>

<script src="<rst:assetUrl value='/scripts/tags/importFromProtocolsIo.js'/>"></script>

<style>
.seamless {
    padding: 0px;
    border: 0px none transparent;
}
</style>

<div id="protocolsIoChooserDlg" style="display: none; overflow: inherit">
	<iframe id="protocolsIoChooserDlgIframe" data-parentid="${parentId}" style="width:100%;height:100%;" class="seamless" frameborder="0"></iframe>
</div>
