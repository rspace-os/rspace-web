<%@ include file="/common/taglibs.jsp"%>
<c:choose>
	<c:when test="${field['class'].name eq 'com.researchspace.model.field.NumberFieldForm'}">
		<%@ include file="numberFieldForm.jsp"%>
	</c:when>
	<c:when test="${field['class'].name eq 'com.researchspace.model.field.StringFieldForm'}">
		<%@ include file="stringFieldForm.jsp"%>
	</c:when>
	<c:when test="${field['class'].name eq 'com.researchspace.model.field.TextFieldForm'}">
		<%@ include file="textFieldForm.jsp"%>
	</c:when>
	<c:when test="${field['class'].name eq 'com.researchspace.model.field.RadioFieldForm'}">
		<%@ include file="radioFieldForm.jsp"%>
	</c:when>
	<c:when test="${field['class'].name eq 'com.researchspace.model.field.ChoiceFieldForm'}">
		<%@ include file="choiceFieldForm.jsp"%>
	</c:when>
	<c:when test="${field['class'].name eq 'com.researchspace.model.field.DateFieldForm'}">
		<%@ include file="dateFieldForm.jsp"%>
	</c:when>
	<c:when test="${field['class'].name eq 'com.researchspace.model.field.TimeFieldForm'}">
		<%@ include file="timeFieldForm.jsp"%>
	</c:when>
</c:choose>
