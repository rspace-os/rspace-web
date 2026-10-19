import { useForm } from "@formisch/react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import * as v from "valibot";
import { describe, expect, it } from "vitest";
import type { FieldConfig } from "@/modules/common/collection/config";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { RenderFields } from "../RenderFields";

type RenderedData = {
  id: string;
  status: string;
  notes: string;
  conditional: string;
  selection: string;
  enabled: boolean;
};

const renderedSchema = v.object({
  id: v.string(),
  status: v.string(),
  notes: v.string(),
  conditional: v.string(),
  selection: v.string(),
  enabled: v.boolean(),
});

function resolvedFields(fields: readonly FieldConfig<RenderedData>[]) {
  return resolveCollectionConfig<RenderedData>({
    slug: "reactivity",
    idField: "id",
    useAsTitle: "id",
    defaultColumns: ["id"],
    labels: { singularKey: "item", pluralKey: "items" },
    fields: [{ name: "id", type: "text", labelKey: "id", form: false }, ...fields],
  }).fields;
}

function ConditionalTextHarness({ evaluations }: { evaluations: { count: number } }) {
  const form = useForm({
    schema: renderedSchema,
    initialInput: { id: "1", status: "show", notes: "", conditional: "", selection: "", enabled: true },
  });
  const fields = resolvedFields([
    { name: "status", type: "text", labelKey: "status" },
    { name: "notes", type: "text", labelKey: "notes" },
    {
      name: "conditional",
      type: "text",
      labelKey: "conditional",
      form: {
        condition: ({ data }) => {
          evaluations.count++;
          return data.status === "show";
        },
      },
    },
  ]);
  return <RenderFields form={form} fields={fields} />;
}

function ConditionalRowHarness({ evaluations }: { evaluations: { count: number } }) {
  const form = useForm({
    schema: renderedSchema,
    initialInput: { id: "1", status: "show", notes: "", conditional: "", selection: "", enabled: true },
  });
  const fields = resolvedFields([
    { name: "status", type: "text", labelKey: "status" },
    { name: "notes", type: "text", labelKey: "notes" },
    { name: "conditional", type: "text", labelKey: "conditional" },
  ]);
  const row = {
    type: "row" as const,
    fields: fields.filter((field) => field.name === "conditional"),
    condition: ({ data }: { data: Partial<RenderedData> }) => {
      evaluations.count++;
      return data.status === "show";
    },
  };
  return <RenderFields form={form} fields={[...fields.filter((field) => field.name !== "conditional"), row]} />;
}

function SelectWithoutDisabledHarness({ optionReads }: { optionReads: { count: number } }) {
  const form = useForm({
    schema: renderedSchema,
    initialInput: { id: "1", status: "", notes: "", conditional: "", selection: "", enabled: true },
  });
  const fields = resolvedFields([
    { name: "notes", type: "text", labelKey: "notes" },
    { name: "selection", type: "select", labelKey: "selection", options: ["First", "Second"] },
  ]);
  const selectionField = fields.find((field) => field.name === "selection");
  if (selectionField?.type !== "select") throw new Error("Expected a select field");
  Object.defineProperty(selectionField, "options", {
    get: () => {
      optionReads.count++;
      return ["First", "Second"];
    },
  });

  return <RenderFields form={form} fields={fields} />;
}

type SelectWidget = "card" | "radio" | "select";

function SelectWithDisabledOptionHarness({ widget }: { widget: SelectWidget }) {
  const form = useForm({
    schema: renderedSchema,
    initialInput: { id: "1", status: "", notes: "", conditional: "", selection: "", enabled: true },
  });
  const fields = resolvedFields([
    { name: "enabled", type: "boolean", labelKey: "enabled" },
    {
      name: "selection",
      type: "select",
      labelKey: "selection",
      options: ["Available", "Locked"],
      form: {
        widget,
        isOptionDisabled: (option, data) => option === "Locked" && !data.enabled,
      },
    },
  ]);

  return <RenderFields form={form} fields={fields} />;
}

describe("RenderFields form data reactivity", () => {
  it("keeps a conditional text field quiet for unrelated edits and follows its dependency", async () => {
    const user = userEvent.setup();
    const evaluations = { count: 0 };
    render(<ConditionalTextHarness evaluations={evaluations} />);

    const conditional = screen.getByRole("textbox", { name: "common:conditional" });
    const before = evaluations.count;
    await user.type(screen.getByRole("textbox", { name: "common:notes" }), "hello");
    expect(evaluations.count - before).toBe(0);
    expect(conditional).toBeInTheDocument();

    const status = screen.getByRole("textbox", { name: "common:status" });
    await user.clear(status);
    expect(screen.queryByRole("textbox", { name: "common:conditional" })).not.toBeInTheDocument();
    await user.type(status, "show");
    expect(screen.getByRole("textbox", { name: "common:conditional" })).toBeInTheDocument();
  });

  it("keeps a conditional row quiet for unrelated edits and follows its dependency", async () => {
    const user = userEvent.setup();
    const evaluations = { count: 0 };
    render(<ConditionalRowHarness evaluations={evaluations} />);

    const before = evaluations.count;
    await user.type(screen.getByRole("textbox", { name: "common:notes" }), "hello");
    expect(evaluations.count - before).toBe(0);
    expect(screen.getByRole("textbox", { name: "common:conditional" })).toBeInTheDocument();

    await user.clear(screen.getByRole("textbox", { name: "common:status" }));
    expect(screen.queryByRole("textbox", { name: "common:conditional" })).not.toBeInTheDocument();
  });

  it("does not re-render a select without option disabling when an unrelated field changes", async () => {
    const user = userEvent.setup();
    const optionReads = { count: 0 };
    render(<SelectWithoutDisabledHarness optionReads={optionReads} />);

    const before = optionReads.count;
    expect(before).toBeGreaterThan(0);
    await user.type(screen.getByRole("textbox", { name: "common:notes" }), "hello");
    expect(optionReads.count - before).toBe(0);
  });

  it.each(["radio", "card", "select"] as const)(
    "updates a %s select option's disabled state when its dependency changes",
    async (widget) => {
      const user = userEvent.setup();
      render(<SelectWithDisabledOptionHarness widget={widget} />);

      const enabled = screen.getByRole("checkbox", { name: "common:enabled" });
      const optionRole = widget === "select" ? "option" : "radio";
      const openOptions = async () => {
        if (widget === "select" && !screen.queryByRole("option", { name: "Locked" })) {
          await user.click(screen.getByRole("combobox", { name: "common:selection" }));
        }
      };

      await openOptions();
      expect(await screen.findByRole(optionRole, { name: "Locked" })).not.toHaveAttribute("aria-disabled", "true");

      await user.click(enabled);
      await openOptions();
      expect(await screen.findByRole(optionRole, { name: "Locked" })).toHaveAttribute("aria-disabled", "true");

      await user.click(enabled);
      await openOptions();
      expect(await screen.findByRole(optionRole, { name: "Locked" })).not.toHaveAttribute("aria-disabled", "true");
    },
  );
});
