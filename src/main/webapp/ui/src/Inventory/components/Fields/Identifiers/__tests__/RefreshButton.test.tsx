import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import type { Identifier } from "../../../../../stores/definitions/Identifier";
import RefreshButton from "../RefreshButton";
import { mockIGSNIdentifier } from "./mocking";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

const submittedPidinst = (overrides?: Partial<Identifier>): Identifier => ({
  ...mockIGSNIdentifier("instrument"),
  doiType: "PIDINST_B2INST",
  state: "submitted",
  refresh: vi.fn().mockResolvedValue(undefined),
  ...overrides,
});

describe("RefreshButton", () => {
  test("renders for a submitted identifier and calls refresh on click", async () => {
    const identifier = submittedPidinst();
    render(
      <ThemeProvider theme={materialTheme}>
        <RefreshButton identifier={identifier} />
      </ThemeProvider>,
    );

    const button = screen.getByRole("button", { name: "inventory:fields.identifiers.list.refresh" });
    fireEvent.click(button);

    await waitFor(() => {
      const refresh = identifier.refresh;
      expect(vi.mocked(refresh)).toHaveBeenCalled();
    });
  });

  test("renders nothing unless the state is submitted", () => {
    for (const state of ["draft", "created", "accepted", "declined", "findable"] as const) {
      const { container } = render(
        <ThemeProvider theme={materialTheme}>
          <RefreshButton identifier={submittedPidinst({ state })} />
        </ThemeProvider>,
      );
      expect(container).toBeEmptyDOMElement();
    }
  });

  test("is disabled when the record is being edited", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <RefreshButton identifier={submittedPidinst()} disabled />
      </ThemeProvider>,
    );
    expect(screen.getByRole("button", { name: "inventory:fields.identifiers.list.refresh" })).toBeDisabled();
  });
});
