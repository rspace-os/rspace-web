import type { ResolvedCollectionConfig } from "@/modules/common/collection/collectionConfig";
import type { RuntimeFieldDefinition } from "../adapters/apiV2/runtimeFieldCatalog";
import type { TableListFeatures } from "../tableListState";
import { TableListColumns } from "./columns/TableListColumns";
import { TableListFilters } from "./filters/TableListFilters";
import { TableListSorting } from "./sorting/TableListSorting";
import type { TableListControlPanel as ControlPanel } from "./TableListToolbar";

export function TableListControlPanel<TDocument>({
  activePanel,
  config,
  features,
  onSelectRuntimeField,
  runtimeFieldDefinitions,
  onClose,
}: {
  activePanel: ControlPanel | null;
  config: ResolvedCollectionConfig<TDocument>;
  features: TableListFeatures<TDocument>;
  onSelectRuntimeField?: (namespace: string, definition: RuntimeFieldDefinition) => void;
  runtimeFieldDefinitions?: readonly {
    namespace: string;
    definitions: readonly RuntimeFieldDefinition[];
  }[];
  onClose: () => void;
}) {
  if (!activePanel) return null;

  return (
    <div className="border-b py-3">
      {activePanel === "filters" && features.filtering !== false ? (
        <TableListFilters
          config={config}
          expression={features.filtering.value.expression}
          visibleFields={features.columns === false ? undefined : features.columns.value}
          onShowColumn={
            features.columns === false
              ? undefined
              : (field, shown) => {
                  if (features.columns === false) return;
                  const current = features.columns.value;
                  const name = field as (typeof current)[number];
                  const next = shown
                    ? current.includes(name)
                      ? current
                      : [...current, name]
                    : current.filter((candidate) => candidate !== name);
                  if (next !== current) features.columns.onChange(next);
                }
          }
          onSelectRuntimeField={onSelectRuntimeField}
          runtimeFieldDefinitions={runtimeFieldDefinitions}
          onApply={(expression) => {
            features.filtering !== false && features.filtering.onChange({ ...features.filtering.value, expression });
            if (features.pagination !== false)
              features.pagination.onChange({ ...features.pagination.value, pageIndex: 0 });
            onClose();
          }}
          onClose={onClose}
        />
      ) : null}
      {activePanel === "columns" && features.columns !== false ? (
        <TableListColumns
          config={config}
          visibleFields={features.columns.value}
          onChange={features.columns.onChange}
          onSelectRuntimeField={onSelectRuntimeField}
          runtimeFieldDefinitions={runtimeFieldDefinitions}
          onClose={onClose}
        />
      ) : null}
      {activePanel === "sorting" && features.sorting !== false ? (
        <TableListSorting
          config={config}
          sorting={features.sorting.value}
          onChange={(sorting) => {
            features.sorting !== false && features.sorting.onChange(sorting);
            if (features.pagination !== false)
              features.pagination.onChange({ ...features.pagination.value, pageIndex: 0 });
          }}
          onClose={onClose}
        />
      ) : null}
    </div>
  );
}
