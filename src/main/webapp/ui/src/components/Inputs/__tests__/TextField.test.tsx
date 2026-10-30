import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { PHISHING_DESCRIPTION } from "@/__tests__/mocks/inventoryRichTextMocks";
import TextField from "../TextField";

describe("TextField in read-only mode", () => {
  test("shows the description's text and none of its form controls or images", () => {
    render(<TextField value={PHISHING_DESCRIPTION} disabled />);

    expect(screen.getByText("Calibrated light microscope.")).toBeInTheDocument();
    expect(screen.getByText("Your session expired. Sign in again to see the full record.")).toBeInTheDocument();
    expect(screen.getByText("Sign in")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Sign in" })).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Username")).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText("RSpace password")).not.toBeInTheDocument();
    expect(screen.queryByRole("img", { name: "tracking pixel" })).not.toBeInTheDocument();
  });

  test("keeps a link that opens in a new tab", () => {
    render(
      <TextField value='<p><a href="https://example.org/docs" target="_blank" rel="noopener">docs</a></p>' disabled />,
    );

    expect(screen.getByRole("link", { name: "docs" })).toHaveAttribute("target", "_blank");
  });
});
