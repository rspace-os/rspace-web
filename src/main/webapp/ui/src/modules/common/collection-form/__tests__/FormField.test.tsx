import { getInput, useForm } from "@formisch/react";
import { Temporal } from "@js-temporal/polyfill";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import * as v from "valibot";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { instantToWire } from "@/modules/common/time/instant";
import { RenderFields } from "../RenderFields";
import type { FieldLayout } from "../RenderFields.types";

const fields = resolveCollectionConfig<{ enabled: boolean }>({
  slug: "records",
  idField: "enabled",
  useAsTitle: "enabled",
  defaultColumns: ["enabled"],
  labels: {
    singularKey: "collectionFormExamples:record",
    pluralKey: "collectionFormExamples:records",
  },
  fields: [
    {
      name: "enabled",
      type: "boolean",
      labelKey: "collectionFormExamples:fields.enabled",
      form: { descriptionKey: "collectionFormExamples:fields.titleDescription" },
    },
  ],
}).fields;

function CheckboxForm({ layout }: { layout: FieldLayout }) {
  const form = useForm({ schema: v.object({ enabled: v.boolean() }), initialInput: { enabled: false } });
  return <RenderFields fields={fields} form={form} layout={layout} />;
}

describe("checkbox field help", () => {
  it.each(["stacked", "inline"] as const)("explains a %s checkbox on focus without toggling it", async (layout) => {
    const user = userEvent.setup();
    const { container } = render(<CheckboxForm layout={layout} />);
    const checkbox = screen.getByRole("checkbox", { name: "collectionFormExamples:fields.enabled" });
    const help = screen.getByRole("button", { name: "common:collectionForm.fieldHelp" });

    expect(checkbox).toHaveAccessibleDescription("collectionFormExamples:fields.titleDescription");
    await user.tab();
    if (layout === "stacked") await user.tab();
    expect(help).toHaveFocus();
    expect(await screen.findByRole("tooltip")).toHaveTextContent("collectionFormExamples:fields.titleDescription");
    await user.click(help);
    expect(checkbox).not.toBeChecked();
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("tooltip")).not.toBeInTheDocument());
    await expectAccessible(container);
  });
});

const dateTimeFields = resolveCollectionConfig<{ at: Temporal.Instant | string }>({
  slug: "records",
  idField: "at",
  useAsTitle: "at",
  defaultColumns: ["at"],
  labels: {
    singularKey: "collectionFormExamples:record",
    pluralKey: "collectionFormExamples:records",
  },
  fields: [{ name: "at", type: "dateTime", labelKey: "collectionFormExamples:fields.title" }],
}).fields;

function DateTimeForm({ at }: { at: Temporal.Instant }) {
  const form = useForm({
    schema: v.object({ at: v.union([v.instance(Temporal.Instant), v.string()]) }),
    initialInput: { at },
  });
  return (
    <>
      <RenderFields fields={dateTimeFields} form={form} layout="stacked" />
      <output aria-label="Form values">{JSON.stringify(getInput(form))}</output>
    </>
  );
}

describe("date-time field", () => {
  it("shows a decoded instant as local wall-clock time and emits wire text on change", () => {
    const timeZone = Temporal.Now.timeZoneId();
    const at = Temporal.Instant.from("2026-08-17T10:30:00Z");
    render(<DateTimeForm at={at} />);
    const input = screen.getByLabelText("collectionFormExamples:fields.title");

    expect(input).toHaveValue(at.toZonedDateTimeISO(timeZone).toPlainDateTime().toString({ smallestUnit: "minute" }));

    fireEvent.change(input, { target: { value: "2026-08-18T09:15" } });
    const emitted = JSON.parse(screen.getByRole("status", { name: "Form values" }).textContent ?? "{}").at;
    expect(emitted).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    expect(emitted).toBe(
      instantToWire(Temporal.PlainDateTime.from("2026-08-18T09:15").toZonedDateTime(timeZone).toInstant()),
    );
  });
});
