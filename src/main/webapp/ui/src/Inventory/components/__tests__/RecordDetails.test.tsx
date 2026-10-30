import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { PHISHING_DESCRIPTION } from "@/__tests__/mocks/inventoryRichTextMocks";
import { makeMockSample } from "../../../stores/models/__tests__/SampleModel/mocking";
import materialTheme from "../../../theme";
import RecordDetails from "../RecordDetails";

vi.mock("../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: { getUnit: () => ({ label: "ml" }) },
    uiStore: { isVerySmall: false },
  }),
}));

describe("RecordDetails", () => {
  test("shows the description without its form controls", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <RecordDetails record={makeMockSample({ description: PHISHING_DESCRIPTION })} />
      </ThemeProvider>,
    );

    expect(screen.getByText("Calibrated light microscope.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Sign in" })).not.toBeInTheDocument();
  });
});
