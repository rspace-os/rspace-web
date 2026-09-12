import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import materialTheme from "@/theme";
import ProcessAction, { isProcessableSelection } from "../ProcessAction";

// The wizard itself is under test elsewhere; here only the menu entry matters.
vi.mock("../../Operations/OperationWizard", () => ({ default: () => null }));
// The subsample model reads the root store (units); a minimal stand-in suffices.
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({ unitStore: { getUnit: () => ({ label: "ml" }) } }),
}));

describe("isProcessableSelection", () => {
  // The single gate shared by ContextActions visibility and the action's own wizard mounting.
  it("requires at least one record, all of them subsamples", () => {
    expect(isProcessableSelection([])).toBe(false);
    expect(isProcessableSelection([makeMockSubSample({})])).toBe(true);
    expect(isProcessableSelection([makeMockSubSample({}), makeMockSubSample({ id: 2, globalId: "SS2" })])).toBe(true);
    expect(isProcessableSelection([makeMockSubSample({}), {} as InventoryRecord])).toBe(false);
  });
});

describe("ProcessAction", () => {
  it("uses the same icon as the Derive operation (code-branch), tying the menu entry to the wizard", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <ProcessAction as="button" disabled="" selectedResults={[makeMockSubSample({})]} closeMenu={() => {}} />
      </ThemeProvider>,
    );
    const button = screen.getByRole("button", { name: /operations\.action\.process/i });
    expect(button.querySelector('svg[data-icon="code-branch"]')).toBeInTheDocument();
  });
});
