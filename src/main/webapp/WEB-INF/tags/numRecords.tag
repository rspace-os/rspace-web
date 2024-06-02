<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<c:set var="numRecordsOptions" value="${fn:split('10,15,30,50', ',')}"/>

<form class="form-horizontal numRecordsForm">
	<fieldset>
		<div class="form-group" style="margin: 0;">
			<label for="numberRecordsId" class="control-label">
				Items per page:
			</label>
				<select name="numberRecords" id="numberRecordsId" class="form-control">
					<c:forEach items="${numRecordsOptions}" var="link">
                      <c:choose>
                        <c:when test="${numberRecords == link}">
                            <option value="${link}" selected="selected">${link}</option>
                        </c:when>
                        <c:otherwise>
					       <option value="${link}">${link}</option>
                        </c:otherwise>
                      </c:choose>
					</c:forEach>
				</select>
			<button class="btn btn-primary" id="applyNumberRecords" style="display: none" title="Apply the Items per page setting">
				<span class="glyphicon glyphicon-ok"></span>
			</button>
		</div>
	</fieldset>
</form>

