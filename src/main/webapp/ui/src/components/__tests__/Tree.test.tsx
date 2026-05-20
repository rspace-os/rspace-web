import { describe, expect, test, vi } from "vitest";
import React from "react";
import userEvent from "@testing-library/user-event";
import { fireEvent } from "@testing-library/react";
import { render, screen } from "@/__tests__/customQueries";
import {
  ControlledTreeExample,
  ExpandableTreeExample,
  MultiSelectTreeExample,
  SimpleTreeExample,
} from "../Tree.story";

describe("Tree", () => {
  test("supports single selection and replaces the current selection", async () => {
    const user = userEvent.setup();
    const onSelectionChange = vi.fn();

    render(<SimpleTreeExample onSelectionChange={onSelectionChange} />);

    await user.click(screen.getByText("Item 1"));
    expect(onSelectionChange).toHaveBeenLastCalledWith([
      expect.objectContaining({ name: "Item 1" }),
    ]);

    await user.click(screen.getByText("Item 2"));
    expect(onSelectionChange).toHaveBeenLastCalledWith([
      expect.objectContaining({ name: "Item 2" }),
    ]);
  });

  test("supports multi-select and clearing the selection", async () => {
    const user = userEvent.setup();
    const onSelectionChange = vi.fn();

    render(<MultiSelectTreeExample onSelectionChange={onSelectionChange} />);

    await user.click(screen.getByText("Item 1"));
    fireEvent.click(screen.getByText("Item 2"), { ctrlKey: true });
    expect(onSelectionChange).toHaveBeenCalledTimes(2);

    await user.click(screen.getByRole("button", { name: /clear selection/i }));
    expect(onSelectionChange).toHaveBeenLastCalledWith([]);
  });

  test("expands and collapses items through the control buttons", async () => {
    const user = userEvent.setup();
    const onExpansionChange = vi.fn();

    render(<ExpandableTreeExample onExpansionChange={onExpansionChange} />);

    await user.click(screen.getByRole("button", { name: /expand all/i }));
    expect(onExpansionChange).toHaveBeenCalled();
    expect(onExpansionChange.mock.lastCall?.[0]).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ name: "Parent Item" }),
        expect.objectContaining({ name: "Another Parent" }),
      ]),
    );

    await user.click(screen.getByRole("button", { name: /collapse all/i }));
    expect(onExpansionChange).toHaveBeenLastCalledWith([]);
  });

  test("supports controlled selection and expansion", async () => {
    const user = userEvent.setup();
    const onSelectionChange = vi.fn();
    const onExpansionChange = vi.fn();

    render(
      <ControlledTreeExample
        onSelectionChange={onSelectionChange}
        onExpansionChange={onExpansionChange}
      />,
    );

    await user.click(screen.getByRole("button", { name: /expand all/i }));
    expect(onExpansionChange).toHaveBeenCalled();

    await user.click(screen.getAllByText("Child Item 1")[0]);
    expect(onSelectionChange).toHaveBeenLastCalledWith([
      expect.objectContaining({ name: "Child Item 1" }),
    ]);
  });

  test("supports keyboard navigation and selection", async () => {
    const user = userEvent.setup();
    const onSelectionChange = vi.fn();

    render(<SimpleTreeExample onSelectionChange={onSelectionChange} />);

    const item1 = screen.getByRole("treeitem", { name: /Item 1/i });
    item1.focus();
    expect(item1).toHaveFocus();

    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("treeitem", { name: /Item 2/i })).toHaveFocus();

    await user.keyboard(" ");
    expect(onSelectionChange).toHaveBeenCalled();
  });

  test("is accessible", async () => {
    const { baseElement } = render(<SimpleTreeExample />);

    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    await expect(baseElement).toBeAccessible();
  });
});
