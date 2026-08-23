import { useState } from "react";
import type { FieldName, SortRule } from "@/modules/common/collection/collectionConfig";
import { TableList } from "./TableList";
import { type ResearchRecord, storyConfig, storyRecords } from "./TableList.stories";
import type { FilterState, PageState } from "./tableListState";

const initialFilters: FilterState<ResearchRecord> = { search: "", expression: null };

function ResponsiveDemo() {
  const [narrow, setNarrow] = useState(false);
  const [filters, setFilters] = useState(initialFilters);
  const [sorting, setSorting] = useState<readonly SortRule<ResearchRecord>[]>(storyConfig.defaultSort ?? []);
  const [page, setPage] = useState<PageState>({ pageIndex: 0, pageSize: 2 });
  const [columns, setColumns] = useState<readonly FieldName<ResearchRecord>[]>(storyConfig.defaultColumns);

  return (
    <main className="min-h-screen space-y-4 bg-background p-4 text-foreground">
      <button
        type="button"
        className="rounded-sm border bg-background px-3 py-2 text-sm"
        onClick={() => setNarrow((current) => !current)}
      >
        {narrow ? "Use wide container" : "Use narrow container"}
      </button>
      <output aria-label="TableList container width" className="ml-3 text-sm">
        {narrow ? "520px" : "900px"}
      </output>
      <div style={{ width: narrow ? 520 : 900, maxWidth: "100%" }}>
        <TableList
          queryString={false}
          config={storyConfig}
          rows={storyRecords}
          getRowId={(row) => row.id}
          clientSide
          presentations={{ table: "wide", cards: "narrow" }}
          features={{
            filtering: { value: filters, onChange: setFilters },
            sorting: { value: sorting, onChange: setSorting },
            pagination: { value: page, rowCount: storyRecords.length, onChange: setPage },
            columns: { value: columns, onChange: setColumns },
          }}
        />
      </div>
    </main>
  );
}

export function TableListResponsiveStory() {
  return <ResponsiveDemo />;
}
