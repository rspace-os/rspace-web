import { render, screen } from "@testing-library/react";
import type React from "react";
import { isValidElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { mockAxiosGet, mockAxiosPost, mockCreateRoot, rootRenderCalls } = vi.hoisted(() => {
  const renderCalls: Array<{
    container: Element;
    node: React.ReactNode;
  }> = [];

  return {
    rootRenderCalls: renderCalls,
    mockCreateRoot: vi.fn((container: Element) => ({
      render: vi.fn((node: React.ReactNode) => {
        renderCalls.push({ container, node });
      }),
      unmount: vi.fn(),
    })),
    mockAxiosGet: vi.fn(),
    mockAxiosPost: vi.fn(),
  };
});

vi.mock("react-dom/client", () => ({
  createRoot: mockCreateRoot,
}));

vi.mock("@/common/axios", () => ({
  default: {
    get: mockAxiosGet,
    post: mockAxiosPost,
  },
}));

import KetcherTinyMce from "../KetcherTinyMce";

vi.mock("../../../components/Ketcher/KetcherDialog", () => ({
  default: ({
    isOpen,
    title,
    actionBtnText,
    validationResult,
  }: {
    isOpen: boolean;
    title: string;
    actionBtnText: string;
    validationResult: { message?: string };
  }) =>
    isOpen ? (
      <div role="dialog" aria-label={title}>
        <p>{validationResult.message ?? "Ready to insert"}</p>
        <button type="button">{actionBtnText}</button>
      </div>
    ) : null,
}));

vi.mock("@/hooks/api/useChemicalImport", () => ({
  default: () => ({
    save: vi.fn(),
  }),
}));

describe("KetcherTinyMce accessibility", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    rootRenderCalls.length = 0;
    document.body.innerHTML = "";

    Object.assign(globalThis, {
      tinymceDialogUtils: {
        showErrorAlert: vi.fn(),
      },
    });

    mockAxiosGet.mockResolvedValue({ data: null });
    mockAxiosPost.mockResolvedValue({});
  });

  it("has no accessibility violations when the insert dialog is open", async () => {
    Object.assign(globalThis, {
      tinymce: {
        activeEditor: {
          id: "rtf_12",
          selection: {
            getNode: () => null,
          },
          execCommand: vi.fn(),
          windowManager: {
            close: vi.fn(),
          },
        },
      },
    });

    const { baseElement } = render(<KetcherTinyMce onUnmount={vi.fn()} />);

    expect(await screen.findByRole("dialog", { name: "Ketcher Insert Chemical" })).toBeVisible();

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    await expect(baseElement).toBeAccessible();
  });

  it("loads an existing chemical when the selected node comes from another document", async () => {
    const editorDocument = document.implementation.createHTMLDocument("editor");
    const chemicalNode = editorDocument.createElement("img");

    chemicalNode.id = "chem-42";
    chemicalNode.className = "chem";
    editorDocument.body.appendChild(chemicalNode);

    mockAxiosGet.mockResolvedValueOnce({
      data: {
        data: {
          chemElements: '{"mol0":{}}',
        },
      },
    });

    Object.assign(globalThis, {
      tinymce: {
        activeEditor: {
          id: "rtf_12",
          selection: {
            getNode: () => chemicalNode,
          },
          execCommand: vi.fn(),
          windowManager: {
            close: vi.fn(),
          },
        },
      },
    });

    render(<KetcherTinyMce onUnmount={vi.fn()} />);

    expect(await screen.findByRole("dialog", { name: "Ketcher Insert Chemical" })).toBeVisible();
    expect(mockAxiosGet).toHaveBeenCalledWith("/chemical/ajax/loadChemElements", {
      params: {
        chemId: "chem-42",
      },
    });
  });

  it("reuses the existing React root when the TinyMCE Ketcher dialog is opened multiple times", () => {
    document.body.innerHTML = '<div id="tinymce-ketcher"></div>';

    window.dispatchEvent(new Event("OPEN_KETCHER_DIALOG"));
    window.dispatchEvent(new Event("OPEN_KETCHER_DIALOG"));

    expect(mockCreateRoot).toHaveBeenCalledTimes(1);
    expect(rootRenderCalls).toHaveLength(2);
    expect(rootRenderCalls[0]?.container).toHaveAttribute("id", "tinymce-ketcher");
    expect(rootRenderCalls[1]?.container).toHaveAttribute("id", "tinymce-ketcher");
  });

  it("unmounts the component on close and creates a fresh mount on re-open", () => {
    document.body.innerHTML = '<div id="tinymce-ketcher"></div>';

    window.dispatchEvent(new Event("OPEN_KETCHER_DIALOG"));
    expect(mockCreateRoot).toHaveBeenCalledTimes(1);
    expect(rootRenderCalls).toHaveLength(1);

    // Find KetcherTinyMce's onUnmount by walking the rendered tree's `children`
    // chain, so the test doesn't depend on how many wrapper providers sit above it.
    const findOnUnmount = (node: React.ReactNode): (() => void) | undefined => {
      if (!isValidElement(node)) return undefined;
      const props = node.props as { onUnmount?: () => void; children?: React.ReactNode };
      return props.onUnmount ?? findOnUnmount(props.children);
    };
    const onUnmount = findOnUnmount(rootRenderCalls[0].node);

    expect(onUnmount).toBeTypeOf("function");

    // Simulates the user closing the dialog.
    onUnmount?.();

    // The mock root's unmount should have been called.
    const firstRoot = mockCreateRoot.mock.results[0].value as {
      unmount: ReturnType<typeof vi.fn>;
    };
    expect(firstRoot.unmount).toHaveBeenCalledOnce();

    // Re-opening should create a brand-new React root (fresh mount → fresh state).
    window.dispatchEvent(new Event("OPEN_KETCHER_DIALOG"));
    expect(mockCreateRoot).toHaveBeenCalledTimes(2);
    expect(rootRenderCalls).toHaveLength(2);
  });
});
