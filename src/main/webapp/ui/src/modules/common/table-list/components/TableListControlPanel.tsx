import type { ResolvedCollectionConfig } from "@/modules/common/collection/config";
import type { RuntimeFieldCatalogForNamespace, RuntimeFieldDefinition } from "../api-v2/runtimeFieldCatalog";
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
  runtimeFieldDefinitions?: readonly RuntimeFieldCatalogForNamespace[];
  onClose: () => void;
}) {
  const { filtering, sorting, columns, pagination } = features;
  const panel = (() => {
    if (activePanel === "filters" && filtering !== false)
      return (
        <TableListFilters
          key={JSON.stringify(filtering.value.expression)}
          config={config}
          expression={filtering.value.expression}
          visibleFields={columns === false ? undefined : columns.value}
          onShowColumn={
            columns === false
              ? undefined
              : (field, shown) => {
                  const current = columns.value;
                  const name = field as (typeof current)[number];
                  const next = shown
                    ? current.includes(name)
                      ? current
                      : [...current, name]
                    : current.filter((candidate) => candidate !== name);
                  if (next !== current) columns.onChange(next);
                }
          }
          onSelectRuntimeField={onSelectRuntimeField}
          runtimeFieldDefinitions={runtimeFieldDefinitions}
          runtimeFieldAuthScope={runtimeFieldAuthScope}
          onApply={(expression) => {
            filtering.onChange({ ...filtering.value, expression });
            if (pagination !== false) pagination.onChange({ ...pagination.value, pageIndex: 0 });
            onClose();
          }}
          onClose={onClose}
        />
      );
    if (activePanel === "columns" && columns !== false)
      return (
        <TableListColumns
          config={config}
          visibleFields={columns.value}
          onChange={columns.onChange}
          onSelectRuntimeField={onSelectRuntimeField}
          runtimeFieldDefinitions={runtimeFieldDefinitions}
          runtimeFieldAuthScope={runtimeFieldAuthScope}
          onClose={onClose}
        />
      );
    if (activePanel === "sorting" && sorting !== false)
      return (
        <TableListSorting
          config={config}
          sorting={sorting.value}
          onChange={(next) => {
            sorting.onChange(next);
            if (pagination !== false) pagination.onChange({ ...pagination.value, pageIndex: 0 });
          }}
          onClose={onClose}
        />
      );
    return null;
  })();
  return panel ? <div className="border-b py-3">{panel}</div> : null;
}
