<%@ include file="/common/taglibs.jsp"%>
<div>
  <ul class="jqueryFileTree" style="display: none;">
    <c:forEach items="${records}" var="record">
      <c:if test="${record.deleted ne 'true'}">
        <c:choose>
          <c:when test="${record.folder eq 'true'}"><!-- folder or notebook -->
            <li class="directory <c:if test="${record.notebook eq 'true'}">notebook</c:if> collapsed">
              <a href="#" rel="${record.id}/" data-id="${record.id}" data-globalid="${record.oid}" data-name="${record.name}"
                data-creationdate="${record.creationDateMillis}" data-modificationdate="${record.modificationDateMillis}" > ${record.name}</a>
            </li>
          </c:when>
          <c:otherwise><!-- is a record -->
            <c:choose>
              <c:when test="${record.structuredDocument eq 'true'}">
                <li class="file record">
                  <a href="#" rel="${record.id}_${record.name}" class="recordTreeLink" data-category="Structured Document"
                    data-id="${record.id}" data-globalid="${record.oid}" data-name="${record.name}"
                    data-creationdate="${record.creationDateMillis}" data-modificationdate="${record.modificationDateMillis}">${record.name}</a>
                </li>
              </c:when>
              <c:when test="${record.mediaRecord eq 'true'}">
                <li class="file ext_${record.extension}">
                  <a href="/workspace/getEcatMediaFile/${record.id}" target="_blank"
                    data-id="${record.id}" data-globalid="${record.oid}" data-name="${record.name}"
                    data-creationdate="${record.creationDateMillis}" data-modificationdate="${record.modificationDateMillis}">${record.name}</a>
                </li>
              </c:when>
               <c:when test="${record.snippet eq 'true'}">
                <li class="file">
                  <a href="/globalId/${record.oid}" target="_blank"
                    data-id="${record.id}" data-globalid="${record.oid}" data-name="${record.name}"
                    data-creationdate="${record.creationDateMillis}" data-modificationdate="${record.modificationDateMillis}">${record.name}</a>
                </li>
              </c:when>
              <c:otherwise>
                <li class="file">${record.name}</li>
              </c:otherwise>
            </c:choose>
          </c:otherwise>
        </c:choose>
      </c:if>
    </c:forEach>
  </ul>
  <div>
  <div id='clientUISettingsPref' data-settings="${clientUISettingsPref}"></div>
  </div>
</div>