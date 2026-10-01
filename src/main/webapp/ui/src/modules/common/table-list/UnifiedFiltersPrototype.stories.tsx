import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NuqsAdapter } from "nuqs/adapters/react";
import { useState } from "react";
import { I18nextProvider } from "react-i18next";
import { expect, userEvent, within } from "storybook/test";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { queryKeys } from "@/modules/common/hooks/auth";
import i18n from "@/modules/common/i18n";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import commonMessages from "@/modules/common/i18n/locales/en-US/common.json";
import { createApiV2FilterFields } from "./adapters/apiV2/apiV2FilterFields";
import type { RuntimeFieldDefinition } from "./adapters/apiV2/runtimeFieldCatalog";
import { TableList } from "./TableList";
import type { FilterState } from "./tableListState";

// Storybook fixtures exercise the production filter panel and runtime catalog picker.
type Booking = {
  id: string;
  purpose: string;
  status: string;
  itemName: string;
  itemLocation: string;
  ownerName: string;
  ownerEmail: string;
  project: string;
  itemSerialNumber: string;
  itemCapacity: number;
  target?: { id: string; name: string };
  sample?: { id: string; name: string };
};

const config = resolveCollectionConfig<Booking>({
  slug: "unified-filters-prototype",
  idField: "id",
  labels: { singularKey: "tableList.examples.record", pluralKey: "tableList.examples.records" },
  useAsTitle: "purpose",
  defaultColumns: ["purpose", "status", "target", "sample", "ownerName"],
  fields: [
    ...(
      [
        ["target", "Bookable item", "instruments"],
        ["sample", "Sample", "samples"],
      ] as const
    ).map(([name, label, relationTo]) => ({
      name,
      labelKey: label,
      label,
      type: "relationship" as const,
      relationTo,
      hasMany: false,
      capabilities: { filterOperators: [], sortable: false },
      list: { renderCell: ({ row }: { row: Booking }) => row[name]?.name ?? "" },
    })),
    { name: "id", type: "text", labelKey: "ID", list: false },
    { name: "purpose", type: "text", labelKey: "Purpose", label: "Purpose" },
    {
      name: "status",
      type: "select",
      labelKey: "Status",
      label: "Status",
      options: ["Confirmed", "Pending", "Cancelled"],
    },
    ...(
      [
        ["itemName", "Name", "Bookable item"],
        ["itemLocation", "Location", "Bookable item"],
        ["ownerName", "Name", "Booked by"],
        ["ownerEmail", "Email", "Booked by"],
      ] as const
    ).map(([name, label, groupLabelKey]) => ({
      name,
      type: "text" as const,
      labelKey: label,
      label,
      origin: { kind: "relationshipTarget" as const, groupLabelKey },
    })),
    ...(
      [
        ["project", "Custom fields › Project", "Custom fields"],
        ["itemSerialNumber", "Bookable item › Custom fields › Serial number", "Bookable item · Custom fields"],
      ] as const
    ).map(([name, label, groupLabelKey]) => ({
      name,
      type: "text" as const,
      labelKey: label,
      label,
      origin: { kind: "runtimeField" as const, groupLabelKey },
    })),
    {
      name: "itemCapacity",
      type: "number",
      labelKey: "Capacity",
      label: "Bookable item › Custom fields › Capacity",
      origin: { kind: "runtimeField", groupLabelKey: "Bookable item · Custom fields" },
    },
  ],
});

