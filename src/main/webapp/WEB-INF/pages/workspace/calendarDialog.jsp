<%@ include file="/common/taglibs.jsp"%>


<%-- Dialog for creating request --%>
<div id="createCalendarEntryDlg" style="display: none">
    <table>
        <tbody>
            <%-- Event Title --%>
            <tr>
                <td>
                    <label for="cd_event_title">
                        <fmt:message key="dialogs.createCalendarEntry.eventTitle.title"/>
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
                        <fmt:message key="dialogs.createCalendarEntry.eventStart.title"/>
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
                        <fmt:message key="dialogs.createCalendarEntry.eventEnd.title"/>
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
                        <fmt:message key="dialogs.createCalendarEntry.description.title"/>
                    </label>
                </td>
                <td>
                    <textarea id="cd_description" name="cd_description" class="form-control" placeholder="an optional description for this event" rows="5"></textarea>
                </td>
            </tr>

            <%-- Repeat event --%>
            <tr>
                <td>
                    <label for="cd_repeat_event">
                        <fmt:message key="dialogs.createCalendarEntry.repeatEvent.title"/>
                    </label>
                </td>
                <td>
                    <input id="cd_repeat_event" name="repeat_event" type="checkbox"></input>
                </td>
            </tr>

            <%-- Repeat event frequency --%>
            <tr id="cd_repeat_event_frequency">
                <td>
                    <label for="cd_frequency_select">Frequency</label>
                </td>
                <td>
                    <select id="cd_frequency_select" class="form-control">
                        <option value="DAILY">Daily</option>
                        <option value="WEEKLY">Weekly</option>
                        <option value="MONTHLY">Monthly</option>
                        <option value="YEARLY">Yearly</option>
                    </select>
                </td>
            </tr>

            <%-- Repeat event number of times --%>
            <tr id="cd_repeat_event_times">
                <td>
                    <label for="cd_repeat_n_times">Repeat for</label>
                </td>
                <td>
                    <input 
                        type="number" 
                        id="cd_repeat_n_times" 
                        value="1" 
                        style="width: 50%; display: inline !important;" 
                        class="form-control"
                        aria-label="How many times to repeat event"
                    />
                    <span>times</span>
                </td>
            </tr>


            <%-- Share event --%>
            <%--
            <tr>
                <td>
                    <label for="cd_share_event">
                        <fmt:message key="dialogs.createCalendarEntry.shareEvent.title"/>
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
                        <fmt:message key="dialogs.createCalendarEntry.to.title"/>
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
                        <fmt:message key="dialogs.createCalendarEntry.messageText.title"/>
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
                        <fmt:message key="dialogs.createCalendarEntry.includeLinkCheckbox.title"/>
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
                    <div>Attached resources:</div>
                    <ul id="cd_current_files" style="padding: 0;">
                        <li style="list-style: none;">None</li>
                    </ul>
                    <button type="button" id="cd_add_file">Add</button>
                    <button type="button" id="cd_clear">Clear</button>
                </td>
            </tr>
        </tbody>
    </table>
</div>

<%-- Dialog for attaching files --%>
<div id="attachFileToCalendarEntryDlg" style="display: none">
    <div class="sortingSettings" style="padding: 5px;">
        Order by:
        <select class="orderBy" aria-label="Order by">
            <option value="name">Name</option>
            <option value="creationdate">Creation Date</option>
            <option value="modificationdate">Last Modified</option>
        </select>
        <select class="sortOrder" aria-label="Sort order">
            <option value="ASC">Ascending</option>
            <option value="DESC">Descending</option>
        </select>
    </div>
    <div id="file_tree_frame">
        <h3>Workspace</h3>
        <div id="calendar_file_tree" style="height: 320px; width: 100%; background-color: white; overflow: auto;"></div>
    </div>
    <div id="linkData">
        <fieldset id="linksFieldset" class="links">
            <div>
                <label id='label' for="selectedNode">selected record</label> <input type="text" id='selectedNode' disabled="disabled" />
            </div>
        </fieldset>
    </div>
</div>
