import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { PHISHING_DESCRIPTION } from "@/__tests__/mocks/inventoryRichTextMocks";
import TextField from "../TextField";

describe("TextField in read-only mode", () => {
  test("shows the description without its form controls", () => {
    render(<TextField value={PHISHING_DESCRIPTION} disabled />);

    expect(screen.getByText("Calibrated light microscope.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Sign in" })).not.toBeInTheDocument();
  });
});
