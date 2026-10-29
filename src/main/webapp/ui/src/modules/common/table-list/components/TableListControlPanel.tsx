import type { ResolvedCollectionConfig } from "@/modules/common/collection/config";
import type { RuntimeFieldDefinition } from "../api-v2/runtimeFieldCatalog";
import type { TableListFeatures } from "../types";
import { TableListColumns } from "./TableListColumns";
import { TableListFilters } from "./TableListFilters";
import { TableListSorting } from "./TableListSorting";
import type { TableListControlPanel as ControlPanel } from "./TableListToolbar";

export function TableListControlPanel<TDocument>({
  activePanel,
  config,
  features,
  onSelectRuntimeField,
  runtimeFieldDefinitions,
  runtimeFieldAuthScope,
  onClose,
}: {
  activePanel: ControlPanel | null;
  config: ResolvedCollectionConfig<TDocument>;
  features: TableListFeatures<TDocument>;
  onSelectRuntimeField?: (namespace: string, definition: RuntimeFieldDefinition) => void;
  runtimeFieldAuthScope?: string | number;
  runtimeFieldDefinitions?: readonly {
    namespace: string;
    definitions: readonly RuntimeFieldDefinition[];
  }[];
  onClose: () => void;
}) {
  const panel = (() => {
    if (activePanel === "filters" && features.filtering !== false)
      return (
        <TableListFilters
          key={JSON.stringify(features.filtering.value.expression)}
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
          runtimeFieldAuthScope={runtimeFieldAuthScope}
          onApply={(expression) => {
            features.filtering !== false && features.filtering.onChange({ ...features.filtering.value, expression });
            if (features.pagination !== false)
              features.pagination.onChange({ ...features.pagination.value, pageIndex: 0 });
            onClose();
          }}
          onClose={onClose}
        />
      );
    if (activePanel === "columns" && features.columns !== false)
      return (
        <TableListColumns
          config={config}
          visibleFields={features.columns.value}
          onChange={features.columns.onChange}
          onSelectRuntimeField={onSelectRuntimeField}
          runtimeFieldDefinitions={runtimeFieldDefinitions}
          runtimeFieldAuthScope={runtimeFieldAuthScope}
          onClose={onClose}
        />
      );
    if (activePanel === "sorting" && features.sorting !== false)
      return (
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
      );
    return null;
  })();
  return panel ? <div className="border-b py-3">{panel}</div> : null;
}
