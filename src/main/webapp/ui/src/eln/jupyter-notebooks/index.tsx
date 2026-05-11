import React from "react";
import { createRoot } from "react-dom/client";
import { Notebook, type Ipynb } from "@jupyter-kit/react";
import { createKatexPlugin } from "@jupyter-kit/katex";
import { javascript } from "@jupyter-kit/core/langs/javascript";
import { julia } from "@jupyter-kit/core/langs/julia";
import { haskell } from "@jupyter-kit/core/langs/haskell";
import { python } from "@jupyter-kit/core/langs/python";
import { r } from "@jupyter-kit/core/langs/r";
import { ruby } from "@jupyter-kit/core/langs/ruby";
import axios from "@/common/axios";
import "@jupyter-kit/theme-default/default.css";
import "@jupyter-kit/theme-default/syntax/one-dark.css";
import "katex/dist/katex.min.css";

const supportedLanguages = [python, javascript, r, julia, haskell, ruby];

const supportedLanguageNames = new Set(
  supportedLanguages.flatMap(({ name, aliases = [] }) => [name, ...aliases]),
);

const notebookPlugins = [createKatexPlugin()];

function getFieldButton(fieldId: string | null): HTMLElement | null {
  return fieldId == null
    ? null
    : document.getElementById("jupyter_notebooks_button_" + fieldId);
}

function showElement(element: HTMLElement, defaultDisplay = "block") {
  element.hidden = false;
  element.style.display = "";
  if (window.getComputedStyle(element).display === "none") {
    element.style.display = defaultDisplay;
  }
}

function toggleElement(element: HTMLElement) {
  if (element.hidden || window.getComputedStyle(element).display === "none") {
    showElement(element);
    return;
  }

  element.style.display = "none";
}

function findJupyterNotebookContents(root: Element): Element[] {
  const matches: Element[] = [];

  if (root.classList.contains("jupyter_notebooks_contents")) {
    matches.push(root);
  }

  matches.push(...root.querySelectorAll(".jupyter_notebooks_contents"));

  return matches;
}

function getJupyterDivsUntil(
  start: Element | null,
  stop: (element: Element) => boolean,
  direction: "next" | "previous",
): Element[] {
  const matches: Element[] = [];
  let current = start;

  while (current != null && !stop(current)) {
    matches.push(...findJupyterNotebookContents(current));
    current =
      direction === "next"
        ? current.nextElementSibling
        : current.previousElementSibling;
  }

  return matches;
}

function getNextAttachmentDivs(button: Element | null): Element[] {
  const attachments: Element[] = [];
  let current = button?.nextElementSibling ?? null;

  while (current != null) {
    if (current.classList.contains("attachmentDiv")) {
      attachments.push(current);
    }
    current = current.nextElementSibling;
  }

  return attachments;
}

function parseAttachmentsFromTextField(fieldId: string | null): Element[] {
  const container = document.createElement("div");

  container.innerHTML = getTextFieldHtml(fieldId);

  return [...container.querySelectorAll(".attachmentDiv")];
}

function getAttachmentRecordId(
  attachment: Element,
  isNotebook: boolean,
): string | null {
  if (isNotebook) {
    const downloadLink = attachment.querySelector<HTMLAnchorElement>(
      ".inlineActionLink.downloadActionLink",
    );

    return downloadLink?.getAttribute("href")?.substring(12) ?? null;
  }

  const attachmentInfo =
    attachment.querySelector<HTMLElement>(".attachmentInfoDiv");

  return attachmentInfo?.id.substring(18) ?? null;
}

/**
 * Renders a view of an attached jupyter notebook
 */

/**
 * invoked on page load of a notebook page by journal.js event dispatch
 */
window.addEventListener("jupyterNotebooks-init", function () {
  loadUIOnPageLoad(true);
});
window.addEventListener("jupyter_viewer_click", function (event) {
  const { detail } = event as CustomEvent<{ id: string | number }>;

  // alert(event.detail.id);
  [...document.getElementsByClassName("jupyter_notebooks_contents")].forEach(
    (wrapperDiv) => {
      const fieldId = wrapperDiv.getAttribute("data-field-id");
      if (fieldId === `${detail.id}`) {
        toggleElement(wrapperDiv as HTMLElement);
      }
    },
  );
});

function getNotebookLanguage(ipynb: Ipynb): string {
  const metadata = ipynb.metadata;
  const kernelspec = metadata?.kernelspec as
    | Record<string, unknown>
    | undefined;
  const languageInfo = metadata?.language_info as
    | Record<string, unknown>
    | undefined;
  const rawLanguage =
    (typeof languageInfo?.name === "string" && languageInfo.name) ||
    (typeof kernelspec?.language === "string" && kernelspec.language) ||
    "python";
  const normalisedLanguage = rawLanguage.toLowerCase();

  const aliases: Record<string, string> = {
    js: "javascript",
    node: "javascript",
    python3: "python",
    py: "python",
  };

  const resolvedLanguage = aliases[normalisedLanguage] ?? normalisedLanguage;

  return supportedLanguageNames.has(resolvedLanguage)
    ? resolvedLanguage
    : "python";
}

