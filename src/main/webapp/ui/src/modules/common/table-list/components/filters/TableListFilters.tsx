import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { restrictToVerticalAxis } from "@dnd-kit/modifiers";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { PlusIcon, Trash2Icon, XIcon } from "lucide-react";
import { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type {
  FilterOperator,
  ResolvedCollectionConfig,
  ResolvedFieldConfig,
  SearchSelector,
} from "@/modules/common/collection/collectionConfig";
import { fieldLabel } from "@/modules/common/collection/collectionConfig";
import { Button } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import type { RuntimeFieldDefinition } from "../../adapters/apiV2/runtimeFieldCatalog";
import type { FilterExpression, FilterValue } from "../../tableListState";
import { CustomFieldPicker, groupRuntimeFieldSources } from "./CustomFieldPicker";
import { FilterSelect, type FilterSelectOption } from "./FilterSelect";
import { FilterValueInput } from "./FilterValueInput";
import { SortableFilterRow } from "./SortableFilterRow";

type DraftRule<TDocument> = {
  id: number;
  field: SearchSelector<TDocument>;
  operator: FilterOperator;
  value: string | readonly string[];
};

const operatorKeys: Record<FilterOperator, `tableList.filters.operators.${FilterOperator}`> = {
  equals: "tableList.filters.operators.equals",
  notEquals: "tableList.filters.operators.notEquals",
  greaterThan: "tableList.filters.operators.greaterThan",
  greaterThanOrEqual: "tableList.filters.operators.greaterThanOrEqual",
  lessThan: "tableList.filters.operators.lessThan",
  lessThanOrEqual: "tableList.filters.operators.lessThanOrEqual",
  in: "tableList.filters.operators.in",
  notIn: "tableList.filters.operators.notIn",
  contains: "tableList.filters.operators.contains",
  matches: "tableList.filters.operators.matches",
  exists: "tableList.filters.operators.exists",
};

function comparisonNodes<TDocument>(expression: FilterExpression<TDocument> | null): readonly DraftRule<TDocument>[] {
  if (!expression) return [];
  const nodes = expression.kind === "and" ? expression.children : [expression];
  return nodes.flatMap((node, index) =>
    node.kind === "comparison"
      ? [{ id: index + 1, field: node.field, operator: node.operator, value: draftValue(node.value) }]
      : [],
  );
}

function draftValue(value: FilterValue): string | readonly string[] {
  if (Array.isArray(value)) return value.map((item) => (item instanceof Date ? item.toISOString() : String(item)));
  if (value instanceof Date) return value.toISOString();
  return String(value);
}

function scalarValue(value: DraftRule<never>["value"]): string {
  return typeof value === "string" ? value : (value[0] ?? "");
}

function listValue(value: DraftRule<never>["value"]): readonly string[] {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? [] : [trimmed];
}

function valueForOperator(
  value: DraftRule<never>["value"],
  previous: FilterOperator,
  next: FilterOperator,
): DraftRule<never>["value"] {
  const previousUsesMultipleValues = previous === "in" || previous === "notIn";
  const nextUsesMultipleValues = next === "in" || next === "notIn";
  if (next === "exists") return "true";
  if (previous === "exists") return nextUsesMultipleValues ? [] : "";
  if (nextUsesMultipleValues && !previousUsesMultipleValues) {
    const current = scalarValue(value);
    return current.trim() === "" ? [] : [current];
  }
  if (!nextUsesMultipleValues && previousUsesMultipleValues) return scalarValue(value);
  return value;
}

function parsedValue<TDocument>(rule: DraftRule<TDocument>, field: ResolvedFieldConfig<TDocument>) {
  if (rule.operator === "exists") return scalarValue(rule.value) !== "false";
  const parse = (value: string): string | number | boolean => {
    if (field.type === "number") return Number(value);
    if (field.type === "boolean") return value === "true";
    return value;
  };
  if (rule.operator === "in" || rule.operator === "notIn") {
    return listValue(rule.value).map(parse);
  }
  return parse(scalarValue(rule.value).trim());
}

/**
 */
const CUSTOM_FIELD_SENTINEL = "\u0000customField";

function sentinelFor(namespace: string): string {
  return `${CUSTOM_FIELD_SENTINEL}:${namespace}`;
}

function namespaceForSentinel(value: string): string | null {
  return value.startsWith(`${CUSTOM_FIELD_SENTINEL}:`) ? value.slice(CUSTOM_FIELD_SENTINEL.length + 1) : null;
}

function isActive<TDocument>(rule: DraftRule<TDocument>): boolean {
  if (rule.operator === "in" || rule.operator === "notIn") {
    return listValue(rule.value).length > 0;
  }
  return rule.operator === "exists" || scalarValue(rule.value).trim() !== "";
}

function defaultOperator<TDocument>(field: ResolvedFieldConfig<TDocument>): FilterOperator {
  return field.capabilities.filterOperators.includes("contains")
    ? "contains"
    : (field.capabilities.filterOperators[0] ?? "equals");
}

export function topLevelFilterCount<TDocument>(expression: FilterExpression<TDocument> | null): number {
  if (!expression) return 0;
  return expression.kind === "and" ? expression.children.length : 1;
}

function fieldGroups<TDocument>(
  fields: readonly ResolvedFieldConfig<TDocument>[],
): readonly { key: string; labelKey: string | null; fields: readonly ResolvedFieldConfig<TDocument>[] }[] {
  const groups = new Map<string, { key: string; labelKey: string | null; fields: ResolvedFieldConfig<TDocument>[] }>();
  for (const field of fields) {
    const labelKey = field.origin?.groupLabelKey ?? null;
    const key = labelKey ?? "";
    const group = groups.get(key) ?? { key, labelKey, fields: [] };
    group.fields.push(field);
    groups.set(key, group);
  }
  return [...groups.values()];
}

function fieldOptionLabel<TDocument>(
  field: ResolvedFieldConfig<TDocument>,
  translate: (key: string) => string,
): string {
  const label = fieldLabel(field, translate);
  const source = field.origin?.sourceLabel;
  return source ? `${label} (${source})` : label;
}

export function TableListFilters<TDocument>({
  config,
  expression,
  visibleFields,
  onApply,
  onShowColumn,
  onSelectRuntimeField,
  runtimeFieldDefinitions,
  onClose,
}: {
  config: ResolvedCollectionConfig<TDocument>;
  expression: FilterExpression<TDocument> | null;
  visibleFields?: readonly SearchSelector<TDocument>[];
  onApply: (expression: FilterExpression<TDocument> | null) => void;
  onShowColumn?: (field: SearchSelector<TDocument>, shown: boolean) => void;
  onSelectRuntimeField?: (namespace: string, definition: RuntimeFieldDefinition) => void;
  runtimeFieldDefinitions?: readonly {
    namespace: string;
    definitions: readonly RuntimeFieldDefinition[];
  }[];
  onClose: () => void;
}) {
  const { i18n, t } = useTranslation("common");
  const fields = useMemo(
    () =>
      // A filter-only field is hidden from the columns but must still be offered here. That is how
      // a selector derived from a relationship target, such as target.name, reaches the panel.
      config.fields.filter(
        (field) => field.capabilities.filterOperators.length > 0 && (field.list !== false || field.form === false),
      ),
    [config.fields],
  );
  const groups = useMemo(() => fieldGroups(fields), [fields]);
  const runtimeSources = useMemo(
    () =>
      onSelectRuntimeField === undefined ? [] : (config.runtimeSources ?? []).filter((source) => source.filterable),
    [config.runtimeSources, onSelectRuntimeField],
  );
  const runtimeSourceGroups = useMemo(() => groupRuntimeFieldSources(runtimeSources), [runtimeSources]);
  const shownColumns = useMemo(() => new Set<string>((visibleFields ?? []).map(String)), [visibleFields]);
  const offersColumns = onShowColumn !== undefined && visibleFields !== undefined;
  const rowColumns = offersColumns
    ? "sm:grid-cols-[1.5rem_5rem_minmax(10rem,1.3fr)_minmax(7.5rem,auto)_minmax(10rem,1.3fr)_auto_2rem]"
    : "sm:grid-cols-[1.5rem_5rem_minmax(10rem,1.3fr)_minmax(7.5rem,auto)_minmax(10rem,1.3fr)_2rem]";
  const [rules, setRules] = useState<readonly DraftRule<TDocument>[]>(() => comparisonNodes(expression));
  const nextId = useRef(Math.max(0, ...rules.map((rule) => rule.id)) + 1);
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 5 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  // A server-published target title is already display text. Only treat configured semantic keys
  // as i18n keys; otherwise a composed label such as "Bookable item: Name" is misread as a
  // namespace-qualified key and loses its relationship prefix.
  const translate = (key: string) => (i18n.exists(key) ? t(key as never) : key);
  const fieldOptions: FilterSelectOption[] = groups.flatMap((group) =>
    group.fields
      .filter((candidate) => candidate.origin?.namespace === undefined)
      .map((candidate) => ({
        value: String(candidate.name),
        label: fieldOptionLabel(candidate, translate),
        groupLabelKey: group.labelKey,
      })),
  );
  for (const group of runtimeSourceGroups) {
    fieldOptions.push({
      value: sentinelFor(group.key),
      label:
        group.viaLabel === ""
          ? t("tableList.filters.customField.option")
          : t("tableList.filters.customField.optionVia", { via: group.viaLabel }),
      groupLabelKey: "tableList.fieldGroups.customFields",
    });
  }
  const fieldSelectLabels = {
    placeholder: t("tableList.filters.fieldSearch.placeholder"),
    noMatch: t("tableList.filters.fieldSearch.noMatch"),
    clear: t("tableList.filters.fieldSearch.clear"),
    trigger: t("tableList.filters.fieldSearch.trigger"),
  };
  const operatorSelectLabels = {
    placeholder: t("tableList.filters.operatorSearch.placeholder"),
    noMatch: t("tableList.filters.operatorSearch.noMatch"),
    clear: t("tableList.filters.operatorSearch.clear"),
    trigger: t("tableList.filters.operatorSearch.trigger"),
  };

  const updateRule = (id: number, update: Partial<DraftRule<TDocument>>) => {
    setRules((current) => current.map((rule) => (rule.id === id ? { ...rule, ...update } : rule)));
  };
  const reorderRules = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    setRules((current) => {
      const sourceIndex = current.findIndex((rule) => rule.id === active.id);
      const targetIndex = current.findIndex((rule) => rule.id === over.id);
      return sourceIndex < 0 || targetIndex < 0 ? current : arrayMove([...current], sourceIndex, targetIndex);
    });
  };

  return (
    <section
      id="table-list-control-panel"
      aria-labelledby="table-list-filter-title"
      className="rounded-sm border bg-popover p-4"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id="table-list-filter-title" className="font-heading text-sm font-medium">
            {t("tableList.filters.title")}
          </h2>
        </div>
        <Button aria-label={t("tableList.actions.closeFilters")} size="icon-xs" variant="ghost" onClick={onClose}>
          <XIcon aria-hidden="true" />
        </Button>
      </div>

      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        modifiers={[restrictToVerticalAxis]}
        autoScroll
        onDragEnd={reorderRules}
      >
        <SortableContext items={rules.map((rule) => rule.id)} strategy={verticalListSortingStrategy}>
          <ol
            aria-label={t("tableList.filters.title")}
            className={cn("mt-4 grid max-h-72 list-none gap-x-2 gap-y-2 overflow-y-auto p-0", rowColumns)}
          >
            {rules.map((rule, index) => {
              const chosen = fields.find((candidate) => candidate.name === rule.field);
              const selectedNamespace = chosen?.origin?.namespace ?? null;
              const pickingGroup =
                namespaceForSentinel(String(rule.field)) ??
                (selectedNamespace === null
                  ? null
                  : (runtimeSourceGroups.find((group) =>
                      group.sources.some((source) => source.namespace === selectedNamespace),
                    )?.key ?? null));
              const sourceGroup = runtimeSourceGroups.find((candidate) => candidate.key === pickingGroup) ?? null;
              const field = chosen ?? fields[0];
              const awaitingDefinition = namespaceForSentinel(String(rule.field)) !== null;
              if (!field) return null;
              return (
                <SortableFilterRow
                  key={rule.id}
                  id={rule.id}
                  moveLabel={t("tableList.actions.moveFilter", { number: index + 1 })}
                >
                  <span className="text-xs font-medium text-muted-foreground">
                    {index === 0 ? t("tableList.filters.where") : t("tableList.filters.and")}
                  </span>
                  <FilterSelect
                    ariaLabel={t("tableList.filters.field", { number: index + 1 })}
                    options={fieldOptions}
                    labels={fieldSelectLabels}
                    value={pickingGroup === null ? String(rule.field) : sentinelFor(pickingGroup)}
                    onChange={(next) => {
                      const namespace = namespaceForSentinel(next);
                      if (namespace !== null) {
                        updateRule(rule.id, { field: sentinelFor(namespace) as SearchSelector<TDocument>, value: "" });
                        return;
                      }
                      const nextField = fields.find((candidate) => String(candidate.name) === next);
                      if (nextField)
                        updateRule(rule.id, { field: nextField.name, operator: defaultOperator(nextField), value: "" });
                    }}
                  />
                  {awaitingDefinition ? null : (
                    <FilterSelect
                      ariaLabel={t("tableList.filters.operator", { number: index + 1 })}
                      options={field.capabilities.filterOperators.map((operator) => ({
                        value: operator,
                        label: t(operatorKeys[operator]),
                        groupLabelKey: null,
                      }))}
                      value={rule.operator}
                      labels={operatorSelectLabels}
                      onChange={(next) => {
                        const operator = next as FilterOperator;
                        updateRule(rule.id, {
                          operator,
                          value: valueForOperator(rule.value, rule.operator, operator),
                        });
                      }}
                    />
                  )}
                  {awaitingDefinition ? null : (
                    <FilterValueInput
                      field={field}
                      fields={config.fields}
                      operator={rule.operator}
                      value={rule.value}
                      number={index + 1}
                      onChange={(value) => updateRule(rule.id, { value })}
                    />
                  )}
                  {offersColumns && field.list !== false && !awaitingDefinition ? (
                    <label className="flex items-center gap-1.5 text-xs whitespace-nowrap text-muted-foreground">
                      <input
                        type="checkbox"
                        className="size-3.5"
                        checked={shownColumns.has(String(field.name))}
                        onChange={(event) => onShowColumn?.(field.name, event.target.checked)}
                      />
                      {t("tableList.filters.alsoShowAsColumn")}
                    </label>
                  ) : null}
                  <Button
                    aria-label={t("tableList.actions.removeFilter", { number: index + 1 })}
                    size="icon-xs"
                    variant="ghost"
                    onClick={() => setRules((current) => current.filter((candidate) => candidate.id !== rule.id))}
                  >
                    <Trash2Icon aria-hidden="true" />
                  </Button>
                  {sourceGroup !== null && onSelectRuntimeField ? (
                    <div className="sm:col-start-3 sm:col-span-3">
                      <CustomFieldPicker
                        sources={sourceGroup.sources}
                        ariaLabel={
                          sourceGroup.viaLabel === ""
                            ? t("tableList.filters.customField.searchLabel", { number: index + 1 })
                            : t("tableList.filters.customField.searchLabelVia", {
                                number: index + 1,
                                via: sourceGroup.viaLabel,
                              })
                        }
                        hint={
                          awaitingDefinition
                            ? t("tableList.filters.customField.onlySelectionAttaches")
                            : t("tableList.filters.customField.attached")
                        }
                        chosenLabel={awaitingDefinition ? undefined : fieldLabel(field, translate)}
                        known={runtimeFieldDefinitions ?? []}
                        onSelect={(namespace, definition) => {
                          onSelectRuntimeField(namespace, definition);
                          const selector = `${namespace}.${definition.id}` as SearchSelector<TDocument>;
                          updateRule(rule.id, { field: selector, operator: "equals", value: "" });
                        }}
                      />
                    </div>
                  ) : null}
                </SortableFilterRow>
              );
            })}
          </ol>
        </SortableContext>
      </DndContext>

      <Button
        className="mt-3"
        size="sm"
        variant="ghost"
        disabled={fields.length === 0}
        onClick={() => {
          const field = fields[0];
          if (!field) return;
          setRules((current) => [
            ...current,
            { id: nextId.current++, field: field.name, operator: defaultOperator(field), value: "" },
          ]);
        }}
      >
        <PlusIcon aria-hidden="true" data-icon="inline-start" />
        {t("tableList.actions.addFilter")}
      </Button>

      <div className="mt-4 flex items-center justify-between gap-3 border-t pt-3">
        <Button
          size="sm"
          variant="ghost"
          disabled={rules.length === 0}
          // Clearing applies straight away: leaving the applied filters in place while the panel
          // shows none reads as a no-op.
          onClick={() => {
            setRules([]);
            onApply(null);
          }}
        >
          {t("tableList.actions.clearAll")}
        </Button>
        <Button
          size="sm"
          onClick={() => {
            const comparisons: FilterExpression<TDocument>[] = rules.filter(isActive).map((rule) => {
              const field = fields.find((candidate) => candidate.name === rule.field);
              if (!field) throw new Error(`Unknown filter field ${rule.field}`);
              return {
                kind: "comparison",
                field: rule.field,
                operator: rule.operator,
                value: parsedValue(rule, field),
              };
            });
            onApply(
              comparisons.length === 0
                ? null
                : comparisons.length === 1
                  ? comparisons[0]
                  : { kind: "and", children: comparisons },
            );
          }}
        >
          {t("tableList.actions.applyFilters")}
        </Button>
      </div>
    </section>
  );
}