const rows: Booking[] = [
  {
    id: "1",
    purpose: "Cell imaging",
    status: "Confirmed",
    itemName: "Confocal microscope",
    itemLocation: "Lab A",
    ownerName: "Maya Chen",
    ownerEmail: "maya@example.test",
    project: "Organoids",
    itemSerialNumber: "CM-001",
    itemCapacity: 4,
  },
  {
    id: "2",
    purpose: "Protein analysis",
    status: "Pending",
    itemName: "Mass spectrometer",
    itemLocation: "Lab B",
    ownerName: "Jon Bell",
    ownerEmail: "jon@example.test",
    project: "Proteomics",
    itemSerialNumber: "MS-042",
    itemCapacity: 12,
  },
  {
    id: "3",
    purpose: "Live cell time-lapse",
    status: "Confirmed",
    itemName: "Confocal microscope",
    itemLocation: "Lab A",
    ownerName: "Aisha Rahman",
    ownerEmail: "aisha@example.test",
    project: "Organoids",
    itemSerialNumber: "CM-001",
    itemCapacity: 4,
  },
  {
    id: "4",
    purpose: "Sample preparation",
    status: "Cancelled",
    itemName: "Centrifuge",
    itemLocation: "Lab B",
    ownerName: "Maya Chen",
    ownerEmail: "maya@example.test",
    project: "Proteomics",
    itemSerialNumber: "CF-019",
    itemCapacity: 24,
  },
];

// The same catalog shape consumed by CustomFieldPicker and useApiV2RuntimeFields.
// Duplicate labels deliberately retain their template/record source and stable ID.
// Mirrors ExtraFieldIdentity: exact name + declared type, shared across readable items.
function extraField(label: string, type: "text" | "number"): RuntimeFieldDefinition {
  const encoded = Array.from(new TextEncoder().encode(label), (byte) => byte.toString(16).padStart(2, "0")).join("");
  const id = `XF${type === "number" ? "n" : "t"}${encoded}`;
  return {
    id,
    selector: `extraFields.${id}`,
    label,
    type,
    jsonType: type === "number" ? "number" : "string",
    options: [],
    source: { id: "", label: "" },
    operators:
      type === "number"
        ? ["==", "!=", "=gt=", "=ge=", "=lt=", "=le=", "=in=", "=out=", "=exists="]
        : ["==", "!=", "=in=", "=out=", "=contains=", "=like=", "=exists="],
    supportsWildcards: type === "text",
    columnSelectable: true,
    sortable: false,
  };
}

function customField(
  id: string,
  label: string,
  type: RuntimeFieldDefinition["type"],
  options: readonly string[] = [],
  source = { id: "IT9", label: "Microscopy template" },
): RuntimeFieldDefinition {
  const operators: Record<RuntimeFieldDefinition["type"], RuntimeFieldDefinition["operators"]> = {
    text: ["==", "!=", "=in=", "=out=", "=contains=", "=like=", "=exists="],
    number: ["==", "!=", "=gt=", "=ge=", "=lt=", "=le=", "=in=", "=out=", "=exists="],
    date: ["==", "!=", "=gt=", "=ge=", "=lt=", "=le=", "=in=", "=out=", "=exists="],
    time: ["==", "!=", "=gt=", "=ge=", "=lt=", "=le=", "=in=", "=out=", "=exists="],
    radio: ["==", "!=", "=in=", "=out=", "=exists="],
    choice: ["=contains=", "=in=", "=exists="],
  };
  return {
    id,
    selector: `customFields.${id}`,
    label,
    type,
    jsonType: type === "number" ? "number" : type === "choice" ? "array" : "string",
    options: [...options],
    source,
    operators: operators[type],
    supportsWildcards: type === "text",
    columnSelectable: true,
    sortable: false,
  };
}

const serviceContact = extraField("Service contact", "text");
const sampleProject = extraField("Project", "text");
const numericVoltage = extraField("Voltage", "number");
const textVoltage = extraField("Voltage", "text");

const catalog: RuntimeFieldDefinition[] = [
  customField("SF104", "Hazard class", "radio", ["BSL-1", "BSL-2"]),
  customField("SF204", "Hazard class", "radio", ["BSL-1", "BSL-2"], {
    id: "IT11",
    label: "Analytical template",
  }),
  customField("SF108", "Operating temperature", "number"),
  customField("SF109", "Calibration date", "date"),
  customField("SF110", "Daily cutoff", "time"),
  customField("SF111", "Imaging modes", "choice", ["Brightfield", "Fluorescence", "Phase contrast"]),
  customField("SF112", "Safety notes", "text"),
];
catalog.push(serviceContact, numericVoltage, textVoltage);

