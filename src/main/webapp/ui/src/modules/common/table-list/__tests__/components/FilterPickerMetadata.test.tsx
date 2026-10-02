import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import type { RelationshipFilterPicker } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { FilterValueInput } from "../../components/filters/FilterValueInput";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: () => ({ data: "test-token" }),
}));

function fieldWithPicker(picker: RelationshipFilterPicker) {
  return resolveCollectionConfig<{ target: string }>({
    slug: "metadata-picker",
    idField: "target",
    useAsTitle: "target",
    labels: { singularKey: "item", pluralKey: "items" },
    defaultColumns: ["target"],
    fields: [{ name: "target", labelKey: "target", type: "text", filterPicker: picker }],
  }).fields[0];
}

describe("metadata-selected relationship filter controls", () => {
  it.each([
    { type: "text" as const, role: "textbox", inputType: "text" },
    { type: "number" as const, role: "spinbutton", inputType: "number" },
    { type: "dateTime" as const, role: null, inputType: "datetime-local" },
  ])("uses a $type input for a relationship scalar field", ({ type, role, inputType }) => {
    const config = resolveCollectionConfig<{ target: string; "target.name": string }>({
      slug: "related-scalars",
      idField: "target",
      useAsTitle: "target",
      labels: { singularKey: "item", pluralKey: "items" },
      defaultColumns: ["target"],
      fields: [
        { name: "target", labelKey: "target", type: "relationship", relationTo: "instruments", hasMany: false },
        { name: "target.name", labelKey: "name", type },
      ],
    });
    render(
      <QueryClientProvider client={new QueryClient()}>
        <FilterValueInput field={config.fields[1]} operator="equals" value="" number={1} onChange={vi.fn()} />
      </QueryClientProvider>,
    );
    const input =
      role === null
        ? screen.getByLabelText("common:tableList.filters.value")
        : screen.getByRole(role, { name: "common:tableList.filters.value" });
    expect(input).toHaveAttribute("type", inputType);
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });

  it("uses the published source for an identity field without page-specific relationship wiring", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    server.use(
      http.get("/api/v2/instruments", () =>
        HttpResponse.json({
          docs: [{ id: 123, name: "Metadata microscope", globalId: "IN123" }],
        }),
      ),
    );
    const field = fieldWithPicker({ resource: "instruments", identity: "globalId", globalIdPrefix: "IN" });
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <FilterValueInput field={field} operator="equals" value="" number={1} onChange={onChange} />
      </QueryClientProvider>,
    );
    await user.type(screen.getByRole("combobox", { name: "common:tableList.filters.value" }), "Meta");
    await user.click(await screen.findByRole("option", { name: /Metadata microscope/ }));
    expect(onChange).toHaveBeenCalledWith("IN123");
    await expectAccessible(document.body);
  });

  it.each([
    { resource: "unregistered", identity: "globalId" as const, globalIdPrefix: "IN" },
    { resource: "instruments", identity: "globalId" as const, globalIdPrefix: "XX" },
  ])("keeps typed saved values when the source is unavailable or incompatible: $resource/$globalIdPrefix", (picker) => {
    const field = fieldWithPicker(picker);
    render(<FilterValueInput field={field} operator="equals" value="IN123" number={1} onChange={vi.fn()} />);
    expect(screen.getByRole("textbox", { name: "common:tableList.filters.value" })).toHaveValue("IN123");
    expect(screen.queryByRole("button", { name: "common:relationshipPicker.openOptions" })).not.toBeInTheDocument();
  });
});
