<%@ attribute name="record" required="true"
	type="com.researchspace.model.record.BaseRecord"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%--  the shared icon for records --%>
<c:choose>
	<c:when test="${record.structuredDocument or record.notebook}">
	 	<c:choose>
			<c:when
				test="${record.sharedStatus eq 'SHARED' and not record.selectedForOfflineWork}">
				<img class="sharedStatusImg" src="/images/documentStatusShared.png" width=32 height=13
					alt="Shared" title="Shared">
			</c:when>
			<c:when
				test="${record.selectedForOfflineWork and record.sharedStatus eq 'UNSHARED'}">
				<img class="sharedStatusImg" src="/images/documentStatusOffline.png" width=32 height=13
					alt="Offline" title="Offline">
			</c:when>
			<c:when
				test="${record.selectedForOfflineWork and record.sharedStatus eq 'SHARED'}">
				<img class="sharedStatusImg" src="/images/documentStatusSharedOffline.png" width=32
					height=13 alt="Shared & Offline" title="Shared & Offline">
			</c:when>
			<c:otherwise>
				<img class="sharedStatusImg" src="/images/documentStatusDefault.png" width=32 height=13
					alt="Not Shared" title="Not Shared">
			</c:otherwise>
		</c:choose> 
	</c:when>
	<%--  WE can't share folders or system documents --%>
	<c:otherwise>
		<img class="sharedStatusImg" src="/images/documentStatusNotShareable.png" width=32 height=13 alt="Not Shareable" title="Not Shareable">
	</c:otherwise>
</c:choose>