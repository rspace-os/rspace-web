import React from "react";
import { act, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
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

describe("SidebarInfo accessibility", () => {
  it("has no accessibility violations when showing selected chemistry cards", async () => {
    const { iframe, iframeDocument } = createIframeWithDocument();
    const execCommand = vi.fn();
    const chemicalImage = iframeDocument.createElement("img");

    chemicalImage.id = "chem-1";
    chemicalImage.className = "chem";
    chemicalImage.src = "data:image/gif;base64,R0lGODlhAQABAAAAACw=";
    iframeDocument.body.appendChild(chemicalImage);

    Object.assign(globalThis, {
      tinymce: {
        activeEditor: {
          execCommand,
        },
      },
    });

    const { baseElement } = render(<SidebarInfo iframe={iframe} />);

    await act(async () => {
      chemicalImage.dispatchEvent(new Event("click", { bubbles: true }));
    });

    expect(
      await screen.findByRole("region", { name: "Chemical chem-1" }),
    ).toBeVisible();

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    await expect(baseElement).toBeAccessible();
  });
});


