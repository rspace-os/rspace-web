import React, {useState} from "react";
import {createRoot} from "react-dom/client";
import { IpynbRenderer } from "react-ipynb-renderer";
import "react-ipynb-renderer/dist/styles/default.css";
import axios from "@/common/axios";

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
        const attachedFiles = getAttachedFilesByParsingEmbeddedText(isForNotebookPage,fieldId);
        (async () =>
        {
          const {data} = await axios.get<unknown>(
              "/Streamfile/" +
              attachedFiles[0]
          );
          const root = createRoot(wrapperDiv);
          function App() {
            const [theme, setTheme] = useState<string>('duotoneSea');
            // @ts-ignore
            // @ts-ignore
            return(
                <>
                  <div style={{height: 50}}>
                    Syntax theme: <select value={theme} onChange={(e) => setTheme(e.target.value as string)}>
                    {themes.map((theme) => (
                        <option key={theme} value={theme}>{theme}</option>
                    ))}
                  </select>
                  </div>

                  <IpynbRenderer ipynb={data} syntaxTheme={theme} />
                </>
            );
          }
          root.render(<App/>);
        })();
      }
  );
}
/**
 * invoked when Structured Doc page loads (but not when a Notebook page loads)
 */
loadUIOnPageLoad();

function thereAreNoOtherJupyterDivsBetweenThisAndTheAttachmentDiv(fieldId: string | null) {
  // @ts-ignore
  return $('#jupyter_notebooks_details_' + fieldId).nextUntil('.attachmentDiv').find('.jupyter-notebooks-textfield').length === 0;
}

/*
 * we are only interested in embedded jupyter links
 */
function getAttachedFilesByParsingEmbeddedText(isNotebook: boolean, fieldId: string | null): string[] {
  const recordIds: string [] = [];
  // @ts-ignore
  const attachment = isNotebook ? $(".journalPageContent").find('#jupyter_notebooks_details_' + fieldId).nextAll('.attachmentDiv').first() : $('<div>' + $('#rtf_' + fieldId).val()).find('.attachmentDiv')
  if (attachment.length > 0) {
    // @ts-ignore
    const record: string = isNotebook ? $(attachment).find('.inlineActionLink.downloadActionLink').attr('href').substring(12) : $(attachment).find('.attachmentInfoDiv').attr('id').substring(18);
    // @ts-ignore
    const html = $(attachment).html();
    if (html.includes("ipynb")) {
      // In Notebook 'view mode', attachment divs have no connection to a useable ID for finding them
      // So we determine if our jupyter div has no other jupyter divs between it and the next attachment div
      if (thereAreNoOtherJupyterDivsBetweenThisAndTheAttachmentDiv(fieldId)) {
        // @ts-ignore
        $('#jupyter_notebooks_details_' + fieldId).show();
      }
      recordIds.push(record);
    }
  }
  return recordIds;
}
const themes: string [] = [
  'atomDark',
  'cb',
  'coy',
  'darcula',
  'dark',
  'duotoneDark',
  'duotoneEarth',
  'duotoneForest',
  'duotoneLight',
  'duotoneSea',
  'duotoneSpace',
  'funky',
  'ghcolors',
  'hopscotch',
  'okaidia',
  'pojoaque',
  'prism',
  'solarizedlight',
  'tomorrow',
  'twilight',
  'vscDarkPlus',
  'xonokai',
];



