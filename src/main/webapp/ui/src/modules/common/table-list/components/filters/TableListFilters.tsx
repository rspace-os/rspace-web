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
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVerticalIcon, PlusIcon, Trash2Icon, XIcon } from "lucide-react";
import { type CSSProperties, type ReactNode, Suspense, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type {
  FilterOperator,
  ResolvedCollectionConfig,
  ResolvedFieldConfig,
  SearchSelector,
} from "@/modules/common/collection/collectionConfig";
import { RelationshipPicker } from "@/modules/common/relationship-picker/RelationshipPicker";
import { type RelationshipSource, relationshipSources } from "@/modules/common/relationship-picker/relationshipSources";
import { Button } from "@/modules/common/ui/button";
import { Input } from "@/modules/common/ui/input";
import { MultiSelect } from "@/modules/common/ui/multi-select";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { cn } from "@/modules/common/utils/cn";
import type { FilterExpression, FilterValue } from "../../tableListState";
import { TargetFieldValueInput } from "./TargetFieldValueInput";

type DraftRule<TDocument> = {
  id: number;
  field: SearchSelector<TDocument>;
  operator: FilterOperator;
  value: string | readonly string[];
};

function SortableFilterRow({ id, moveLabel, children }: { id: number; moveLabel: string; children: ReactNode }) {
  const { attributes, isDragging, listeners, setNodeRef, transform, transition } = useSortable({ id });
  const style: CSSProperties = {
    transform: CSS.Translate.toString(transform),
    transition,
    zIndex: isDragging ? 1 : undefined,
  };
  return (
    <li
      ref={setNodeRef}
      style={style}
      className={cn(
        "grid items-center gap-2 rounded-sm border bg-background p-2 sm:grid-cols-[1.5rem_5rem_minmax(7rem,0.8fr)_minmax(11rem,1fr)_minmax(9rem,1fr)_2rem]",
        isDragging && "opacity-60 shadow-sm",
      )}
    >
      <button
        type="button"
        aria-label={moveLabel}
        className="flex size-6 touch-none cursor-grab items-center justify-center rounded-sm text-muted-foreground hover:bg-muted active:cursor-grabbing"
        {...attributes}
        {...listeners}
      >
        <GripVerticalIcon aria-hidden="true" className="size-3" />
      </button>
      {children}
    </li>
  );
}

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
 * The relationship source behind a filter named `<relationship>.<field>`, when the collection
 * declares that relationship and the picker knows its target. Returns null otherwise, so an
 * ordinary field with a dot in its name still renders as plain text.
 */
function targetFieldSource<TDocument>(
  name: string,
  fields: readonly ResolvedFieldConfig<TDocument>[],
): RelationshipSource | null {
  const dot = name.indexOf(".");
  if (dot <= 0) return null;
  const owner = fields.find((candidate) => candidate.name === name.slice(0, dot));
  if (owner?.type !== "relationship") return null;
  return relationshipSources[owner.relationTo] ?? null;
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

export function TableListFilters<TDocument>({
  config,
  expression,
  onApply,
  onClose,
}: {
  config: ResolvedCollectionConfig<TDocument>;
  expression: FilterExpression<TDocument> | null;
  onApply: (expression: FilterExpression<TDocument> | null) => void;
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
  const multiSelectLabels = {
    selectPlaceholder: t("tableList.filters.multiSelect.selectPlaceholder"),
    customPlaceholder: t("tableList.filters.multiSelect.customPlaceholder"),
    empty: t("tableList.filters.multiSelect.empty"),
    enterValue: t("tableList.filters.multiSelect.enterValue"),
    remove: (value: string) =>
      t("tableList.filters.multiSelect.remove", {
        value,
      }),
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
            className="mt-4 max-h-72 list-none space-y-2 overflow-y-auto p-0"
          >
            {rules.map((rule, index) => {
              const field = fields.find((candidate) => candidate.name === rule.field) ?? fields[0];
              if (!field) return null;
              const usesMultipleValues = rule.operator === "in" || rule.operator === "notIn";
              return (
                <SortableFilterRow
                  key={rule.id}
                  id={rule.id}
                  moveLabel={t("tableList.actions.moveFilter", { number: index + 1 })}
                >
                  <span className="text-xs font-medium text-muted-foreground">
                    {index === 0 ? t("tableList.filters.where") : t("tableList.filters.and")}
                  </span>
                  <select
                    aria-label={t("tableList.filters.field", { number: index + 1 })}
                    className="h-8 min-w-0 rounded-sm border bg-background px-2 text-xs"
                    value={rule.field}
                    onChange={(event) => {
                      const nextField = fields.find((candidate) => candidate.name === event.target.value);
                      if (nextField)
                        updateRule(rule.id, { field: nextField.name, operator: defaultOperator(nextField), value: "" });
                    }}
                  >
                    {fields.map((candidate) => (
                      <option key={candidate.name} value={candidate.name}>
                        {translate(candidate.labelKey)}
                      </option>
                    ))}
                  </select>
                  <select
                    aria-label={t("tableList.filters.operator", { number: index + 1 })}
                    className="h-8 min-w-0 rounded-sm border bg-background px-2 text-xs"
                    value={rule.operator}
                    onChange={(event) => {
                      const operator = event.target.value as FilterOperator;
                      updateRule(rule.id, {
                        operator,
                        value: valueForOperator(rule.value, rule.operator, operator),
                      });
                    }}
                  >
                    {field.capabilities.filterOperators.map((operator) => (
                      <option key={operator} value={operator}>
                        {t(operatorKeys[operator])}
                      </option>
                    ))}
                  </select>
                  {rule.operator !== "exists" &&
                  field.type === "relationship" &&
                  relationshipSources[field.relationTo] ? (
                    // The picker reads the API v2 token with a suspense query, which a page that does
                    // not already hold one would otherwise suspend the whole panel for.
                    <Suspense fallback={<Skeleton className="h-8 rounded-sm" />}>
                      <RelationshipPicker
                        source={relationshipSources[field.relationTo]}
                        ariaLabel={t("tableList.filters.value", { number: index + 1 })}
                        className="rounded-sm"
                        compact
                        multiple={usesMultipleValues}
                        value={usesMultipleValues ? listValue(rule.value).join(",") : scalarValue(rule.value)}
                        onChange={(value) =>
                          updateRule(rule.id, {
                            value: usesMultipleValues ? value.split(",").filter(Boolean) : value,
                          })
                        }
                      />
                    </Suspense>
                  ) : usesMultipleValues ? (
                    <MultiSelect
                      options={field.type === "select" ? field.options : []}
                      value={listValue(rule.value)}
                      onValueChange={(value) => updateRule(rule.id, { value })}
                      allowCustomValues={field.type !== "select"}
                      ariaLabel={t("tableList.filters.value", { number: index + 1 })}
                      placeholder={
                        field.type === "select"
                          ? multiSelectLabels.selectPlaceholder
                          : multiSelectLabels.customPlaceholder
                      }
                      emptyMessage={field.type === "select" ? multiSelectLabels.empty : multiSelectLabels.enterValue}
                      removeLabel={multiSelectLabels.remove}
                      className="min-h-8 rounded-sm py-1 text-xs"
                    />
                  ) : rule.operator === "equals" && field.type === "select" ? (
                    <select
                      aria-label={t("tableList.filters.value", { number: index + 1 })}
                      className="h-8 min-w-0 rounded-sm border bg-background px-2 text-xs"
                      value={scalarValue(rule.value)}
                      onChange={(event) => updateRule(rule.id, { value: event.target.value })}
                    >
                      <option value="">{t("tableList.filters.placeholders.value")}</option>
                      {field.options.map((option) => (
                        <option
                          key={typeof option === "string" ? option : option.value}
                          value={typeof option === "string" ? option : option.value}
                        >
                          {typeof option === "string" ? option : option.label}
                        </option>
                      ))}
                    </select>
                  ) : rule.operator === "exists" || field.type === "boolean" ? (
                    <select
                      aria-label={t("tableList.filters.value", { number: index + 1 })}
                      className="h-8 min-w-0 rounded-sm border bg-background px-2 text-xs"
                      value={scalarValue(rule.value)}
                      onChange={(event) => updateRule(rule.id, { value: event.target.value })}
                    >
                      {rule.operator === "exists" ? (
                        <>
                          <option value="true">{t("tableList.filters.present")}</option>
                          <option value="false">{t("tableList.filters.missing")}</option>
                        </>
                      ) : (
                        <>
                          <option value="true">{t("actions.yes")}</option>
                          <option value="false">{t("actions.no")}</option>
                        </>
                      )}
                    </select>
                  ) : targetFieldSource(field.name, config.fields) ? (
                    // A filter on the target's own field, so suggestions come from the target
                    // collection while the value stays free text.
                    <Suspense fallback={<Skeleton className="h-8 rounded-sm" />}>
                      <TargetFieldValueInput
                        source={targetFieldSource(field.name, config.fields) as RelationshipSource}
                        ariaLabel={t("tableList.filters.value", { number: index + 1 })}
                        value={scalarValue(rule.value)}
                        onChange={(value) => updateRule(rule.id, { value })}
                        idLinkLabel={(globalId) => t("tableList.filters.openRecord", { globalId })}
                      />
                    </Suspense>
                  ) : (
                    <Input
                      aria-label={t("tableList.filters.value", { number: index + 1 })}
                      className="h-8 rounded-sm text-xs"
                      type={field.type === "number" ? "number" : field.type === "dateTime" ? "datetime-local" : "text"}
                      placeholder={t(
                        rule.operator === "matches"
                          ? "tableList.filters.placeholders.pattern"
                          : "tableList.filters.placeholders.value",
                      )}
                      value={scalarValue(rule.value)}
                      onChange={(event) => updateRule(rule.id, { value: event.target.value })}
                    />
                  )}
                  <Button
                    aria-label={t("tableList.actions.removeFilter", { number: index + 1 })}
                    size="icon-xs"
                    variant="ghost"
                    onClick={() => setRules((current) => current.filter((candidate) => candidate.id !== rule.id))}
                  >
                    <Trash2Icon aria-hidden="true" />
                  </Button>
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