const sampleCatalog: RuntimeFieldDefinition[] = [sampleProject];

// Each relationship owns its namespaces and catalog; identical labels never share selectors.
const relationships = [
  { via: "target", resource: "instruments", catalog },
  { via: "sample", resource: "samples", catalog: sampleCatalog },
];
const runtimeFieldDefinitions = relationships.flatMap(({ via, catalog }) =>
  ["customFields", "extraFields"]
    .filter((namespace) => catalog.some((definition) => definition.selector.startsWith(`${namespace}.`)))
    .map((namespace) => ({
      namespace: `${via}.${namespace}`,
      definitions: catalog.filter((definition) => definition.selector.startsWith(`${namespace}.`)),
    })),
);

const derived = createApiV2FilterFields<Booking>({
  config,
  metadata: {
    resourceName: "bookingConfigurations",
    fields: [],
    sorting: { fields: [], default: [], maximumFields: 5 },
    filtering: {
      selectors: {},
      limits: {
        maximumComparisons: 50,
        maximumLikeComparisons: 10,
        maximumNesting: 10,
        maximumArguments: 100,
        maximumWhereLength: 4096,
      },
    },
    pagination: { defaultLimit: 20, maximumLimit: 100 },
    runtimeFields: relationships.flatMap(({ via, resource }) =>
      ["customFields", "extraFields"]
        .filter((namespace) => runtimeFieldDefinitions.some((entry) => entry.namespace === `${via}.${namespace}`))
        .map((namespace) => ({
          namespace: `${via}.${namespace}`,
          catalog: `/api/v2/${resource}/fields/${namespace}`,
          responseField: `${via}.${namespace}`,
          via,
          viaResource: resource,
          filterable: true,
          columnSelectable: true,
          sortable: false,
          maximumProjections: 20,
          catalogDefaultLimit: 50,
          catalogMaximumLimit: 200,
          catalogMaximumIds: 50,
        })),
    ),
  },
  runtimeFields: runtimeFieldDefinitions,
});

const runtimeRows = rows.map((row) => ({
  ...row,
  target: { id: row.itemSerialNumber, name: row.itemName },
  sample: { id: `SA${row.id}`, name: `Sample ${row.id} · ${row.project}` },
  "target.customFields.SF104": row.itemName === "Confocal microscope" ? "BSL-2" : null,
  "target.customFields.SF204": row.itemName === "Mass spectrometer" ? "BSL-1" : null,
  "target.customFields.SF108": row.itemName === "Confocal microscope" ? 37 : null,
  "target.customFields.SF109": row.id === "2" ? "2026-08-15" : "2026-09-20",
  "target.customFields.SF110": row.id === "2" ? "18:30" : "17:00",
  "target.customFields.SF111":
    row.itemName === "Confocal microscope" ? ["Fluorescence", "Phase contrast"] : ["Brightfield"],
  "target.customFields.SF112": row.id === "2" ? "Requires induction training" : "Standard operating procedure",
  [`target.${serviceContact.selector}`]: row.itemName === "Confocal microscope" ? "Maya Chen" : "Jon Bell",
  [`target.${numericVoltage.selector}`]: row.id === "2" ? 110 : 230,
  [`target.${textVoltage.selector}`]: row.id === "2" ? "110 V AC" : "230 V AC",
  [`sample.${sampleProject.selector}`]: row.id === "3" ? "Cell atlas" : row.id === "2" ? "Organoids" : row.project,
}));

const prototypeI18n = i18n.cloneInstance({ forkResourceStore: true });

