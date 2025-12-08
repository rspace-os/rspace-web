import { useState } from "react";
import { createRoot } from "react-dom/client";
import { IpynbRenderer } from "react-ipynb-renderer";
import type { IpynbType, SyntaxThemeType } from "react-ipynb-renderer/dist/types";
import "react-ipynb-renderer/dist/styles/oceans16.css";
import axios from "@/common/axios";

/**
 * Renders a view of an attached jupyter notebook
 */

/**
 * invoked on page load of a notebook page by journal.js event dispatch
 */
window.addEventListener("jupyterNotebooks-init", () => {
    loadUIOnPageLoad(true);
});
window.addEventListener("jupyter_viewer_click", (event) => {
    // alert(event.detail.id);
    [...document.getElementsByClassName("jupyter_notebooks_contents")].forEach((wrapperDiv) => {
        const fieldId = wrapperDiv.getAttribute("data-field-id");
        // @ts-expect-error
        if (fieldId === `${event.detail.id}`) {
            // @ts-expect-error
            $(wrapperDiv).toggle();
        }
    });
});
/**
 *
 * notebook pages and structured doc pages require different positioning for the Workflow Button
 * @param isForNotebookPage
 */
const loadUIOnPageLoad = (isForNotebookPage = false) => {
    [...document.getElementsByClassName("jupyter_notebooks_contents")].forEach((wrapperDiv) => {
        const fieldId = wrapperDiv.getAttribute("data-field-id");
        const attachedFileIds = getAttachedFilesByParsingEmbeddedText(isForNotebookPage, fieldId);
        for (const attachedFileId of attachedFileIds) {
            const rootDivId = `rootDiv_${attachedFileId}`;

            // @ts-expect-error
            $(wrapperDiv).append(`<div id=${rootDivId} />`);
            // @ts-expect-error
            const rootDiv = $(`#${rootDivId}`)[0] as HTMLElement;
            // @ts-expect-error
            (async () => {
                const { data } = await axios.get<IpynbType>(`/Streamfile/${attachedFileId}`);
                const root = createRoot(rootDiv);

                function App() {
                    const [theme, setTheme] = useState<SyntaxThemeType>("vscDarkPlus");
                    return (
                        <>
                            <div style={{ height: 50, marginTop: "30px" }}>
                                Syntax theme:{" "}
                                <select value={theme} onChange={(e) => setTheme(e.target.value as SyntaxThemeType)}>
                                    {themes.map((theme) => (
                                        <option key={theme} value={theme}>
                                            {theme}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <IpynbRenderer ipynb={data} syntaxTheme={theme} />
                        </>
                    );
                }

                root.render(<App />);
            })();
        }
    });
};
/**
 * invoked when Structured Doc page loads (but not when a Notebook page loads)
 */
loadUIOnPageLoad();

function thereAreNoOtherJupyterDivsBetweenThisAndTheAttachmentDiv(fieldId: string | null) {
    let result = true;
    // @ts-expect-error
    const matches = $(`#jupyter_notebooks_button_${fieldId}`)
        .nextUntil(".attachmentDiv")
        .find(".jupyter_notebooks_contents")
        .toArray();
    for (const jupyterDiv of matches) {
        const matchedID = jupyterDiv.getAttribute("data-field-id");
        if (fieldId !== matchedID) {
            result = false;
            break;
        }
    }
    return result;
}

function thereAreNoOtherJupyterDivsBetweenTheAttachmentDivAndThis(attachment: any, fieldId: string | null) {
    let result = true;
    // @ts-expect-error
    const matches = $(attachment)
        .prevUntil(`#jupyter_notebooks_button_${fieldId}`)
        .find(".jupyter_notebooks_contents")
        .toArray();
    for (const jupyterDiv of matches) {
        const matchedID = jupyterDiv.getAttribute("data-field-id");
        if (fieldId !== matchedID) {
            result = false;
            break;
        }
    }
    return result;
}

/*
 * we are only interested in embedded jupyter links
 */
function getAttachedFilesByParsingEmbeddedText(isNotebook: boolean, fieldId: string | null): string[] {
    const recordIds: string[] = [];
    // @ts-expect-error
    //For documents in any mode and notebook entries in 'edit' mode, this will create an array of all attachments in this textfield
    //However, for notebooks in 'view' mode this will create an array of all attachments in the entire document
    const attachments = isNotebook
        ? $(".journalPageContent").find(`#jupyter_notebooks_button_${fieldId}`).nextAll(".attachmentDiv").toArray()
        : $(`<div>${$(`#rtf_${fieldId}`).val()}`)
              .find(".attachmentDiv")
              .toArray();
    if (attachments.length > 0) {
        for (const attachment of attachments) {
            // @ts-expect-error
            const record: string = isNotebook
                ? $(attachment).find(".inlineActionLink.downloadActionLink").attr("href").substring(12)
                : $(attachment).find(".attachmentInfoDiv").attr("id").substring(18);
            // @ts-expect-error
            const html = $(attachment).html();
            if (html.includes("ipynb")) {
                // In Notebook 'view mode', attachment divs have no connection to a useable ID for finding them
                // So we determine if our jupyter div has no other jupyter divs between it and the next attachment div
                if (thereAreNoOtherJupyterDivsBetweenThisAndTheAttachmentDiv(fieldId)) {
                    // @ts-expect-error
                    $(`#jupyter_notebooks_button_${fieldId}`).show();
                    if (thereAreNoOtherJupyterDivsBetweenTheAttachmentDivAndThis(attachment, fieldId)) {
                        recordIds.push(record);
                    }
                }
            }
        }
    }
    return recordIds;
}
const themes: SyntaxThemeType[] = [
    "atomDark",
    "cb",
    "coy",
    "darcula",
    "dark",
    "duotoneDark",
    "duotoneEarth",
    "duotoneForest",
    "duotoneLight",
    "duotoneSea",
    "duotoneSpace",
    "funky",
    "ghcolors",
    "hopscotch",
    "okaidia",
    "pojoaque",
    "prism",
    "solarizedlight",
    "tomorrow",
    "twilight",
    "vscDarkPlus",
    "xonokai",
];
