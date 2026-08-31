import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import type { FilterOperator, ResolvedFieldConfig } from "@/modules/common/collection/collectionConfig";
import { selectOptionText } from "@/modules/common/collection/collectionConfig";
import { RelationshipPicker } from "@/modules/common/relationship-picker/RelationshipPicker";
import { type RelationshipSource, relationshipSources } from "@/modules/common/relationship-picker/relationshipSources";
import { Input } from "@/modules/common/ui/input";
import { MultiSelect } from "@/modules/common/ui/multi-select";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { FilterSelect } from "./FilterSelect";
import { SuggestedValueInput } from "./SuggestedValueInput";
import { useTargetScalarValues } from "./useTargetScalarValues";

type FilterValue = string | readonly string[];

function scalarValue(value: FilterValue): string {
  return typeof value === "string" ? value : (value[0] ?? "");
}

function listValue(value: FilterValue): readonly string[] {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? [] : [trimmed];
}

function targetFieldSource<TDocument>(
  name: string,
  fields: readonly ResolvedFieldConfig<TDocument>[],
): RelationshipSource | null {
  const dot = name.indexOf(".");
  if (dot <= 0) return null;
  const owner = fields.find((candidate) => candidate.name === name.slice(0, dot));
  if (owner?.type !== "relationship") return null;
  const field = fields.find((candidate) => candidate.name === name);
  if (field?.origin?.kind === "runtimeField") return null;
  return relationshipSources[owner.relationTo] ?? null;
}

function TargetScalarValue({
  source,
  ariaLabel,
  value,
  onChange,
  idLinkLabel,
}: {
  source: RelationshipSource;
  ariaLabel: string;
  value: string;
  onChange: (value: string) => void;
  idLinkLabel: (globalId: string) => string;
}) {
  return (
    <SuggestedValueInput
      ariaLabel={ariaLabel}
      value={value}
      onChange={onChange}
      minimumLength={2}
      useSuggestions={(term, open) => useTargetScalarValues({ source, term, enabled: open, idLinkLabel })}
    />
  );
}

export function FilterValueInput<TDocument>({
  field,
  fields,
  operator,
  value,
  number,
  onChange,
}: {
  field: ResolvedFieldConfig<TDocument>;
  fields: readonly ResolvedFieldConfig<TDocument>[];
  operator: FilterOperator;
  value: FilterValue;
  number: number;
  onChange: (value: FilterValue) => void;
}) {
  const { t } = useTranslation("common");
  const ariaLabel = t("tableList.filters.value", { number });
  const multiple = operator === "in" || operator === "notIn";
  const targetSource = targetFieldSource(field.name, fields);
  const selectLabels = {
    placeholder: t("tableList.filters.placeholders.value"),
    noMatch: t("tableList.filters.valueSearch.noMatch"),
    clear: t("tableList.filters.valueSearch.clear"),
    trigger: t("tableList.filters.valueSearch.trigger"),
  };

  if (operator !== "exists" && field.type === "relationship" && relationshipSources[field.relationTo]) {
    return (
      <Suspense fallback={<Skeleton className="h-8 rounded-sm" />}>
        <RelationshipPicker
          source={relationshipSources[field.relationTo]}
          ariaLabel={ariaLabel}
          className="rounded-sm"
          compact
          multiple={multiple}
          value={multiple ? listValue(value).join(",") : scalarValue(value)}
          onChange={(next) => onChange(multiple ? next.split(",").filter(Boolean) : next)}
        />
      </Suspense>
    );
  }

  if (multiple) {
    const select = field.type === "select";
    return (
      <MultiSelect
        options={
          select
            ? field.options.map((option) => ({
                label: selectOptionText(option),
                value: typeof option === "string" ? option : option.value,
              }))
            : []
        }
        value={listValue(value)}
        onValueChange={onChange}
        allowCustomValues={!select}
        ariaLabel={ariaLabel}
        placeholder={
          select
            ? t("tableList.filters.multiSelect.selectPlaceholder")
            : t("tableList.filters.multiSelect.customPlaceholder")
        }
        emptyMessage={select ? t("tableList.filters.multiSelect.empty") : t("tableList.filters.multiSelect.enterValue")}
        removeLabel={(item) => t("tableList.filters.multiSelect.remove", { value: item })}
        className="min-h-8 rounded-sm py-1 text-xs"
      />
    );
  }

  if (operator === "equals" && field.type === "select") {
    return (
      <FilterSelect
        ariaLabel={ariaLabel}
        options={field.options.map((option) => ({
          value: typeof option === "string" ? option : option.value,
          label: selectOptionText(option),
          groupLabelKey: null,
        }))}
        value={scalarValue(value)}
        labels={selectLabels}
        onChange={onChange}
      />
    );
  }

  if (operator === "exists" || field.type === "boolean") {
    return (
      <FilterSelect
        ariaLabel={ariaLabel}
        options={
          operator === "exists"
            ? [
                { value: "true", label: t("tableList.filters.present"), groupLabelKey: null },
                { value: "false", label: t("tableList.filters.missing"), groupLabelKey: null },
              ]
            : [
                { value: "true", label: t("actions.yes"), groupLabelKey: null },
                { value: "false", label: t("actions.no"), groupLabelKey: null },
              ]
        }
        value={scalarValue(value)}
        labels={selectLabels}
        onChange={onChange}
      />
    );
  }

  if (targetSource) {
    return (
      <Suspense fallback={<Skeleton className="h-8 rounded-sm" />}>
        <TargetScalarValue
          source={targetSource}
          ariaLabel={ariaLabel}
          value={scalarValue(value)}
          onChange={onChange}
          idLinkLabel={(globalId) => t("tableList.filters.openRecord", { globalId })}
        />
      </Suspense>
    );
  }

  return (
    <Input
      aria-label={ariaLabel}
      className="h-8 rounded-sm text-xs"
      type={field.type === "number" ? "number" : field.type === "dateTime" ? "datetime-local" : "text"}
      placeholder={t(
        operator === "matches" ? "tableList.filters.placeholders.pattern" : "tableList.filters.placeholders.value",
      )}
      value={scalarValue(value)}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}
