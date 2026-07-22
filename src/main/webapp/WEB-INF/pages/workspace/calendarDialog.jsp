<%@ include file="/common/taglibs.jsp"%>


<%-- Dialog for creating request --%>
<div id="createCalendarEntryDlg" style="display: none">
    <table>
        <tbody>
            <%-- Event Title --%>
            <tr>
                <td>
                    <label for="cd_event_title">
                        <spring:message code="dialogs.createCalendarEntry.eventTitle.title"/>
                    </label>
                </td>
                <td>
                    <input id="cd_event_title" name="event_title" class="form-control"></input>
                </td>
            </tr>

            <%-- Event start --%>
            <tr>
                <td>
                    <label for="cd_event_start">
                        <spring:message code="dialogs.createCalendarEntry.eventStart.title"/>
                    </label>
                </td>
                <td>
                    <input id="cd_event_start" name="event_start" class="date datepickerz-index form-control"></input>
                </td>
            </tr>

            <%-- Event end --%>
            <tr>
                <td>
                    <label for="cd_event_end">
                        <spring:message code="dialogs.createCalendarEntry.eventEnd.title"/>
                    </label>
                </td>
                <td>
                    <input id="cd_event_end" name="event_end" class="date datepickerz-index form-control"></input>
                </td>
            </tr>

            <%-- Event description --%>
            <tr>
                <td>
                    <label for="cd_description">
                        <spring:message code="dialogs.createCalendarEntry.description.title"/>
                    </label>
                </td>
                <td>
                    <textarea id="cd_description" name="cd_description" class="form-control" placeholder="<spring:message code='dialogs.createCalendarEntry.description.placeholder'/>" rows="5"></textarea>
                </td>
            </tr>

            <%-- Repeat event --%>
            <tr>
                <td>
                    <label for="cd_repeat_event">
                        <spring:message code="dialogs.createCalendarEntry.repeatEvent.title"/>
                    </label>
                </td>
                <td>
                    <input id="cd_repeat_event" name="repeat_event" type="checkbox"></input>
                </td>
            </tr>

            <%-- Repeat event frequency --%>
            <tr id="cd_repeat_event_frequency">
                <td>
                    <label for="cd_frequency_select"><spring:message code="dialogs.createCalendarEntry.frequency.label"/></label>
                </td>
                <td>
                    <select id="cd_frequency_select" class="form-control">
                        <option value="DAILY"><spring:message code="dialogs.createCalendarEntry.frequency.daily"/></option>
                        <option value="WEEKLY"><spring:message code="dialogs.createCalendarEntry.frequency.weekly"/></option>
                        <option value="MONTHLY"><spring:message code="dialogs.createCalendarEntry.frequency.monthly"/></option>
                        <option value="YEARLY"><spring:message code="dialogs.createCalendarEntry.frequency.yearly"/></option>
                    </select>
                </td>
            </tr>

            <%-- Repeat event number of times --%>
            <tr id="cd_repeat_event_times">
                <td>
                    <label for="cd_repeat_n_times"><spring:message code="dialogs.createCalendarEntry.repeatFor.label"/></label>
                </td>
                <td>
                    <input
                        type="number"
                        id="cd_repeat_n_times"
                        value="1"
                        style="width: 50%; display: inline !important;"
                        class="form-control"
                        aria-label="<spring:message code='dialogs.createCalendarEntry.repeatFor.ariaLabel'/>"
                    />
                    <span><spring:message code="dialogs.createCalendarEntry.repeatFor.unit"/></span>
                </td>
            </tr>


            <%-- Share event --%>
            <%--
            <tr>
                <td>
                    <label for="cd_share_event">
                        <spring:message code="dialogs.createCalendarEntry.shareEvent.title"/>
                    </label>
                </td>
                <td>
                    <input id="cd_share_event" name="share_event" type="checkbox" aria-labelledby="cd_share_event"></input>
                </td>
            </tr>
            --%>

            <%-- Share event with --%>
            <%--
            <tr id="cd_share_event_to">
                <td>
                    <label for="cd_share_to">
                        <spring:message code="dialogs.createCalendarEntry.to.title"/>
                    </label>
                </td>
                <td>
                    <textarea id="cd_share_to" name="share_to" aria-labelledby="cd_share_to"></textarea>
                </td>
            </tr>
            --%>

            <%-- Message text --%>
            <%--
            <tr id="cd_message_text">
                <td>
                    <label for="cd_message_text_field">
                        <spring:message code="dialogs.createCalendarEntry.messageText.title"/>
                    </label>
                </td>
                <td>
                    <textarea id="cd_message_text_field" name="cd_message_text_field"></textarea>
                </td>
            </tr>
            --%>

            <%-- Include links to resources --%>
            <tr>
                <td>
                    <label for="cd_include_links_to_resources">
                        <spring:message code="dialogs.createCalendarEntry.includeLinkCheckbox.title"/>
                    </label>
                </td>
                <td>
                    <input id="cd_include_links_to_resources" name="include_links_to_resources" type="checkbox"></input>
                </td>
            </tr>

            <%-- Treeview to select resources --%>
            <tr id="cd_tree_view">
                <td></td>
                <td>
                    <div><spring:message code="dialogs.createCalendarEntry.attachedResources.label"/></div>
                    <ul id="cd_current_files" style="padding: 0;">
                        <li style="list-style: none;"><spring:message code="common:actions.none"/></li>
                    </ul>
                    <button type="button" id="cd_add_file"><spring:message code="common:actions.add"/></button>
                    <button type="button" id="cd_clear"><spring:message code="common:actions.clear"/></button>
                </td>
            </tr>
        </tbody>
    </table>
</div>

<%-- Dialog for attaching files --%>
<div id="attachFileToCalendarEntryDlg" style="display: none">
    <div class="sortingSettings" style="padding: 5px;">
        <spring:message code="workspace.sort.label"/>
        <select class="orderBy" aria-label="<spring:message code='workspace.sort.ariaLabel'/>">
            <option value="name"><spring:message code="workspace.sort.byName"/></option>
            <option value="creationdate"><spring:message code="workspace.sort.byCreationDate"/></option>
            <option value="modificationdate"><spring:message code="workspace.sort.byLastModified"/></option>
        </select>
        <select class="sortOrder" aria-label="<spring:message code='workspace.sort.sortOrderAriaLabel'/>">
            <option value="ASC"><spring:message code="workspace.sort.ascending"/></option>
            <option value="DESC"><spring:message code="workspace.sort.descending"/></option>
        </select>
    </div>
    <div id="file_tree_frame">
        <h3><spring:message code="dialogs.createCalendarEntry.treeHeading"/></h3>
        <div id="calendar_file_tree" style="height: 320px; width: 100%; background-color: white; overflow: auto;"></div>
    </div>
    <div id="linkData">
        <fieldset id="linksFieldset" class="links">
            <div>
                <label id='label' for="selectedNode"><spring:message code="dialogs.createCalendarEntry.selectedRecordLabel"/></label> <input type="text" id='selectedNode' disabled="disabled" />
            </div>
        </fieldset>
    </div>
</div>
