import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import {
  StandardTableListCardGrid,
  type StandardTableListCardItem,
} from "../../prototypes/StandardTableListCardPrototype";

const firstRecord = "First record";
const secondRecord = "Second record";
const open = "Open";

const items: readonly StandardTableListCardItem[] = [
  {
    id: "one",
    accessibleName: firstRecord,
    title: <a href="#one">{firstRecord}</a>,
    selection: <input type="checkbox" aria-label="Select First record" />,
    fields: [
      { id: "owner", label: "Owner", value: "Ada" },
      {
        id: "schedule",
        label: "Schedule",
        value: <div role="img" aria-label="Availability timeline" />,
        fullWidth: true,
      },
    ],
    actions: <button type="button">{open}</button>,
  },
  {
    id: "two",
    accessibleName: secondRecord,
    title: <a href="#two">{secondRecord}</a>,
    fields: [],
  },
];

describe("StandardTableListCardGrid prototype", () => {
  it("standardises arbitrary table rows as accessible title, field, and action cards", async () => {
    const { container } = render(<StandardTableListCardGrid label="Records" items={items} />);

    expect(screen.getByRole("list", { name: "Records" })).toBeVisible();
    const first = screen.getByRole("article", { name: "First record" });
    expect(within(first).getByRole("link", { name: "First record" })).toBeVisible();
    const terms = within(first).getAllByRole("term");
    const definitions = within(first).getAllByRole("definition");
    expect(terms).toHaveLength(2);
    expect(terms[0]).toHaveTextContent("Owner");
    expect(terms[1]).toHaveTextContent("Schedule");
    expect(definitions[0]).toHaveTextContent("Ada");
    expect(within(first).getByRole("img", { name: "Availability timeline" })).toBeVisible();
    expect(within(first).getByRole("button", { name: "Open" })).toBeVisible();
    expect(within(first).getByRole("checkbox", { name: "Select First record" })).toBeVisible();
    expect(screen.getByRole("article", { name: "Second record" })).toBeVisible();
    await expectAccessible(container);
  });
});
