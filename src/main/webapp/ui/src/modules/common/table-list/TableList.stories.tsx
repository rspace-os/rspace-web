import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { NuqsAdapter } from "nuqs/adapters/react";
import { useState } from "react";
import { expect, fn, userEvent, within } from "storybook/test";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { TableList } from "./TableList";
import type { FilterState, PageState, TableListVariant } from "./tableListState";
import { useTableList } from "./useTableList";

export type ResearchRecord = {
  id: string;
  title: string;
  owner: string;
  score: number;
  enabled: boolean;
  modifiedAt: string;
};

export const storyConfig = resolveCollectionConfig<ResearchRecord>({
  slug: "research-records",
  idField: "id",
  labels: {
    singularKey: "tableList.examples.record",
    pluralKey: "tableList.examples.records",
    descriptionKey: "tableList.examples.description",
  },
  useAsTitle: "title",
  defaultColumns: ["title", "owner", "score", "enabled", "modifiedAt"],
  listSearchableFields: ["title", "owner"],
  pagination: { defaultLimit: 5, limits: [5, 10, 25] },
  defaultSort: [{ field: "modifiedAt", direction: "desc" }],
  fields: [
    { name: "id", type: "text", labelKey: "tableList.examples.fields.id", list: false },
    {
      name: "title",
      type: "text",
      labelKey: "tableList.examples.fields.title",
      list: { descriptionKey: "tableList.examples.fields.titleDescription" },
    },
    { name: "owner", type: "text", labelKey: "tableList.examples.fields.owner" },
    { name: "score", type: "number", labelKey: "tableList.examples.fields.score" },
    { name: "enabled", type: "boolean", labelKey: "tableList.examples.fields.enabled" },
    { name: "modifiedAt", type: "dateTime", labelKey: "tableList.examples.fields.modified" },
  ],
});

export const storyRecords: readonly ResearchRecord[] = [
  {
    id: "RS-1042",
    title: "Organoid culture optimization",
    owner: "Maya Chen",
    score: 92,
    enabled: true,
    modifiedAt: "2026-08-04",
  },
  {
    id: "RS-1038",
    title: "Flow cytometry gating protocol",
    owner: "Jon Bell",
    score: 88,
    enabled: true,
    modifiedAt: "2026-08-03",
  },
  {
    id: "RS-1031",
    title: "Pilot RNA-seq results",
    owner: "Aisha Rahman",
    score: 76,
    enabled: false,
    modifiedAt: "2026-08-02",
  },
  {
    id: "RS-1027",
    title: "CRISPR screen notes",
    owner: "Maya Chen",
    score: 84,
    enabled: true,
    modifiedAt: "2026-08-01",
  },
  {
    id: "RS-1022",
    title: "Western blot transfer protocol",
    owner: "Lucas Meyer",
    score: 69,
    enabled: true,
    modifiedAt: "2026-07-31",
  },
  {
    id: "RS-1018",
    title: "Metabolomics batch 07",
    owner: "Aisha Rahman",
    score: 95,
    enabled: false,
    modifiedAt: "2026-07-30",
  },
];

const defaultFilters: FilterState<ResearchRecord> = { search: "", expression: null };
const defaultPage: PageState = { pageIndex: 0, pageSize: 5 };

function ControlledLocalDemo({
  rows = storyRecords,
  status = "idle",
  variant,
}: {
  rows?: readonly ResearchRecord[];
  status?: "idle" | "loading" | "refreshing" | "error";
  variant?: TableListVariant;
}) {
  const table = useTableList({
    config: storyConfig,
    dataSource: { type: "client", rows },
  });
  return (
    <div className="mx-auto max-w-7xl p-8">
      <TableList
        {...table.tableProps}
        status={status}
        error={status === "error" ? new Error("The mock backend rejected this request.") : undefined}
        onCreate={fn()}
        onRowOpen={fn()}
        variant={variant}
      />
    </div>
  );
}