/**
 *
 * notebook pages and structured doc pages require different positioning for the Workflow Button
 * @param isForNotebookPage
 */
const loadUIOnPageLoad = (isForNotebookPage = false) => {
  [...document.getElementsByClassName("jupyter_notebooks_contents")].forEach(
    (wrapperDiv) => {
      const fieldId = wrapperDiv.getAttribute("data-field-id");
      const attachedFileIds = getAttachedFilesByParsingEmbeddedText(
        isForNotebookPage,
        fieldId,
      );
      for (const attachedFileId of attachedFileIds) {
        const rootDivId = "rootDiv_" + attachedFileId;
        const rootDiv = document.createElement("div");

        rootDiv.id = rootDivId;
        wrapperDiv.append(rootDiv);

        void (async () => {
          const { data } = await axios.get<Ipynb>(
            "/Streamfile/" + attachedFileId,
          );
          const root = createRoot(rootDiv);

          function App() {
            return (
              <div style={{ margin: "4rem 2rem", border: "1px solid black" }}>
                <Notebook
                  ipynb={data}
                  filename={attachedFileId + ".ipynb"}
                  language={getNotebookLanguage(data)}
                  languages={supportedLanguages}
                  plugins={notebookPlugins}
                />
              </div>
            );
          }

          root.render(<App />);
        })();
      }
    },
  );
};
/**
 * invoked when Structured Doc page loads (but not when a Notebook page loads)
 */
loadUIOnPageLoad();

function thereAreNoOtherJupyterDivsBetweenThisAndTheAttachmentDiv(
  fieldId: string | null,
) {
  const button = getFieldButton(fieldId);
  const matches = getJupyterDivsUntil(
    button?.nextElementSibling ?? null,
    (element) => element.classList.contains("attachmentDiv"),
    "next",
  );

  for (const jupyterDiv of matches) {
    const matchedID = jupyterDiv.getAttribute("data-field-id");
    if (fieldId !== matchedID) {
      return false;
    }
  }

  return true;
}

function thereAreNoOtherJupyterDivsBetweenTheAttachmentDivAndThis(
  attachment: Element,
  fieldId: string | null,
) {
  const buttonId = "jupyter_notebooks_button_" + fieldId;
  const matches = getJupyterDivsUntil(
    attachment.previousElementSibling,
    (element) => element.id === buttonId,
    "previous",
  );

  for (const jupyterDiv of matches) {
    const matchedID = jupyterDiv.getAttribute("data-field-id");
    if (fieldId !== matchedID) {
      return false;
    }
  }

  return true;
}

/*
 * we are only interested in embedded jupyter links
 */
function getTextFieldHtml(fieldId: string | null): string {
  if (fieldId == null) {
    return "";
  }

  const textField = document.getElementById("rtf_" + fieldId);

  if (
    textField instanceof HTMLInputElement ||
    textField instanceof HTMLTextAreaElement ||
    textField instanceof HTMLSelectElement
  ) {
    return textField.value;
  }

  return "";
}

function getAttachedFilesByParsingEmbeddedText(
  isNotebook: boolean,
  fieldId: string | null,
): string[] {
  const recordIds: string[] = [];
  //For documents in any mode and notebook entries in 'edit' mode, this will create an array of all attachments in this textfield
  //However, for notebooks in 'view' mode this will create an array of all attachments in the entire document
  const attachments = isNotebook
    ? getNextAttachmentDivs(getFieldButton(fieldId))
    : parseAttachmentsFromTextField(fieldId);

  if (attachments.length > 0) {
    for (const attachment of attachments) {
      const record = getAttachmentRecordId(attachment, isNotebook);
      const html = attachment.innerHTML;

      if (!record || typeof html !== "string") {
        continue;
      }

      if (html.includes("ipynb")) {
        // In Notebook 'view mode', attachment divs have no connection to a useable ID for finding them
        // So we determine if our jupyter div has no other jupyter divs between it and the next attachment div
        if (thereAreNoOtherJupyterDivsBetweenThisAndTheAttachmentDiv(fieldId)) {
          const button = getFieldButton(fieldId);

          if (button != null) {
            showElement(button, "inline-block");
          }
          if (
            thereAreNoOtherJupyterDivsBetweenTheAttachmentDivAndThis(
              attachment,
              fieldId,
            )
          ) {
            recordIds.push(record);
          }
        }
      }
    }
  }
  return recordIds;
}