function UnifiedFiltersPrototype() {
  const [filters, setFilters] = useState<FilterState<Booking>>({ search: "", expression: null });
  const [selectedFields, setSelectedFields] = useState<string[]>([]);
  const [queryClient] = useState(() => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(queryKeys.oauthToken(true), "prototype-token");
    return client;
  });
  const unifiedConfig = {
    ...resolveCollectionConfig<Booking>({
      ...config,
      defaultColumns: config.defaultColumns.filter((name) => name !== "project"),
      fields: [
        ...config.fields.filter((field) => field.origin?.kind !== "runtimeField"),
        ...derived.runtimeFields
          .filter(({ field }) => selectedFields.includes(String(field.name)))
          .map(({ field }) => field),
      ],
    }),
    runtimeSources: derived.runtimeSources,
  };
  return (
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={prototypeI18n}>
        <main className="mx-auto max-w-7xl space-y-4 p-4 sm:p-8">
          <div>
            <h1 className="text-xl font-semibold">One Filter menu</h1>
            <p className="text-sm text-muted-foreground">
              Bookable item and Sample are independent relationship columns. Choose either relationship’s extra fields
              and search its catalog. Combine “Bookable item › Service contact = Maya Chen” with “Sample › Project =
              Organoids” to find Cell imaging. Each rule keeps its own relationship and field; all rules use AND. Custom
              fields are template definitions (try “Hazard class”). Extra fields match by name and type across items
              (try “Service contact” or the text and number versions of “Voltage”). The fixtures cover text, number,
              date, time, radio, and multiple-choice value controls.
            </p>
          </div>
          <TableList
            config={unifiedConfig}
            rows={runtimeRows}
            getRowId={(row) => row.id}
            clientSide
            queryString={false}
            runtimeFieldDefinitions={runtimeFieldDefinitions}
            runtimeFieldAuthScope="unified-filters-prototype"
            onSelectRuntimeField={(namespace, definition) => {
              const selector = `${namespace}.${definition.id}`;
              setSelectedFields((current) => (current.includes(selector) ? current : [...current, selector]));
            }}
            features={{
              filtering: { value: filters, onChange: setFilters },
              sorting: false,
              pagination: false,
              columns: false,
            }}
          />
          <details className="rounded-sm border p-3 text-sm">
            <summary className="cursor-pointer">Applied filter state</summary>
            <pre className="mt-3 overflow-auto text-xs">{JSON.stringify(filters, null, 2)}</pre>
          </details>
        </main>
      </I18nextProvider>
    </QueryClientProvider>
  );
}

