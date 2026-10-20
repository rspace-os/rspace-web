import { useForm, validate } from "@formisch/react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import * as v from "valibot";
import { expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import type { StructuredFieldProps } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { RenderFields } from "../RenderFields";

type ExampleDocument = { id: string; rows: Array<{ quantity: number }> };

function RowEditor({ disabled, fieldConfig, id, describedBy, invalid }: StructuredFieldProps<ExampleDocument>) {
  const child = fieldConfig.fields[0];
  return (
    <input
      id={id}
      aria-describedby={describedBy}
      aria-invalid={invalid || undefined}
      type="number"
      min={Number(child.writeConstraints?.minimum)}
      max={Number(child.writeConstraints?.maximum)}
      required={child.required}
      disabled={disabled}
    />
  );
}

function Example({ disabled = false, readOnly = false, show = true }) {
  const form = useForm({
    schema: v.object({
      id: v.string(),
      rows: v.pipe(v.array(v.object({ quantity: v.number() })), v.minLength(2, "Add another row")),
    }),
    initialInput: { id: "1", rows: [{ quantity: 1 }] },
  });
  const fields = resolveCollectionConfig<ExampleDocument>({
    slug: "examples",
    idField: "id",
    useAsTitle: "id",
    defaultColumns: ["id"],
    labels: { singularKey: "example", pluralKey: "examples" },
    fields: [
      { name: "id", type: "text", labelKey: "id", form: false },
      {
        name: "rows",
        type: "array",
        labelKey: "rows",
        readOnly,
        form: { component: RowEditor, descriptionKey: "rowsDescription", condition: () => show },
        fields: [
          {
            name: "quantity",
            type: "number",
            labelKey: "quantity",
            required: true,
            writeConstraints: { minimum: 1, maximum: 100 },
          },
        ],
      },
    ],
  }).fields;
  return (
    <>
      <RenderFields form={form} fields={fields} disabled={disabled} />
      <button type="button" onClick={() => void validate(form)}>
        {"Validate"}
      </button>
    </>
  );
}

it("passes child constraints and disabled state to the custom array editor", () => {
  render(<Example disabled />);
  const quantity = screen.getByRole("spinbutton", { name: "common:rows" });
  expect(quantity).toHaveAttribute("min", "1");
  expect(quantity).toHaveAttribute("max", "100");
  expect(quantity).toBeRequired();
  expect(quantity).toBeDisabled();
});

it("disables read-only array fields and respects their visibility condition", () => {
  const { rerender } = render(<Example readOnly />);
  expect(screen.getByRole("spinbutton", { name: "common:rows" })).toBeDisabled();
  rerender(<Example show={false} />);
  expect(screen.queryByRole("spinbutton", { name: "common:rows" })).not.toBeInTheDocument();
});

it("associates the custom control with its label, description and validation error", async () => {
  const user = userEvent.setup();
  const { container } = render(<Example />);
  const quantity = screen.getByRole("spinbutton", { name: "common:rows" });
  expect(quantity).toHaveAccessibleDescription("common:rowsDescription");
  await user.click(screen.getByRole("button", { name: "Validate" }));
  expect(await screen.findByText("Add another row")).toBeInTheDocument();
  expect(quantity).toBeInvalid();
  expect(quantity).toHaveAccessibleDescription("common:rowsDescription Add another row");
  await expectAccessible(container);
});
