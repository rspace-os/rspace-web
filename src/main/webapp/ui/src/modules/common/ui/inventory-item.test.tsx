import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { InventoryItem, InventoryLocationLink } from "./inventory-item";

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
        nameClassName="text-2xl font-semibold"
        globalId="IN123"
        href="/globalId/IN123"
        idPlacement="title"
        idLinkLabel="View Confocal microscope in Inventory"
      />,
    );

    const heading = screen.getByRole("heading", { level: 1, name: "Confocal microscope" });
    expect(heading).toHaveClass("text-2xl", "font-semibold");
    expect(within(heading).queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View Confocal microscope in Inventory" })).toHaveTextContent("IN123");
  });

  it("keeps the compact global ID beside the name by default", () => {
    const { container } = render(<InventoryItem name="Confocal microscope" globalId="IN123" compact />);

    const title = container.querySelector('[data-slot="item-title"]');
    expect(title).toContainElement(screen.getByText("IN123"));
    expect(screen.getByText("Confocal microscope")).not.toHaveAttribute("title");
  });

  it("can move the compact global ID under the name and expose the full name on hover", () => {
    const { container } = render(
      <InventoryItem
        name="Development booking microscope"
        nameTitle="Development booking microscope"
        globalId="IN8"
        compact
        compactIdPlacement="below"
      />,
    );

    const title = container.querySelector('[data-slot="item-title"]');
    expect(title).not.toContainElement(screen.getByText("IN8"));
    expect(container.querySelector('[data-slot="item-content"]')).toContainElement(screen.getByText("IN8"));
    expect(container.querySelector('[data-slot="item-description"]')).not.toBeInTheDocument();
    expect(screen.getByText("Development booking microscope")).toHaveAttribute(
      "title",
      "Development booking microscope",
    );
  });

  it("explains the global ID on hover without replacing a link's accessible name", () => {
    render(
      <>
        <InventoryItem name="Scope" globalId="IN1" />
        <InventoryItem name="Freezer" globalId="IN2" href="/globalId/IN2" idLinkLabel="Open IN2" />
      </>,
    );

    expect(screen.getByText("IN1")).toHaveAttribute("title", "common:values.inventoryGlobalId");
    const link = screen.getByRole("link", { name: "Open IN2" });
    expect(link).toHaveAttribute("title", "common:values.inventoryGlobalId");
  });

  it("can keep a compact location on one line", () => {
    render(<InventoryLocationLink name="Imaging lab" globalId="IC456" compact />);

    expect(screen.getByRole("link", { name: "Imaging lab" }).parentElement).toHaveClass("whitespace-nowrap");
  });
});