const meta = {
  title: "Prototypes/Table List/Unified Filters",
  component: UnifiedFiltersPrototype,
  parameters: { layout: "fullscreen" },
  beforeEach: async () => {
    prototypeI18n.addResourceBundle("en-US", "common", commonMessages, true, true);
    await prototypeI18n.changeLanguage("en-US");
    prototypeI18n.addResource("en-US", "common", "tableList.filters.customField.option", "Extra fields");
    prototypeI18n.addResource("en-US", "common", "tableList.filters.customField.optionVia", "{via} → Extra fields");
    // Only fixture catalogs are mocked; production search/debounce/schema parsing run unchanged.
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (input, init) => {
      const url = new URL(input instanceof Request ? input.url : String(input), window.location.origin);
      const relationship = relationships.find(({ resource }) =>
        ["customFields", "extraFields"].some((path) => url.pathname === `/api/v2/${resource}/fields/${path}`),
      );
      if (!relationship) return originalFetch(input, init);
      const namespace = url.pathname.endsWith("/customFields") ? "customFields" : "extraFields";
      const term = (url.searchParams.get("search") ?? "").toLowerCase();
      const limit = Number(url.searchParams.get("limit") ?? 20);
      const fields = relationship.catalog.filter(
        (definition) =>
          definition.selector.startsWith(`${namespace}.`) &&
          `${definition.label} ${definition.source.label} ${definition.id}`.toLowerCase().includes(term),
      );
      return Response.json({
        fields: fields.slice(0, limit),
        totalFields: fields.length,
        hasMore: fields.length > limit,
        page: 1,
        limit,
      });
    };
    return () => {
      globalThis.fetch = originalFetch;
    };
  },
  decorators: [
    (Story) => (
      <NuqsAdapter>
        <I18nRoot namespaces={["common"]}>
          <Story />
        </I18nRoot>
      </NuqsAdapter>
    ),
  ],
} satisfies Meta<typeof UnifiedFiltersPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const page = within(canvasElement.ownerDocument.body);
    await userEvent.click(canvas.getByRole("button", { name: "Filters, none applied" }));
    await userEvent.click(canvas.getByRole("button", { name: "Add filter" }));
    await userEvent.click(canvas.getByRole("combobox", { name: "Field for filter 1" }));
    const bookableItemGroup = await page.findByRole("group", { name: "Bookable item" });
    await userEvent.click(within(bookableItemGroup).getByRole("option", { name: "Extra fields" }));
    await expect(canvas.queryByText(/Only choosing one attaches it to this rule/)).not.toBeInTheDocument();
    await userEvent.type(
      canvas.getByRole("combobox", { name: "Search Bookable item custom fields for filter 1" }),
      "Service",
    );
    await userEvent.click(await page.findByRole("option", { name: /Service contact.*text/ }));
    await expect(canvas.queryByText(/This rule filters on the target's field/)).not.toBeInTheDocument();
    const runtimeFieldPicker = canvas.getByRole("combobox", {
      name: "Search Bookable item custom fields for filter 1",
    });
    const operator = canvas.getByRole("combobox", { name: "Operator for filter 1" });
    const value = canvas.getByRole("textbox", { name: "Value for filter 1" });
    await expect(runtimeFieldPicker.getBoundingClientRect().top).toBeLessThan(operator.getBoundingClientRect().top);
    await expect(
      Math.abs(operator.getBoundingClientRect().top - value.getBoundingClientRect().top),
    ).toBeLessThanOrEqual(2);
    await userEvent.type(value, "Maya Chen");
    await userEvent.click(canvas.getByRole("button", { name: "Apply filters" }));
    await expect(canvas.getByText("Cell imaging", { exact: true })).toBeVisible();
    await expect(canvas.queryByText("Protein analysis", { exact: true })).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "Filters, 1 applied" }));
    await expect(canvas.getByRole("combobox", { name: "Field for filter 1" })).toHaveValue(
      "Bookable item → Extra fields",
    );
    await userEvent.click(canvas.getByRole("button", { name: "Add filter" }));
    await userEvent.click(canvas.getByRole("combobox", { name: "Field for filter 2" }));
    const sampleGroup = await page.findByRole("group", { name: "Sample" });
    await userEvent.click(within(sampleGroup).getByRole("option", { name: "Extra fields" }));
    await userEvent.type(canvas.getByRole("combobox", { name: "Search Sample custom fields for filter 2" }), "Project");
    await userEvent.click(await page.findByRole("option", { name: /Project.*text/ }));
    await userEvent.type(canvas.getByRole("textbox", { name: "Value for filter 2" }), "Organoids");
    await userEvent.click(canvas.getByRole("button", { name: "Apply filters" }));
    await expect(canvas.getByText("Cell imaging", { exact: true })).toBeVisible();
    await expect(canvas.queryByText("Live cell time-lapse", { exact: true })).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "Filters, 2 applied" }));
    await expect(canvas.getByRole("combobox", { name: "Field for filter 2" })).toHaveValue("Sample → Extra fields");
    await userEvent.click(canvas.getByRole("button", { name: "Remove filter 1" }));
    await userEvent.click(canvas.getByRole("button", { name: "Apply filters" }));
    await expect(canvas.getByText("Cell imaging", { exact: true })).toBeVisible();
    await expect(canvas.getByText("Protein analysis", { exact: true })).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "Filters, 1 applied" }));
    await userEvent.click(canvas.getByRole("button", { name: "Clear all" }));
    await expect(canvas.getByText("Protein analysis", { exact: true })).toBeVisible();
  },
};
export const Mobile: Story = {
  parameters: { viewport: { defaultViewport: "mobile1" } },
  decorators: [
    (Story) => (
      <div className="max-w-sm">
        <Story />
      </div>
    ),
  ],
};

