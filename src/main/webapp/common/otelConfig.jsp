<%@ include file="/common/taglibs.jsp"%>
<c:if test="${applicationScope['RS_DEPLOY_PROPS']['otelWebEnabled'] eq 'true'}">
  <meta
    name="rs-otel-web"
    content="true"
    data-trace-sampling-ratio="${applicationScope['RS_DEPLOY_PROPS']['otelWebTraceSamplingRatio']}"
  />
</c:if>
