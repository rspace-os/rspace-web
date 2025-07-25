import React from "react";
import {createRoot} from "react-dom/client";
import ExternalWorkflowInvocations
  from "@/eln/eln-external-workflows/ExternalWorkflowInvocations";

/**
 * ExternalWorkflows (eg Galaxy) represents remote computation. RSpace will display a data table
 * of external workflows that have used data attached to an RSpace Document and subsequently uploaded to a remote server
 * then used in remote computation.
 */

/**
 * invoked on page load of a notebook page by journal.js event dispatch
 */
window.addEventListener("extWorkFlows-init", function () {
  loadUIOnPageLoad(true);
});
/**
 * notebook pages and structured doc pages require different positioning for the Workflow Button
 * @param isForNotebookPage
 */
const loadUIOnPageLoad = (isForNotebookPage = false) => {
  [...document.getElementsByClassName("ext-workflows-textfield")].forEach(
      (wrapperDiv) => {
        const fieldId = wrapperDiv.getAttribute("data-field-id");
        const root = createRoot(wrapperDiv);
        root.render(
            <ExternalWorkflowInvocations isForNotebookPage={isForNotebookPage} fieldId = {fieldId}/>
        );
      }
  );
}
/**
 * invoked when Structured Doc page loads (but not when a Notebook page loads)
 */
loadUIOnPageLoad();


