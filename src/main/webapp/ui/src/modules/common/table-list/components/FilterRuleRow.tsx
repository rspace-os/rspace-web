import type { Namespace } from "i18next";
import { Trash2Icon } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { ResolvedCollectionConfig, ResolvedFieldConfig, SearchSelector } from "@/modules/common/collection/config";
import type { FilterOperator } from "@/modules/common/collection/filterOperators";
import { useFieldLabels } from "@/modules/common/collection/useFieldLabels";
import { Button } from "@/modules/common/ui/button";
import type { TableListRuntimeFields } from "../types";
import {
  type DraftRule,
  defaultOperator,
  namespaceForSentinel,
  sentinelFor,
  valueForOperator,
} from "../utils/filterDrafts";
import { CustomFieldPicker, type RuntimeFieldSourceGroup, runtimeFieldGroupKey } from "./CustomFieldPicker";
import { FilterSelect, type FilterSelectOption } from "./FilterSelect";
import { FilterValueInput } from "./FilterValueInput";
import { SortableControlRow } from "./SortableControlRow";

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

export function FilterRuleRow<TDocument>({
  rule,
  index,
  fields,
  fieldOptions,
  runtimeSourceGroups,
  relationshipSources,
  runtimeFields,
  authScope,
  columnToggle,
  onChange,
  onRemove,
}: {
  rule: DraftRule<TDocument>;
  index: number;
  fields: readonly ResolvedFieldConfig<TDocument>[];
  fieldOptions: readonly FilterSelectOption[];
  runtimeSourceGroups: readonly RuntimeFieldSourceGroup[];
  relationshipSources: ResolvedCollectionConfig<TDocument>["relationshipSources"];
  runtimeFields?: TableListRuntimeFields;
  authScope?: string | number;
  /** Set only when the panel offers the "also show as column" toggle. */
  columnToggle?: { shown: ReadonlySet<string>; onChange: (field: SearchSelector<TDocument>, shown: boolean) => void };
  onChange: (update: Partial<DraftRule<TDocument>>) => void;
  onRemove: () => void;
}) {
  const { t } = useTranslation<Namespace>("common");
  const { text, fieldOptionLabel } = useFieldLabels();
  const chosen = fields.find((candidate) => candidate.name === rule.field);
  const sentinelNamespace = namespaceForSentinel(String(rule.field));
  const pickingGroup = runtimeFieldGroupKey(runtimeSourceGroups, String(rule.field), chosen?.origin?.namespace ?? null);
  const sourceGroup = runtimeSourceGroups.find((candidate) => candidate.key === pickingGroup) ?? null;
  const field = chosen ?? fields[0];
  const awaitingDefinition = sentinelNamespace !== null;
  if (!field) return null;
  const operatorControl = awaitingDefinition ? null : (
    <FilterSelect
      ariaLabel={t("tableList.filters.operator", { number: index + 1 })}
      options={field.capabilities.filterOperators.map((operator) => ({
        value: operator,
        label: t(operatorKeys[operator]),
        groupLabelKey: null,
      }))}
      value={rule.operator}
      labels={{
        placeholder: t("tableList.filters.operatorSearch.placeholder"),
        noMatch: t("tableList.filters.operatorSearch.noMatch"),
        clear: t("tableList.filters.operatorSearch.clear"),
        trigger: t("tableList.filters.operatorSearch.trigger"),
      }}
      onChange={(next) => {
        const operator = next as FilterOperator;
        onChange({
          operator,
          value: valueForOperator(rule.value, rule.operator, operator),
        });
      }}
    />
  );
  const valueControl = awaitingDefinition ? null : (
    <FilterValueInput
      field={field}
      sources={relationshipSources}
      authScope={authScope}
      operator={rule.operator}
      value={rule.value}
      number={index + 1}
      onChange={(value) => onChange({ value })}
    />
  );
  const runtimeFieldPicker = sourceGroup !== null && runtimeFields && (
    <CustomFieldPicker
      key={sourceGroup.key}
      sources={sourceGroup.sources}
      ariaLabel={
        sourceGroup.via === undefined
          ? t("tableList.filters.customField.searchLabel", { number: index + 1 })
          : t("tableList.filters.customField.searchLabelVia", {
              number: index + 1,
              via: text(sourceGroup.via.label),
            })
      }
      chosenLabel={awaitingDefinition ? undefined : fieldOptionLabel(field)}
      chosenSelector={awaitingDefinition ? undefined : String(rule.field)}
      known={runtimeFields.definitions}
      onSelect={(namespace, definition) => {
        runtimeFields.onSelect(namespace, definition);
        const selector = `${namespace}.${definition.id}` as SearchSelector<TDocument>;
        onChange({ field: selector, operator: "equals", value: "" });
      }}
    />
  );
  return (
    <SortableControlRow
      className="sm:col-span-full sm:grid-cols-subgrid"
      id={rule.id}
      moveLabel={t("tableList.actions.moveFilter", { number: index + 1 })}
    >
      <FilterSelect
        ariaLabel={t("tableList.filters.field", { number: index + 1 })}
        options={fieldOptions}
        labels={{
          placeholder: t("tableList.filters.fieldSearch.placeholder"),
          noMatch: t("tableList.filters.fieldSearch.noMatch"),
          clear: t("tableList.filters.fieldSearch.clear"),
          trigger: t("tableList.filters.fieldSearch.trigger"),
        }}
        value={pickingGroup === null ? String(rule.field) : sentinelFor(pickingGroup)}
        onChange={(next) => {
          const namespace = namespaceForSentinel(next);
          if (namespace !== null) {
            onChange({ field: sentinelFor(namespace) as SearchSelector<TDocument>, value: "" });
            return;
          }
          const nextField = fields.find((candidate) => String(candidate.name) === next);
          if (nextField) onChange({ field: nextField.name, operator: defaultOperator(nextField), value: "" });
        }}
      />
      {sourceGroup === null ? operatorControl : <div className="sm:col-span-2">{runtimeFieldPicker}</div>}
      {sourceGroup === null ? valueControl : null}
      {columnToggle && field.list !== false && !awaitingDefinition ? (
        <label className="flex items-center gap-1.5 text-xs whitespace-nowrap text-muted-foreground">
          <input
            type="checkbox"
            className="size-3.5"
            checked={columnToggle.shown.has(String(field.name))}
            onChange={(event) => columnToggle.onChange(field.name, event.target.checked)}
          />
          {t("tableList.filters.alsoShowAsColumn")}
        </label>
      ) : null}
      <Button
        aria-label={t("tableList.actions.removeFilter", { number: index + 1 })}
        size="icon-xs"
        variant="ghost"
        onClick={onRemove}
      >
        <Trash2Icon aria-hidden="true" />
      </Button>
      {sourceGroup !== null && !awaitingDefinition ? (
        <div className="grid gap-2 sm:col-start-2 sm:col-span-3 sm:grid-cols-[minmax(7.5rem,auto)_minmax(10rem,1fr)]">
          {operatorControl}
          {valueControl}
        </div>
      ) : null}
    </SortableControlRow>
  );
}