const meta = {
  title: "Components/Table List",
  component: ControlledLocalDemo,
  excludeStories: ["storyConfig", "storyRecords"],
  tags: ["autodocs"],
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <NuqsAdapter>
        <I18nRoot namespaces={["common"]}>
          <Story />
        </I18nRoot>
      </NuqsAdapter>
    ),
  ],
} satisfies Meta<typeof ControlledLocalDemo>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = { render: () => <ControlledLocalDemo /> };

export const Transparent: Story = { render: () => <ControlledLocalDemo variant="transparent" /> };

export const ShareableView: Story = {
  render: () => {
    function Demo() {
      const [filters, setFilters] = useState(defaultFilters);
      const [visibleFields, setVisibleFields] = useState(storyConfig.defaultColumns);
      return (
        <div className="mx-auto max-w-7xl p-8">
          <TableList
            config={storyConfig}
            rows={storyRecords}
            getRowId={(row) => row.id}
            clientSide
            queryString
            features={{
              filtering: { value: filters, onChange: setFilters },
              sorting: false,
              pagination: false,
              columns: { value: visibleFields, onChange: setVisibleFields },
            }}
          />
        </div>
      );
    }
    return <Demo />;
  },
};

export const ControlledLocalData: Story = {
  render: () => <ControlledLocalDemo />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.type(canvas.getByRole("textbox", { name: "Search Research records" }), "Organoid");
    await expect(canvas.getByRole("button", { name: "Organoid culture optimization" })).toBeVisible();
    await expect(canvas.queryByRole("button", { name: "Flow cytometry gating protocol" })).not.toBeInTheDocument();
  },
};

export const FeaturesDisabled: Story = {
  render: () => (
    <div className="mx-auto max-w-7xl p-8">
      <TableList
        config={storyConfig}
        rows={storyRecords}
        getRowId={(row) => row.id}
        features={{ filtering: false, sorting: false, pagination: false, columns: false }}
      />
    </div>
  ),
};

export const LoadingAndRefreshing: Story = {
  render: () => (
    <div className="grid gap-10 p-8 xl:grid-cols-2">
      <ControlledLocalDemo status="loading" />
      <ControlledLocalDemo status="refreshing" />
    </div>
  ),
};

export const EmptyAndError: Story = {
  render: () => (
    <div className="grid gap-10 p-8 xl:grid-cols-2">
      <ControlledLocalDemo rows={[]} />
      <ControlledLocalDemo rows={[]} status="error" />
    </div>
  ),
};

export const FieldTypes: Story = { render: () => <ControlledLocalDemo /> };

export const CardsWithFullWidthField: Story = {
  render: () => (
    <div className="mx-auto max-w-xl p-8">
      <TableList
        config={storyConfig}
        rows={storyRecords.slice(0, 2)}
        getRowId={(row) => row.id}
        clientSide
        features={{ filtering: false, sorting: false, pagination: false, columns: false }}
        presentations={{ table: false, cards: "all" }}
        uiColumns={[
          {
            id: "summary",
            label: "Summary",
            card: { fullWidth: true },
            renderCell: (row) => `${row.owner} last modified this record on ${row.modifiedAt}.`,
          },
        ]}
      />
    </div>
  ),
};

export const CustomBackendCallbacks: Story = {
  render: () => {
    function Demo() {
      const [request, setRequest] = useState("No request yet");
      return (
        <div className="mx-auto max-w-7xl p-8">
          <TableList
            config={storyConfig}
            rows={storyRecords.slice(0, 5)}
            getRowId={(row) => row.id}
            features={{
              filtering: { value: defaultFilters, onChange: (filters) => setRequest(JSON.stringify({ filters })) },
              sorting: { value: [], onChange: (sorting) => setRequest(JSON.stringify({ sorting })) },
              pagination: {
                value: defaultPage,
                rowCount: 40,
                onChange: (page) => setRequest(JSON.stringify({ page })),
              },
              columns: {
                value: storyConfig.defaultColumns,
                onChange: (columns) => setRequest(JSON.stringify({ columns })),
              },
            }}
          />
          <output className="mt-4 block rounded-sm bg-muted p-3 text-xs">{request}</output>
        </div>
      );
    }
    return <Demo />;
  },
};
