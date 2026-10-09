import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { RenderFieldsPage } from "./pageObjects/RenderFieldsPage";
import { RenderFieldsStory } from "./RenderFields.story";

const form = new RenderFieldsPage();

function rowItem(control: Element): DOMRect {
  const item = control.closest('[data-slot="field-row-item"]');
  if (!item) throw new Error("Expected control to be inside a row item");
  return item.getBoundingClientRect();
}

afterEach(() => {
  cleanup();
});

describe("RowField", () => {
  test("lays out fields at configured and equal widths", async () => {
    render(
      <div style={{ width: 800 }}>
        <RenderFieldsStory />
      </div>,
    );

    await expect
      .poll(() => {
        const title = rowItem(form.title.element());
        const score = rowItem(form.score.element());
        const modifiedAt = rowItem(form.modifiedAt.element());
        return {
          equalFlexibleWidths: Math.abs(title.width - modifiedAt.width) < 1,
          fixedWidth: Math.round(score.width),
          sameRow: Math.abs(title.top - score.top) < 1 && Math.abs(title.top - modifiedAt.top) < 1,
        };
      })
      .toEqual({ equalFlexibleWidths: true, fixedWidth: 160, sameRow: true });
  });

  test("makes each field full width inside a small container", async () => {
    render(
      <section aria-label="Small form container" style={{ width: 320 }}>
        <RenderFieldsStory />
      </section>,
    );

    await expect
      .poll(() => {
        const container = form.smallContainer.element().getBoundingClientRect();
        const items = [form.title, form.score, form.modifiedAt].map((control) => rowItem(control.element()));
        const row = form.title.element().closest('[data-slot="field-row"]')?.getBoundingClientRect();
        return {
          contained: items.every((item) => item.left >= container.left && item.right <= container.right),
          fullWidth: row ? items.every((item) => Math.abs(item.width - row.width) < 1) : false,
          wrapped: new Set(items.map((item) => Math.round(item.top))).size > 1,
        };
      })
      .toEqual({ contained: true, fullWidth: true, wrapped: true });
  });
});