export const CustomAndExtraFields: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const page = within(canvasElement.ownerDocument.body);
    await userEvent.click(canvas.getByRole("button", { name: "Filters, none applied" }));
    await userEvent.click(canvas.getByRole("button", { name: "Add filter" }));
    await userEvent.click(canvas.getByRole("combobox", { name: "Field for filter 1" }));
    await userEvent.click(
      within(await page.findByRole("group", { name: "Bookable item" })).getByRole("option", { name: "Extra fields" }),
    );
    await userEvent.type(
      canvas.getByRole("combobox", { name: "Search Bookable item custom fields for filter 1" }),
      "Hazard",
    );
    await expect(await page.findByRole("option", { name: /Hazard class.*Analytical template/ })).toBeVisible();
    await userEvent.click(await page.findByRole("option", { name: /Hazard class.*Microscopy template/ }));
    await userEvent.click(canvas.getByRole("combobox", { name: "Value for filter 1" }));
    await userEvent.click(await page.findByRole("option", { name: "BSL-2" }));
    await userEvent.click(canvas.getByRole("button", { name: "Add filter" }));
    await userEvent.click(canvas.getByRole("combobox", { name: "Field for filter 2" }));
    await userEvent.click(
      within(await page.findByRole("group", { name: "Bookable item" })).getByRole("option", { name: "Extra fields" }),
    );
    await userEvent.type(
      canvas.getByRole("combobox", { name: "Search Bookable item custom fields for filter 2" }),
      "Voltage",
    );
    await expect(await page.findByRole("option", { name: /Voltage.*text/ })).toBeVisible();
    await userEvent.click(await page.findByRole("option", { name: /Voltage.*number/ }));
    await userEvent.type(canvas.getByRole("spinbutton", { name: "Value for filter 2" }), "230");
    await expect(canvas.getByRole("combobox", { name: "Search Bookable item custom fields for filter 1" })).toHaveValue(
      "Hazard class (Microscopy template · SF104)",
    );
    await expect(canvas.getByRole("combobox", { name: "Search Bookable item custom fields for filter 2" })).toHaveValue(
      "Voltage (number)",
    );

    const addRuntimeFilter = async (number: number, option: RegExp) => {
      await userEvent.click(canvas.getByRole("button", { name: "Add filter" }));
      await userEvent.click(canvas.getByRole("combobox", { name: `Field for filter ${number}` }));
      await userEvent.click(
        within(await page.findByRole("group", { name: "Bookable item" })).getByRole("option", {
          name: "Extra fields",
        }),
      );
      await userEvent.click(
        canvas.getByRole("combobox", { name: `Search Bookable item custom fields for filter ${number}` }),
      );
      await userEvent.click(await page.findByRole("option", { name: option }));
    };

    await addRuntimeFilter(3, /Calibration date.*Microscopy template/);
    await expect(canvas.getByLabelText("Value for filter 3")).toHaveAttribute("type", "date");

    await addRuntimeFilter(4, /Daily cutoff.*Microscopy template/);
    await expect(canvas.getByLabelText("Value for filter 4")).toHaveAttribute("type", "time");

    await addRuntimeFilter(5, /Imaging modes.*Microscopy template/);
    await userEvent.click(canvas.getByRole("combobox", { name: "Value for filter 5" }));
    await expect(await page.findByRole("option", { name: "Fluorescence" })).toBeVisible();
    await userEvent.click(page.getByRole("option", { name: "Fluorescence" }));

    await addRuntimeFilter(6, /Safety notes.*Microscopy template/);
    await expect(canvas.getByRole("textbox", { name: "Value for filter 6" })).toHaveAttribute("type", "text");
  },
};
