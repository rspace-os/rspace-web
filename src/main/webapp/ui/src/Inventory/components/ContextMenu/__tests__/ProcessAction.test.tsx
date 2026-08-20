import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import materialTheme from "@/theme";
import ProcessAction from "../ProcessAction";

// The wizard itself is under test elsewhere; here only the menu entry matters.
vi.mock("../../Operations/OperationWizard", () => ({ default: () => null }));
// The subsample model reads the root store (units); a minimal stand-in suffices.
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({ unitStore: { getUnit: () => ({ label: "ml" }) } }),
}));

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
