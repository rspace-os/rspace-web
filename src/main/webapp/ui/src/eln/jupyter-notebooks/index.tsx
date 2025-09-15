import React from "react";
import {createRoot} from "react-dom/client";
import ExternalWorkflowInvocations
  from "@/eln/eln-external-workflows/ExternalWorkflowInvocations";
import { IpynbRenderer } from "react-ipynb-renderer";
import axios from "@/common/axios";
import {idToString} from "@/eln/gallery/useGalleryListing";

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
        (async () =>
        {
          const {data} = await axios.get<unknown>(
              "/Streamfile/" +
              attachedFiles[0]
          );
          if (isForNotebookPage) {
            // @ts-expect-error style does exist on HTMLDivElement
            wrapperDiv.style.position = "relative"
          }
          const root = createRoot(wrapperDiv);
          root.render(
              <IpynbRenderer ipynb={data}/>
          );
        })();
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
function getAttachedFilesByParsingEmbeddedText(selector: string): string[] {
  const recordIds: string [] = [];
  $('<div>' + $('#' + selector).val()).find('.attachmentDiv').each(
      function (index) {
        const record :string = $(this).find('.attachmentInfoDiv').attr('id').substring(18);
        const html = $(this).html();
        if (html.includes("ipynb")) {
          recordIds.push(record);
        }
      });
  return recordIds;
}


