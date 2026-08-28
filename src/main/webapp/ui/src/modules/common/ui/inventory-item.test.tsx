import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { InventoryItem } from "./inventory-item";

describe("InventoryItem", () => {
  it("renders its name as non-heading text by default", () => {
    render(<InventoryItem name="Confocal microscope" globalId="IN123" />);

    expect(screen.getByText("Confocal microscope").tagName).toBe("SPAN");
    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
  });

  it("can use the record name as a heading without including its Global ID", () => {
    render(
      <InventoryItem
        name="Confocal microscope"
        nameAs="h1"
        globalId="IN123"
        href="/globalId/IN123"
        idPlacement="title"
        idLinkLabel="View Confocal microscope in Inventory"
      />,
    );

    const heading = screen.getByRole("heading", { level: 1, name: "Confocal microscope" });
    expect(within(heading).queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View Confocal microscope in Inventory" })).toHaveTextContent("IN123");
  });
});
