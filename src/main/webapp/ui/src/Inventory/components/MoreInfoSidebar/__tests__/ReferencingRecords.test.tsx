import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import ReferencingRecords from "../ReferencingRecords";

const mockGet = vi.fn();
vi.mock("../../../../common/InvApiService", () => ({
  default: {
    get: (path: string) => mockGet(path),
  },
}));

function renderPanel(props: Partial<React.ComponentProps<typeof ReferencingRecords>> = {}) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <ReferencingRecords
        globalId="SA42"
        invKind="samples"
        onPeek={vi.fn()}
        {...props}
      />
    </ThemeProvider>,
  );
}

describe("ReferencingRecords", () => {
  beforeEach(() => {
    mockGet.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders headings for both ELN documents and inventory items sections", async () => {
    mockGet.mockImplementation((path: string) => {
      if (path.includes("listOfMaterials")) {
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({ data: { referencingItems: [] } });
    });

    renderPanel();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: /show references/i }));

    await waitFor(() =>
      expect(screen.getByText(/referenced in eln documents/i)).toBeTruthy(),
    );
    expect(screen.getByText(/linked from inventory items/i)).toBeTruthy();
  });

  it("renders inventory referencing items rows", async () => {
    mockGet.mockImplementation((path: string) => {
      if (path.includes("listOfMaterials")) {
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({
        data: {
          referencingItems: [
            {
              sourceGlobalId: "SA77",
              sourceName: "Source sample",
              sourceType: "SAMPLE",
              relationType: "IsCalibratedBy",
              versionPin: null,
              modifiedAt: "2026-05-28T00:00:00.000Z",
            },
          ],
        },
      });
    });

    renderPanel();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: /show references/i }));

    await waitFor(() => expect(screen.getByText(/SA77/)).toBeTruthy());
    expect(screen.getByText(/IsCalibratedBy/)).toBeTruthy();
    expect(screen.getByText(/Source sample/)).toBeTruthy();
  });

  it("calls onPeek with source GlobalID when a referencing row is clicked", async () => {
    const onPeek = vi.fn();
    mockGet.mockImplementation((path: string) => {
      if (path.includes("listOfMaterials")) {
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({
        data: {
          referencingItems: [
            {
              sourceGlobalId: "SA77",
              sourceName: "Source sample",
              sourceType: "SAMPLE",
              relationType: "References",
              versionPin: null,
              modifiedAt: "2026-05-28T00:00:00.000Z",
            },
          ],
        },
      });
    });

    renderPanel({ onPeek });
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: /show references/i }));
    const cell = await screen.findByText(/SA77/);
    await user.click(cell);

    expect(onPeek).toHaveBeenCalledWith("SA77");
  });
});
