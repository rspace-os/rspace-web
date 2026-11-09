import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ContentLoadingSpinner, ViewportLoadingSpinner } from "../spinner";

describe("shell loading spinners", () => {
  it("centers the booking shell fallback in the viewport", () => {
    render(<ViewportLoadingSpinner />);
    expect(screen.getByRole("status", { name: "common:loading" })).toHaveClass("size-8", "animate-spin");
    expect(screen.getByRole("status", { name: "common:loading" }).parentElement).toHaveClass(
      "fixed",
      "inset-0",
      "place-items-center",
    );
  });
  it("keeps the outlet fallback inside its available content", () => {
    render(<ContentLoadingSpinner />);
    expect(screen.getByRole("status", { name: "common:loading" }).parentElement).toHaveClass(
      "flex-1",
      "place-items-center",
    );
  });
});
