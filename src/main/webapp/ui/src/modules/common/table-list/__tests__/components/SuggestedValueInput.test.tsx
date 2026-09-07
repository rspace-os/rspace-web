import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { TableList } from "../../TableList";
import type { FilterState } from "../../tableListState";
import { chooseFilterField } from "./chooseFilterField";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: () => ({ data: "token" }),
}));

const relationshipOptions = vi.hoisted(() => vi.fn());

vi.mock("@/modules/common/relationship-picker/relationshipOptionQueries", () => ({
  useRelationshipOptions: (args: unknown) => relationshipOptions(args),
  useRelationshipOptionAvailability: () => ({ unavailable: {}, checking: false, failed: false }),
  useSelectedRelationshipOptions: () => [],
}));

type Booking = { id: string; target: string; "target.name": string; timeZone: string };

const config = resolveCollectionConfig<Booking>({
  slug: "bookingConfigurations",
  idField: "id",
  labels: { singularKey: "tableList.examples.record", pluralKey: "tableList.examples.records" },
  useAsTitle: "target",
  defaultColumns: ["target", "timeZone"],
  fields: [
    { name: "id", labelKey: "tableList.examples.fields.id", type: "text", list: false },
    {
      name: "target",
      labelKey: "tableList.examples.fields.owner",
      type: "relationship",
      relationTo: "instruments",
      hasMany: false,
    },
    { name: "target.name", labelKey: "Bookable item: Name", type: "text" },
    { name: "timeZone", labelKey: "tableList.examples.fields.notes", type: "text" },
  ],
});

const emptyFilters: FilterState<Booking> = { search: "", expression: null };

const rows: readonly Booking[] = [
  { id: "1", target: "IN1", "target.name": "Confocal microscope", timeZone: "Europe/Berlin" },
];

beforeEach(() => {
  relationshipOptions.mockReset();
  relationshipOptions.mockImplementation(({ enabled }: { enabled?: boolean }) => ({
    options: enabled
      ? [
          { value: "IN1", label: "Confocal microscope" },
          { value: "IN2", label: "Alpha scope" },
        ]
      : [],
    failed: false,
  }));
});

async function openFilterOn(field: string | RegExp) {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "common:tableList.filters.noneApplied" }));
  await user.click(screen.getByRole("button", { name: "common:tableList.actions.addFilter" }));
  await chooseFilterField(user, field);
  return user;
}

describe("filtering on a relationship target's field", () => {
  it("suggests names from the target collection while keeping the value free text", async () => {
    const { container } = render(
      <TableList
        queryString={false}
        config={config}
        rows={rows}
        getRowId={(row) => row.id}
        features={{
          filtering: { value: emptyFilters, onChange: vi.fn() },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );

    const user = await openFilterOn(/Bookable item/);
    expect(screen.getByRole("combobox", { name: "common:tableList.filters.field" })).toHaveValue("Bookable item: Name");
    const value = screen.getByRole("combobox", { name: "common:tableList.filters.value" });
    expect(screen.queryByText("Confocal microscope")).not.toBeInTheDocument();

    await user.type(value, "conf");

    expect(value).toHaveValue("conf");
    await waitFor(() => expect(screen.getByRole("option", { name: "Confocal microscope" })).toBeInTheDocument());
    expect(screen.getByRole("option", { name: "Alpha scope" })).toBeInTheDocument();

    await user.keyboard("{Escape}");
    await expectAccessible(container);
  });

  it("does not enable suggestions for a one-character term", async () => {
    render(
      <TableList
        queryString={false}
        config={config}
        rows={rows}
        getRowId={(row) => row.id}
        features={{
          filtering: { value: emptyFilters, onChange: vi.fn() },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );

    const user = await openFilterOn(/Bookable item/);
    await user.type(screen.getByRole("combobox", { name: "common:tableList.filters.value" }), "c");

    await waitFor(() =>
      expect(relationshipOptions).toHaveBeenLastCalledWith(expect.objectContaining({ term: "c", enabled: false })),
    );
  });

  it("leaves an ordinary field on the plain input", async () => {
    render(
      <TableList
        queryString={false}
        config={config}
        rows={rows}
        getRowId={(row) => row.id}
        features={{
          filtering: { value: emptyFilters, onChange: vi.fn() },
          sorting: false,
          pagination: false,
          columns: false,
        }}
      />,
    );

    await openFilterOn(/fields\.notes/);

    expect(screen.getByRole("textbox", { name: "common:tableList.filters.value" })).not.toHaveAttribute("list");
  });
});
