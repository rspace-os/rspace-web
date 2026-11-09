import { useForm } from "@formisch/react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import * as v from "valibot";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
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
