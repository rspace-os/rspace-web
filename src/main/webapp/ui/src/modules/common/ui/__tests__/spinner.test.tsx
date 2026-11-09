import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ContentLoadingSpinner } from "../spinner";

describe("shell loading spinners", () => {
  it("keeps the outlet fallback inside its available content", () => {
    render(<ContentLoadingSpinner />);
    expect(screen.getByRole("status", { name: "common:loading" }).parentElement).toHaveClass(
      "flex-1",
      "place-items-center",
    );
  });
});
