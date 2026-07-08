import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render } from "@/__tests__/customQueries";

const { mockAxiosGet } = vi.hoisted(() => ({
  mockAxiosGet: vi.fn<(url: string, config: { params: URLSearchParams }) => Promise<{ data: string }>>(),
}));

vi.mock("@/common/axios", () => ({
  default: {
    get: mockAxiosGet,
  },
}));

vi.mock("../../../components/Ketcher/KetcherDialog", () => ({
  default: ({ isOpen, title }: { isOpen: boolean; title: string }) =>
    isOpen ? <div role="dialog" aria-label={title} /> : null,
}));

import KetcherViewer from "../KetcherViewer";

describe("KetcherViewer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    document.body.innerHTML = "";

    Object.assign(globalThis, {
      tinymceDialogUtils: {
        showErrorAlert: vi.fn(),
      },
    });
  });

  it("loads a chemical selection from a TinyMCE iframe document without jQuery", async () => {
    const editorDocument = document.implementation.createHTMLDocument("editor");
    const chemicalNode = editorDocument.createElement("img");

    chemicalNode.id = "chem-42";
    chemicalNode.className = "chem";
    chemicalNode.setAttribute("data-rsrevision", "3");
    editorDocument.body.appendChild(chemicalNode);

    mockAxiosGet.mockResolvedValueOnce({
      data: '{"mol0":{}}',
    });

    Object.assign(globalThis, {
      tinymce: {
        activeEditor: {
          selection: {
            getNode: () => chemicalNode,
          },
        },
      },
    });

    render(<KetcherViewer />);

    expect(
      await screen.findByRole("dialog", {
        name: "common:ketcher.viewerTitle",
      }),
    ).toBeVisible();

    const [requestUrl, requestConfig] = mockAxiosGet.mock.calls[0] ?? [];

    expect(requestUrl).toBe("/chemical/file/contents");
    expect(requestConfig?.params).toBeInstanceOf(URLSearchParams);
    expect(requestConfig?.params.toString()).toBe("chemId=chem-42&revision=3");
  });
});
