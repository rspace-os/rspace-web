import React from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import SidebarInfo from "../SidebarInfo";

vi.mock("../ChemCard", () => ({
  default: ({
    item,
    onClose,
  }: {
    item: { id: string };
    onClose: (id?: string) => void;
  }) => (
    <section aria-label={`Chemical ${item.id}`}>
      <span>{item.id}</span>
      <button onClick={() => onClose(item.id)} type="button">
        Close
      </button>
    </section>
  ),
}));

function createIframeWithDocument() {
  const iframe = document.createElement("iframe");
  const iframeDocument = document.implementation.createHTMLDocument("iframe");

  Object.defineProperty(iframe, "contentDocument", {
    configurable: true,
    value: iframeDocument,
  });
  Object.defineProperty(iframe, "contentWindow", {
    configurable: true,
    value: { document: iframeDocument },
  });

  return { iframe, iframeDocument };
}

function createChemicalImage(iframeDocument: Document, id: string) {
  const chemicalImage = iframeDocument.createElement("img");

  chemicalImage.id = id;
  chemicalImage.className = "chem";
  chemicalImage.src = "data:image/gif;base64,R0lGODlhAQABAAAAACw=";
  iframeDocument.body.appendChild(chemicalImage);

  return chemicalImage;
}

describe("SidebarInfo accessibility", () => {
  beforeEach(() => {
    delete (globalThis as { tinymce?: unknown }).tinymce;
  });

  afterEach(() => {
    delete (globalThis as { tinymce?: unknown }).tinymce;
  });

  it("has no accessibility violations when showing selected chemistry cards", async () => {
    const { iframe, iframeDocument } = createIframeWithDocument();
    const execCommand = vi.fn();
    const chemicalImage = createChemicalImage(iframeDocument, "chem-1");

    Object.assign(globalThis, {
      tinymce: {
        activeEditor: {
          execCommand,
        },
      },
    });

    const { baseElement } = render(<SidebarInfo iframe={iframe} />);

    act(() => {
      chemicalImage.dispatchEvent(new Event("click", { bubbles: true }));
    });

    expect(
      await screen.findByRole("region", { name: "Chemical chem-1" }),
    ).toBeVisible();

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    await expect(baseElement).toBeAccessible();
  });

  it("shows a chemical card when a chemistry image is inserted via custom event", async () => {
    const { iframe, iframeDocument } = createIframeWithDocument();

    createChemicalImage(iframeDocument, "chem-2");

    render(<SidebarInfo iframe={iframe} />);

    act(() => {
      document.dispatchEvent(
        new CustomEvent("tinymce-chem-inserted", { detail: "chem-2" }),
      );
    });

    expect(
      await screen.findByRole("region", { name: "Chemical chem-2" }),
    ).toBeVisible();
  });

  it("removes cards when chemistry images are removed from the editor document", async () => {
    const { iframe, iframeDocument } = createIframeWithDocument();
    const chemicalImage = createChemicalImage(iframeDocument, "chem-3");

    render(<SidebarInfo iframe={iframe} />);

    act(() => {
      chemicalImage.dispatchEvent(new Event("click", { bubbles: true }));
    });

    expect(
      await screen.findByRole("region", { name: "Chemical chem-3" }),
    ).toBeVisible();

    act(() => {
      chemicalImage.remove();
    });

    await waitFor(() => {
      expect(
        screen.queryByRole("region", { name: "Chemical chem-3" }),
      ).not.toBeInTheDocument();
    });
  });
});
