import { closestCenter, DndContext, type DragEndEvent } from "@dnd-kit/core";
import { restrictToVerticalAxis } from "@dnd-kit/modifiers";
import { arrayMove, SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import type { Namespace, TFunction } from "i18next";
import { PlusIcon, Trash2Icon, XIcon } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { ResolvedCollectionConfig, ResolvedFieldConfig, SearchSelector } from "@/modules/common/collection/config";
import { useFieldLabels } from "@/modules/common/collection/useFieldLabels";
import { Button } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import { useTableListDragAccessibility } from "../hooks/useTableListDragAccessibility";
import { useTableListDragSensors } from "../hooks/useTableListDragSensors";
import { serializeRsqlExpression } from "../rsql";
import type { FilterExpression, TableListRuntimeFields } from "../types";
import { allOf } from "../utils/expressions";
import {
  comparisonNodes,
  type DraftRule,
  defaultOperator,
  isActive,
  parsedValue,
  sentinelFor,
} from "../utils/filterDrafts";
import { groupRuntimeFieldSources, type RuntimeFieldSourceGroup } from "./CustomFieldPicker";
import { FilterRuleRow } from "./FilterRuleRow";
import type { FilterSelectOption } from "./FilterSelect";

type FieldLabels = ReturnType<typeof useFieldLabels>;

function topLevelNodes<TDocument>(
  expression: FilterExpression<TDocument> | null,
): readonly FilterExpression<TDocument>[] {
  if (expression?.kind === "and") return expression.children;
  if (expression) return [expression];
  return [];
}

function fieldGroupKey(via: { name: string } | undefined, groupLabelKey: string | null): string {
  if (via) return `relationship:${via.name}`;
  if (groupLabelKey) return `builtin:${groupLabelKey}`;
  return "ungrouped";
}

function filterFieldOptions<TDocument>(
  fields: readonly ResolvedFieldConfig<TDocument>[],
  groups: readonly RuntimeFieldSourceGroup[],
  { t, text, fieldOptionLabel }: { t: TFunction<Namespace> } & Pick<FieldLabels, "text" | "fieldOptionLabel">,
): FilterSelectOption[] {
  return [
    ...fields
      .filter((candidate) => candidate.origin?.namespace === undefined)
      .map((candidate): FilterSelectOption => {
        const via = candidate.origin?.via;
        const groupLabelKey = via ? null : (candidate.origin?.groupLabelKey ?? null);
        return {
          value: candidate.name,
          label: fieldOptionLabel(candidate),
          groupKey: fieldGroupKey(via, groupLabelKey),
          groupLabelKey,
          groupLabel: via ? text(via.label) : undefined,
        };
      }),
    ...groups.map((group): FilterSelectOption => {
      const viaLabel = group.via ? text(group.via.label) : "";
      return {
        value: sentinelFor(group.key),
        label: t("tableList.filters.customField.option"),
        selectedLabel:
          viaLabel === ""
            ? t("tableList.filters.customField.option")
            : t("tableList.filters.customField.optionVia", { via: viaLabel }),
        groupKey: group.via ? `relationship:${group.via.name}` : "builtin:tableList.fieldGroups.customFields",
        groupLabelKey: viaLabel === "" ? "tableList.fieldGroups.customFields" : null,
        groupLabel: viaLabel || undefined,
      };
    }),
  ];
}

export function TableListFilters<TDocument>({
  controlPanelId,
  config,
  expression,
  visibleFields,
  onApply,
  onShowColumn,
  runtimeFields,
  authScope,
  onClose,
}: {
  /** Unique per table, so several tables on one page have distinct panel ids. */
  controlPanelId: string;
  config: ResolvedCollectionConfig<TDocument>;
  expression: FilterExpression<TDocument> | null;
  visibleFields?: readonly SearchSelector<TDocument>[];
  onApply: (expression: FilterExpression<TDocument> | null) => void;
  onShowColumn?: (field: SearchSelector<TDocument>, shown: boolean) => void;
  runtimeFields?: TableListRuntimeFields;
  /** The caller's query scope for relationship value suggestions. */
  authScope?: string | number;
  onClose: () => void;
}) {
  const { t } = useTranslation<Namespace>("common");
  const { text, fieldOptionLabel } = useFieldLabels();
  // Filter-only fields must still be offered even though they are hidden from columns.
  const fields = config.fields.filter(
    (field) => field.capabilities.filterOperators.length > 0 && (field.list !== false || field.form === false),
  );
  const runtimeSources =
    runtimeFields === undefined ? [] : (config.runtimeSources ?? []).filter((source) => source.filterable);
  const runtimeSourceGroups = groupRuntimeFieldSources(runtimeSources);
  const shownColumns = new Set<string>(visibleFields ?? []);
  const offersColumns = onShowColumn !== undefined && visibleFields !== undefined;
  // Shared parent tracks keep field, operator, value, optional-column and remove-control widths aligned across rows.
  const rowColumns = offersColumns
    ? "sm:grid-cols-[1.5rem_minmax(10rem,1.3fr)_minmax(7.5rem,auto)_minmax(10rem,1.3fr)_auto_2rem]"
    : "sm:grid-cols-[1.5rem_minmax(10rem,1.3fr)_minmax(7.5rem,auto)_minmax(10rem,1.3fr)_2rem]";
  const [rules, setRules] = useState<readonly DraftRule<TDocument>[]>(() => comparisonNodes(expression, config));
  // The row editor edits direct AND comparisons. Keep nested Boolean groups intact.
  const [savedGroups, setSavedGroups] = useState(() =>
    topLevelNodes(expression).filter((node) => node.kind !== "comparison"),
  );
  const sensors = useTableListDragSensors();
  const dragAccessibility = useTableListDragAccessibility((id) =>
    t("tableList.dragAndDrop.filterRule", { number: rules.findIndex((rule) => rule.id === id) + 1 }),
  );
  const fieldOptions = filterFieldOptions(fields, runtimeSourceGroups, { t, text, fieldOptionLabel });

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
      id={controlPanelId}
      aria-labelledby={`${controlPanelId}-title`}
      className="rounded-sm border bg-popover p-4"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id={`${controlPanelId}-title`} className="font-heading text-sm font-medium">
            {t("tableList.filters.title")}
          </h2>
          {rules.length + savedGroups.length > 1 ? (
            <p className="text-xs text-muted-foreground">{t("tableList.filters.matchAll")}</p>
          ) : null}
        </div>
        <Button aria-label={t("tableList.actions.closeFilters")} size="icon-xs" variant="ghost" onClick={onClose}>
          <XIcon aria-hidden="true" />
        </Button>
      </div>

      {savedGroups.map((group, index) => (
        <div key={serializeRsqlExpression(group)} className="mt-3 flex items-start gap-2 rounded-sm border p-2">
          <div className="min-w-0 flex-1">
            <p className="text-sm">{t("tableList.filters.savedGroup")}</p>
            <code className="break-all text-xs">{serializeRsqlExpression(group)}</code>
          </div>
          <Button
            size="icon-xs"
            variant="ghost"
            aria-label={t("tableList.actions.removeFilter", { number: rules.length + index + 1 })}
            onClick={() => setSavedGroups((groups) => groups.filter((_, position) => position !== index))}
          >
            <Trash2Icon aria-hidden="true" />
          </Button>
        </div>
      ))}
      <DndContext
        accessibility={dragAccessibility}
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
            {rules.map((rule, index) => (
              <FilterRuleRow
                key={rule.id}
                rule={rule}
                index={index}
                fields={fields}
                fieldOptions={fieldOptions}
                runtimeSourceGroups={runtimeSourceGroups}
                relationshipSources={config.relationshipSources}
                runtimeFields={runtimeFields}
                authScope={authScope}
                columnToggle={offersColumns ? { shown: shownColumns, onChange: onShowColumn } : undefined}
                onChange={(update) => updateRule(rule.id, update)}
                onRemove={() => setRules((current) => current.filter((candidate) => candidate.id !== rule.id))}
              />
            ))}
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
            {
              id: Math.max(0, ...current.map((rule) => rule.id)) + 1,
              field: field.name,
              operator: defaultOperator(field),
              value: "",
            },
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
          disabled={rules.length === 0 && savedGroups.length === 0}
          // Clearing applies straight away: leaving the applied filters in place while the panel
          // shows none reads as a no-op.
          onClick={() => {
            setRules([]);
            setSavedGroups([]);
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
            onApply(allOf([...comparisons, ...savedGroups]));
          }}
        >
          {t("tableList.actions.applyFilters")}
        </Button>
      </div>
    </section>
  );
}
