import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { NuqsAdapter } from "nuqs/adapters/react";
import { useState } from "react";
import { fn } from "storybook/test";
import * as v from "valibot";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { usePersistedTableList, useTableList } from "./hooks/useTableList";
import { tableViewDescriptor } from "./persistence";
import { TableList } from "./TableList";
import type { FilterState, PageState, TableListVariant } from "./types";
import "./__tests__/i18n/tableListExamples";

export type ResearchRecord = v.InferOutput<typeof researchRecordSchema>;

export const researchRecordSchema = v.object({
  id: v.string(),
  title: v.string(),
  owner: v.string(),
  score: v.number(),
  enabled: v.boolean(),
  modifiedAt: v.string(),
});

export const storyConfig = resolveCollectionConfig<ResearchRecord>({
  slug: "research-records",
  idField: "id",
  labels: {
    singularKey: "tableListExamples:record",
    pluralKey: "tableListExamples:records",
    descriptionKey: "tableListExamples:description",
  },
  useAsTitle: "title",
  defaultColumns: ["title", "owner", "score", "enabled", "modifiedAt"],
  listSearchableFields: ["title", "owner"],
  pagination: { defaultLimit: 5, limits: [5, 10, 25] },
  defaultSort: [{ field: "modifiedAt", direction: "desc" }],
  fields: [
    { name: "id", type: "text", labelKey: "tableListExamples:fields.id", list: false },
    {
      name: "title",
      type: "text",
      labelKey: "tableListExamples:fields.title",
      list: { descriptionKey: "tableListExamples:fields.titleDescription" },
    },
    { name: "owner", type: "text", labelKey: "tableListExamples:fields.owner" },
    { name: "score", type: "number", labelKey: "tableListExamples:fields.score" },
    { name: "enabled", type: "boolean", labelKey: "tableListExamples:fields.enabled" },
    { name: "modifiedAt", type: "dateTime", labelKey: "tableListExamples:fields.modified" },
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
        onRowOpen={fn()}
        variant={variant}
      />
    </div>
  );
}

const meta = {
  title: "Components/Table List",
  component: ControlledLocalDemo,
  excludeStories: ["storyConfig", "storyRecords", "researchRecordSchema"],
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

const shareableTable = tableViewDescriptor(storyConfig, "discard", { parameterPrefix: "shareable" });

export const ShareableView: Story = {
  render: () => {
    function Demo() {
      const table = usePersistedTableList({
        config: storyConfig,
        dataSource: { type: "client", rows: storyRecords },
        features: { sorting: false, pagination: false },
        persistence: shareableTable,
      });
      return (
        <div className="mx-auto max-w-7xl p-8">
          <TableList {...table.tableProps} />
        </div>
      );
    }
    return <Demo />;
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
