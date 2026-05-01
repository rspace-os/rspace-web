import React from "react";
import { render } from "@testing-library/react";
import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

const rootRenderCalls: Array<{
  container: Element;
  node: React.ReactNode;
}> = [];
const mockCreateRoot = vi.fn((container: Element) => ({
  render: vi.fn((node: React.ReactNode) => {
    rootRenderCalls.push({ container, node });
  }),
}));

vi.mock("react-dom/client", () => ({
  createRoot: mockCreateRoot,
}));

vi.mock("../../accentedTheme", async () => {
  const { createTheme } = await import("@mui/material/styles");

  return {
    default: () => createTheme(),
  };
});

vi.mock("@/components/ErrorBoundary", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock("@/components/Alerts/Alerts", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock("@/components/Analytics", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock("../../hooks/api/integrationHelpers", () => ({
  useIntegrationIsAllowedAndEnabled: () => ({
    tag: "success",
    value: false,
  }),
}));

let PreviewInfoModule: typeof import("../PreviewInfo");

beforeAll(async () => {
  PreviewInfoModule = await import("../PreviewInfo");
});

describe("PreviewInfo event handlers", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    rootRenderCalls.length = 0;
    document.body.innerHTML = "";
  });

  it("renders preview info into the parent span for chem images on document-placed", () => {
    const imagesReplacedSpy = vi.fn();
    document.addEventListener("images-replaced", imagesReplacedSpy);

    document.body.innerHTML = `
      <div id="div_42">
        <span id="preview-host">
          <img
            id="chem-1"
            class="chem"
            alt="Chemical preview"
            src="data:image/gif;base64,R0lGODlhAQABAAAAACw="
            data-stoichiometry-table='{"id":1,"revision":2}'
          />
          <em>caption</em>
        </span>
      </div>
    `;

    document.dispatchEvent(new CustomEvent("document-placed", { detail: 42 }));

    expect(mockCreateRoot).toHaveBeenCalledTimes(1);
    expect(rootRenderCalls[0]?.container).toHaveAttribute("id", "preview-host");

    const renderedNode = rootRenderCalls[0]?.node as React.ReactElement<{
      item: Record<string, string | undefined>;
    }>;
    expect(renderedNode.type).toBe(PreviewInfoModule.default);
    expect(renderedNode.props.item).toMatchObject({
      id: "chem-1",
      class: "chem",
      src: "data:image/gif;base64,R0lGODlhAQABAAAAACw=",
      "data-stoichiometry-table": '{"id":1,"revision":2}',
    });
    expect(imagesReplacedSpy).toHaveBeenCalledTimes(1);

    document.removeEventListener("images-replaced", imagesReplacedSpy);
  });

  it("renders preview info directly for table-only stoichiometry nodes on document-placed", () => {
    document.body.innerHTML = `
      <div id="div_7">
        <div
          id="table-only-preview"
          data-stoichiometry-table-only="true"
          data-stoichiometry-table='{"id":3,"revision":4}'
        ></div>
      </div>
    `;

    document.dispatchEvent(new CustomEvent("document-placed", { detail: 7 }));

    expect(mockCreateRoot).toHaveBeenCalledTimes(1);
    expect(rootRenderCalls[0]?.container).toHaveAttribute(
      "id",
      "table-only-preview",
    );

    const renderedNode = rootRenderCalls[0]?.node as React.ReactElement<{
      item: Record<string, string | undefined>;
    }>;
    expect(renderedNode.props.item).toMatchObject({
      id: "table-only-preview",
      "data-stoichiometry-table-only": "true",
      "data-stoichiometry-table": '{"id":3,"revision":4}',
    });
  });

  it("renders preview info into the closest span for chem-updated images", () => {
    document.body.innerHTML = `
      <div id="div_9">
        <span id="updated-preview-host">
          <img
            id="chem-2"
            class="chem"
            alt="Updated chemical preview"
            src="data:image/gif;base64,R0lGODlhAQABAAAAACw="
            data-stoichiometry-table='{"id":5,"revision":6}'
          />
        </span>
      </div>
    `;

    document.dispatchEvent(new CustomEvent("chem-updated", { detail: 9 }));

    expect(mockCreateRoot).toHaveBeenCalledTimes(1);
    expect(rootRenderCalls[0]?.container).toHaveAttribute(
      "id",
      "updated-preview-host",
    );

    const renderedNode = rootRenderCalls[0]?.node as React.ReactElement<{
      item: Record<string, string | undefined>;
    }>;
    expect(renderedNode.props.item).toMatchObject({
      id: "chem-2",
      src: "data:image/gif;base64,R0lGODlhAQABAAAAACw=",
      "data-stoichiometry-table": '{"id":5,"revision":6}',
    });
  });
});

describe("PreviewInfo mount events", () => {
  beforeEach(() => {
    document.body.innerHTML = "";
  });

  it("fires images-replaced when a chemical image preview mounts", () => {
    const imagesReplacedSpy = vi.fn();
    document.addEventListener("images-replaced", imagesReplacedSpy);

    render(
      <PreviewInfoModule.default
        item={{
          id: "chem-3",
          class: "chem",
          src: "data:image/gif;base64,R0lGODlhAQABAAAAACw=",
        }}
      />,
    );

    expect(imagesReplacedSpy).toHaveBeenCalledTimes(1);

    document.removeEventListener("images-replaced", imagesReplacedSpy);
  });

  it("does not fire images-replaced when a table-only preview mounts", () => {
    const imagesReplacedSpy = vi.fn();
    document.addEventListener("images-replaced", imagesReplacedSpy);

    render(
      <PreviewInfoModule.default
        item={{
          "data-stoichiometry-table-only": "true",
          "data-stoichiometry-table": '{"id":8,"revision":9}',
        }}
      />,
    );

    expect(imagesReplacedSpy).not.toHaveBeenCalled();

    document.removeEventListener("images-replaced", imagesReplacedSpy);
  });
});


