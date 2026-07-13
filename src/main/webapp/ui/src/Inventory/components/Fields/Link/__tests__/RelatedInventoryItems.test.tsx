import { ThemeProvider } from "@mui/material/styles";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@/__tests__/customQueries";
import materialTheme from "../../../../../theme";

const mockUseReferencing = vi.fn();
vi.mock("@/eln/gallery/useReferencingInventoryItems", () => ({
  default: (globalId: string | null) => mockUseReferencing(globalId) as unknown,
}));

import RelatedInventoryItems from "../RelatedInventoryItems";

function renderSection() {
  return render(
    <ThemeProvider theme={materialTheme}>
      <RelatedInventoryItems globalId="SD1" recordTypeName="document" />
    </ThemeProvider>,
  );
}

describe("RelatedInventoryItems", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("renders one row per referencing item", () => {
    mockUseReferencing.mockReturnValue({
      items: [
        { globalId: "SA7", name: "sample A", relationType: "References" },
        { globalId: "IC9", name: "box B", relationType: "Cites" },
      ],
      loading: false,
      errorMessage: null,
    });

    renderSection();

    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    expect(screen.getByText("SA7")).toBeInTheDocument();
    expect(screen.getByText("IC9")).toBeInTheDocument();
  });

  it("keeps row keys unique when one source links to the record twice", () => {
    // the endpoint returns one row per link FIELD, so a single source item
    // linking through two fields legitimately repeats its sourceGlobalId;
    // keying rows on globalId alone would collide
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    mockUseReferencing.mockReturnValue({
      items: [
        { globalId: "SA7", name: "sample A", relationType: "References" },
        { globalId: "SA7", name: "sample A", relationType: "Cites" },
      ],
      loading: false,
      errorMessage: null,
    });

    renderSection();

    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    expect(errorSpy).not.toHaveBeenCalled();
  });
});
