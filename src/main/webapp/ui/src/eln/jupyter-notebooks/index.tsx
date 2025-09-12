import React from "react";
import {createRoot} from "react-dom/client";
import ExternalWorkflowInvocations
  from "@/eln/eln-external-workflows/ExternalWorkflowInvocations";
import { IpynbRenderer } from "react-ipynb-renderer";
import notebook from "./notebook.json"

/**
 * Renders a view of an attached jupyter notebook
 */

/**
 * invoked on page load of a notebook page by journal.js event dispatch
 */
window.addEventListener("jupyterNotebooks-init", function () {
  loadUIOnPageLoad(true);
});
/**
 * notebook pages and structured doc pages require different positioning for the Workflow Button
 * @param isForNotebookPage
 */
const loadUIOnPageLoad = (isForNotebookPage = false) => {
  [...document.getElementsByClassName("jupyter-notebooks-textfield")].forEach(
      (wrapperDiv) => {
        const fieldId = wrapperDiv.getAttribute("data-field-id");
        const attachedFiles = getAttachedFilesByParsingEmbeddedText("rtf_"+fieldId);
        if (isForNotebookPage) {
          // @ts-expect-error style does exist on HTMLDivElement
          wrapperDiv.style.position = "relative"
        }
        const root = createRoot(wrapperDiv);
        root.render(
            <IpynbRenderer ipynb={notebook} />
        );
      }
  );
}
/**
 * invoked when Structured Doc page loads (but not when a Notebook page loads)
 */
loadUIOnPageLoad();
/*
 * we are only interested in embedded jupyter links
 */
function getAttachedFilesByParsingEmbeddedText(selector) {
  const recordIDToHtml = [];
  $('<div>' + $('#' + selector).val()).find('.attachmentDiv').each(
      function (index) {
        const attachedRecordId = $(this).find('.attachmentInfoDiv').attr(
            'id').substring(18);
        const html = $(this).html();
        recordIDToHtml.push({id: attachedRecordId, html: html});
      });
  return recordIDToHtml;
}


