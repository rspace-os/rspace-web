import "@/modules/common/styles/index.css";
import { cleanup, render } from "@testing-library/react";
import { useState } from "react";
import { afterEach, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import { TableList } from "../TableList";
import { config } from "./fixtures/tableList";

afterEach(() => cleanup());

function StickyActions() {
  const openLabel = "Open";
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  return (
    <TableList
      queryString={false}
      config={config}
      rows={[{ id: "1", title: "Alpha", owner: "Ada", score: 8, enabled: true, modifiedAt: "2026-08-01T10:00:00Z" }]}
      getRowId={(row) => row.id}
      features={{ filtering: false, sorting: false, pagination: false, columns: false }}
      rowActions={{
        id: "actions",
        label: "Actions",
        renderCell: () => <button type="button">{openLabel}</button>,
        renderInteraction: () => null,
      }}
      selection={{
        value: selected,
        onChange: setSelected,
        maximumCount: 10,
        getRowLabel: (row) => row.title,
        renderActions: () => null,
      }}
    />
  );
}

function pixel(color: string, base?: string) {
  const canvas = document.createElement("canvas");
  const context = canvas.getContext("2d");
  if (!context) throw new Error("Canvas unavailable");
  if (base) {
    context.fillStyle = base;
    context.fillRect(0, 0, 1, 1);
  }
  context.fillStyle = color;
  context.fillRect(0, 0, 1, 1);
  return Array.from(context.getImageData(0, 0, 1, 1).data);
}

test("pins opaque actions and preserves hover and selected row colours", async () => {
  render(<StickyActions />);
  const table = page.getByRole("table");
  const header = table.getByRole("columnheader", { name: "Actions", exact: true });
  await expect.element(header).toBeVisible();
  expect(getComputedStyle(header.element()).position).toBe("sticky");
  expect(pixel(getComputedStyle(header.element()).backgroundColor)[3]).toBe(255);
  const row = table.getByRole("row").filter({ hasText: "Alpha" });
  const actions = row.getByRole("cell", { name: "Open", exact: true });
  const base = getComputedStyle(actions.element()).backgroundColor;
  expect(pixel(base)[3]).toBe(255);
  expect(getComputedStyle(actions.element()).position).toBe("sticky");
  await userEvent.hover(row);
  await expect
    .poll(() => {
      const expected = pixel(getComputedStyle(row.element()).backgroundColor, base);
      return pixel(getComputedStyle(actions.element()).backgroundColor).every(
        (value, index) => Math.abs(value - expected[index]) <= 2,
      );
    })
    .toBe(true);
  await userEvent.click(row.getByRole("checkbox"));
  await expect.element(row).toHaveAttribute("data-state", "selected");
  await expect
    .poll(() => {
      const expected = pixel(getComputedStyle(row.element()).backgroundColor, base);
      return pixel(getComputedStyle(actions.element()).backgroundColor).every(
        (value, index) => Math.abs(value - expected[index]) <= 2,
      );
    })
    .toBe(true);
});
