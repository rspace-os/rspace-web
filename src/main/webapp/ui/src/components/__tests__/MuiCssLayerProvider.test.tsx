import Box from "@mui/material/Box";
import { render, within } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { getMuiCssLayerCache, MuiCssLayerProvider } from "../MuiCssLayerProvider";

const PAGE_CONTENT = "Page content";
const STYLED_CONTENT = "Styled content";
const SHADOW_CONTENT = "Shadow content";
const UNRESTRICTED_CONTENT = "Unrestricted content";

function addNonceMeta(targetDocument: Document, nonce: string): void {
  const meta = targetDocument.createElement("meta");
  meta.setAttribute("property", "csp-nonce");
  meta.nonce = nonce;
  targetDocument.head.appendChild(meta);
}

function createDocument(nonce?: string): Document {
  const targetDocument = document.implementation.createHTMLDocument();
  if (nonce) {
    addNonceMeta(targetDocument, nonce);
  }
  return targetDocument;
}

function getEmotionStyleTags(container: ParentNode): HTMLStyleElement[] {
  /* eslint-disable testing-library/no-node-access -- CSP behavior requires checking the nonce on Emotion's injected style tags. */
  const styleTags = Array.from(container.querySelectorAll<HTMLStyleElement>("style[data-emotion]"));
  /* eslint-enable testing-library/no-node-access */
  return styleTags;
}

describe("MuiCssLayerProvider", () => {
  test("uses the Vite nonce from its default document", () => {
    /* eslint-disable testing-library/no-node-access -- set the server meta before Emotion's document cache is first created. */
    document.querySelector('meta[property="csp-nonce"]')?.remove();
    /* eslint-enable testing-library/no-node-access */
    addNonceMeta(document, "page-nonce");

    render(
      <MuiCssLayerProvider>
        <Box sx={{ color: "tomato" }}>{PAGE_CONTENT}</Box>
      </MuiCssLayerProvider>,
    );

    expect(within(document.body).getByText(PAGE_CONTENT)).toBeInTheDocument();
    const styleTags = getEmotionStyleTags(document.head);
    expect(styleTags.length).toBeGreaterThan(0);
    expect(styleTags.every((styleTag) => styleTag.nonce === "page-nonce")).toBe(true);
  });

  test("adds the target document's Vite nonce to Emotion style tags", () => {
    const targetDocument = createDocument("document-nonce");

    render(
      <MuiCssLayerProvider container={targetDocument}>
        <Box sx={{ color: "tomato" }}>{STYLED_CONTENT}</Box>
      </MuiCssLayerProvider>,
      { container: targetDocument.body },
    );

    expect(within(targetDocument.body).getByText(STYLED_CONTENT).textContent).toBe(STYLED_CONTENT);
    const styleTags = getEmotionStyleTags(targetDocument.head);
    expect(styleTags.length).toBeGreaterThan(0);
    expect(styleTags.every((styleTag) => styleTag.nonce === "document-nonce")).toBe(true);
  });

  test("uses a separate cache for each document", () => {
    const firstDocument = createDocument("first-nonce");
    const secondDocument = createDocument("second-nonce");

    expect(getMuiCssLayerCache(firstDocument)).toBe(getMuiCssLayerCache(firstDocument));
    expect(getMuiCssLayerCache(firstDocument)).not.toBe(getMuiCssLayerCache(secondDocument));
  });

  test("uses the shadow root as the style container and its owner document's nonce", () => {
    const targetDocument = createDocument("shadow-nonce");
    const host = targetDocument.createElement("div");
    const shadowRoot = host.attachShadow({ mode: "open" });
    const otherHost = targetDocument.createElement("div");
    const otherShadowRoot = otherHost.attachShadow({ mode: "open" });

    expect(getMuiCssLayerCache(shadowRoot)).toBe(getMuiCssLayerCache(shadowRoot));
    expect(getMuiCssLayerCache(shadowRoot)).not.toBe(getMuiCssLayerCache(otherShadowRoot));

    render(
      <MuiCssLayerProvider container={shadowRoot}>
        <Box sx={{ color: "tomato" }}>{SHADOW_CONTENT}</Box>
      </MuiCssLayerProvider>,
      { container: shadowRoot },
    );

    const styleTags = getEmotionStyleTags(shadowRoot);
    expect(styleTags.length).toBeGreaterThan(0);
    expect(styleTags.every((styleTag) => styleTag.nonce === "shadow-nonce")).toBe(true);
  });

  test("tolerates a target document without a nonce meta", () => {
    const targetDocument = createDocument();

    render(
      <MuiCssLayerProvider container={targetDocument}>
        <Box sx={{ color: "tomato" }}>{UNRESTRICTED_CONTENT}</Box>
      </MuiCssLayerProvider>,
      { container: targetDocument.body },
    );

    expect(within(targetDocument.body).getByText(UNRESTRICTED_CONTENT).textContent).toBe(UNRESTRICTED_CONTENT);
    const styleTags = getEmotionStyleTags(targetDocument.head);
    expect(styleTags.length).toBeGreaterThan(0);
    expect(styleTags.every((styleTag) => !styleTag.nonce)).toBe(true);
  });
});
