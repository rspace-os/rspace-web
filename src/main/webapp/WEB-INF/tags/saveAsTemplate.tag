<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ attribute name="structuredDocument" required="true" type="com.researchspace.model.record.StructuredDocument" %>

<script src="<c:url value='/scripts/tags/saveAsTemplate.js'/>"></script>

<style>
    #saveAsTemplateNameDiv {
        margin: 10px;
    }
    #saveAsTemplateFieldsDesc {
        margin: 10px;
    }
    #saveAsTemplateFieldsDiv {
        margin: 8px;
        max-height: 250px;
        overflow-y: auto;
    }
</style>

<div id="saveAsTemplateDlg" title="Template Dialog" style="display: none;">
    <div id="saveAsTemplateNameDiv">
        <label>
            Template Name <input id="template_name" type="text" value="${structuredDocument.name}_template" size="30" />
        </label>
    </div>
    
    <div id="saveAsTemplateFieldsDesc">
        Include contents from these fields:
    </div>
    <div id="saveAsTemplateFieldsDiv">
        <table>
            <c:forEach var="field" items="${structuredDocument.fields}">
                <tr><td><label for="template_${field.id}">${field.name}:</label></td><td>
                    <input id="template_${field.id}" type="checkbox" name="templates" checked></input></td>
                </tr>
            </c:forEach>
        </table>
    </div>
</div>
