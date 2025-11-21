<%@ include file="/common/taglibs.jsp"%>
<head>
<title>You are connected to RaID</title>
</head>

<div id="raidAuthorizationSuccess" class="bootstrap-custom-flat">
	Success - RSpace was authorized to access RaID
	<p class="font-weight-bold">You can close this window now or configure <a href="/apps"> more Apps</a>.</p>
</div>

<%-- This script is an addition to make the new apps page backwards compatible. --%>
<script>
    window.addEventListener("load", () => {
        const channel = new BroadcastChannel('rspace.apps.raid.connection');
        channel.postMessage({
            type: 'RAID_CONNECTED',
            alias: "${serverAlias}"
        })
        window.close();
    });
</script>

