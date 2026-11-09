import { type HTMLInputTypeAttribute, Suspense } from "react";
import { useTranslation } from "react-i18next";
import type { ResolvedFieldConfig, SelectOption } from "@/modules/common/collection/config";
import type { FilterOperator } from "@/modules/common/collection/filterOperators";
import { selectOptionText } from "@/modules/common/collection/utils";
import { RelationshipPicker } from "@/modules/common/relationship-picker/RelationshipPicker";
import { type RelationshipSource, relationshipSourceForPicker } from "@/modules/common/relationship-picker/sources";
import { Input } from "@/modules/common/ui/input";
import { MultiSelect } from "@/modules/common/ui/multi-select";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { valueBehaviourOf } from "../fieldValueBehaviour";
import { tableListRelationshipSources } from "../relationshipSources";
import { type DraftValue, listValue, scalarValue } from "../utils/filterDrafts";
import { FilterSelect } from "./FilterSelect";

function identitySourceFor<TDocument>(
  field: ResolvedFieldConfig<TDocument>,
  sources: Readonly<Record<string, RelationshipSource>>,
): RelationshipSource | undefined {
  if (field.filterPicker) {
    return relationshipSourceForPicker(field.filterPicker, sources);
  }
  return field.type === "relationship" ? sources[field.relationTo] : undefined;
}

type ValueControl =
  | { kind: "presence" }
  | { kind: "picker"; source: RelationshipSource }
  // `options` only for a select field; custom values are allowed without them.
  | { kind: "multiple"; options: readonly SelectOption[] | undefined }
  | { kind: "select"; options: readonly SelectOption[] }
  | { kind: "boolean" }
  | { kind: "input"; type: HTMLInputTypeAttribute; step: string | undefined };

/** Which control enters a filter value, carrying what that control renders. */
export function valueControlFor<TDocument>(
  field: ResolvedFieldConfig<TDocument>,
  operator: FilterOperator,
  identitySource: RelationshipSource | undefined,
): ValueControl {
  const { behaviour, inputType, selectOptions } = valueBehaviourOf(field);
  if (operator === "exists") return { kind: "presence" };
  if (identitySource) return { kind: "picker", source: identitySource };
  if (operator === "in" || operator === "notIn") return { kind: "multiple", options: selectOptions };
  if (behaviour.valueControl === "select" && selectOptions !== undefined)
    return { kind: "select", options: selectOptions };
  if (behaviour.valueControl === "boolean") return { kind: "boolean" };
  return { kind: "input", type: inputType, step: behaviour.inputStep };
}

export function FilterValueInput<TDocument>({
  field,
  sources,
  authScope,
  operator,
  value,
  number,
  onChange,
}: {
  field: ResolvedFieldConfig<TDocument>;
  sources?: Readonly<Record<string, RelationshipSource>>;
  authScope?: string | number;
  operator: FilterOperator;
  value: DraftValue;
  number: number;
  onChange: (value: DraftValue) => void;
}) {
  const { t } = useTranslation("common");
  const ariaLabel = t("tableList.filters.value", { number });
  const multiple = operator === "in" || operator === "notIn";
  const availableSources = tableListRelationshipSources(sources);
  const identitySource = identitySourceFor(field, availableSources);
  const selectLabels = {
    placeholder: t("tableList.filters.placeholders.value"),
    noMatch: t("tableList.filters.valueSearch.noMatch"),
    clear: t("tableList.filters.valueSearch.clear"),
    trigger: t("tableList.filters.valueSearch.trigger"),
  };

  const presenceOptions = [
    { value: "true", label: t("tableList.filters.present"), groupLabelKey: null },
    { value: "false", label: t("tableList.filters.missing"), groupLabelKey: null },
  ];
  const yesNoOptions = [
    { value: "true", label: t("actions.yes"), groupLabelKey: null },
    { value: "false", label: t("actions.no"), groupLabelKey: null },
  ];
  const control = valueControlFor(field, operator, identitySource);

  switch (control.kind) {
    case "picker":
      return (
        <Suspense fallback={<Skeleton className="h-8 rounded-sm" />}>
          <RelationshipPicker
            sources={[control.source]}
            authScope={authScope}
            ariaLabel={ariaLabel}
            className="rounded-sm"
            compact
            multiple={multiple}
            value={multiple ? listValue(value).join(",") : scalarValue(value)}
            onChange={(next) => onChange(multiple ? next.split(",").filter(Boolean) : next)}
          />
        </Suspense>
      );
    case "multiple": {
      const select = control.options !== undefined;
      return (
        <MultiSelect
          options={(control.options ?? []).map((option) => ({
            label: selectOptionText(option),
            value: typeof option === "string" ? option : option.value,
          }))}
          value={listValue(value)}
          onValueChange={onChange}
          allowCustomValues={!select}
          ariaLabel={ariaLabel}
          placeholder={select ? "" : t("tableList.filters.multiSelect.customPlaceholder")}
          emptyMessage={
            select ? t("tableList.filters.multiSelect.empty") : t("tableList.filters.multiSelect.enterValue")
          }
          removeLabel={(item) => t("tableList.filters.multiSelect.remove", { value: item })}
          className="min-h-8 rounded-sm py-1 text-xs"
        />
      );
    }
    case "select":
      return (
        <FilterSelect
          ariaLabel={ariaLabel}
          options={control.options.map((option) => ({
            value: typeof option === "string" ? option : option.value,
            label: selectOptionText(option),
            groupLabelKey: null,
          }))}
          value={scalarValue(value)}
          labels={selectLabels}
          onChange={onChange}
        />
      );
    case "presence":
    case "boolean":
      return (
        <FilterSelect
          ariaLabel={ariaLabel}
          options={control.kind === "presence" ? presenceOptions : yesNoOptions}
          value={scalarValue(value)}
          labels={selectLabels}
          onChange={onChange}
        />
      );
    case "input":
      return (
        <Input
          aria-label={ariaLabel}
          className="h-8 rounded-sm text-xs"
          type={control.type}
          step={control.step}
          placeholder={
            operator === "matches"
              ? t("tableList.filters.placeholders.pattern")
              : t("tableList.filters.placeholders.value")
          }
          value={scalarValue(value)}
          onChange={(event) => onChange(event.target.value)}
        />
      );
  }
}
