import { type FocusEventHandler, type Ref, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { RelationshipOptionAvailabilitySource } from "@/modules/common/collection-form/RenderFields.types";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import {
  Combobox,
  ComboboxChip,
  ComboboxChips,
  ComboboxChipsInput,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxValue,
  useComboboxAnchor,
} from "@/modules/common/ui/combobox";
import { Spinner } from "@/modules/common/ui/spinner";
import { cn } from "@/modules/common/utils/cn";
import {
  useRelationshipOptionAvailability,
  useRelationshipOptions,
  useSelectedRelationshipOptions,
} from "./relationshipOptionQueries";
import type { RelationshipOptionWithSource, RelationshipSource } from "./relationshipSources";

// Matches the h-8 / text-xs controls a filter row uses. The inner input needs its own rules because
// `Input` hard-codes h-9 and md:text-sm, which a class on the wrapper cannot override.
const compactInputClasses = "h-8 w-full text-xs [&_input]:h-8 [&_input]:py-0 [&_input]:text-xs";
const compactChipsClasses =
  "min-h-8 max-h-24 w-full overflow-y-auto py-1 text-xs [&_input]:h-6 [&_input]:py-0 [&_input]:text-xs";

function splitValue(value: string, sources: readonly RelationshipSource[]): readonly string[] {
  const seen = new Set<string>();
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean)
    .filter((item) => {
      const owner = sources.find((candidate) => candidate.ownsValue(item));
      const canonical = owner?.normalizeValue?.(item) ?? item;
      const key = JSON.stringify([owner?.id ?? "unknown", canonical]);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}

function sameOption(option: RelationshipOptionWithSource, other: RelationshipOptionWithSource) {
  return option.sourceId === other.sourceId && option.value === other.value;
}

/**
 * Selects related entities from one or more source adapters, by name or by source-owned value.
 *
 * The value is the comma-separated global ID list the REST API v2 relationship filter accepts, so
 * a caller stores and restores exactly what it sends.
 */
export function RelationshipPicker({
  source,
  sources,
  availabilitySource,
  authScope,
  value,
  onChange,
  onOptionChange,
  onOptionsChange,
  multiple = false,
  compact = false,
  disabled = false,
  id,
  name,
  required,
  autoFocus,
  ariaDescribedBy,
  ariaInvalid,
  ariaLabel,
  className,
  inputRef,
  onFocus,
  onBlur,
  showClear = true,
}: {
  /** One source, retained as a compatibility alias. */
  source?: RelationshipSource;
  /** Sources searched in parallel and merged in declaration order. */
  sources?: readonly RelationshipSource[];
  availabilitySource?: RelationshipOptionAvailabilitySource;
  /** Caller identity used to partition cached options when permissions differ between callers. */
  authScope?: string | number;
  value: string;
  onChange: (value: string) => void;
  /** Receives the selected option for consumers that need its display metadata. */
  onOptionChange?: (option: RelationshipOptionWithSource | null) => void;
  /** Receives all selected options in multiple mode. */
  onOptionsChange?: (options: readonly RelationshipOptionWithSource[]) => void;
  multiple?: boolean;
  /** Shrinks the control to the h-8 / text-xs sizing a filter row uses, and lets it shrink to its column. */
  compact?: boolean;
  disabled?: boolean;
  id?: string;
  name?: string;
  required?: boolean;
  autoFocus?: boolean;
  ariaDescribedBy?: string;
  ariaInvalid?: boolean;
  ariaLabel: string;
  className?: string;
  inputRef?: Ref<HTMLInputElement>;
  onFocus?: FocusEventHandler<HTMLInputElement>;
  onBlur?: FocusEventHandler<HTMLInputElement>;
  showClear?: boolean;
}) {
  const { t } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const anchor = useComboboxAnchor();
  const [term, setTerm] = useState("");
  const sourceList = sources ?? (source === undefined ? [] : [source]);
  const hasSearchTerm = term.trim() !== "";
  const labels = useMemo(
    () => ({
      idLinkLabel: (globalId: string) => t("relationshipPicker.openRecord", { globalId }),
      unavailableLabel: (value: string) => t("relationshipPicker.unavailable", { value }),
      failedLabel: () => t("relationshipPicker.restoreFailed"),
      compact,
    }),
    [t, compact],
  );

  const selected = useSelectedRelationshipOptions({
    sources: sourceList,
    values: splitValue(value, sourceList),
    token,
    authScope,
    labels,
  });
  const { options, failed, loading } = useRelationshipOptions({
    sources: sourceList,
    term,
    token,
    authScope,
    labels,
    enabled: hasSearchTerm,
  });
  const availability = useRelationshipOptionAvailability({
    source: sourceList[0] ?? {
      id: "none",
      ownsValue: () => false,
      search: async () => [],
      toOption: () => ({ value: "", label: "" }),
    },
    options,
    availabilitySource,
    token,
    authScope,
  });
  // Keeps the selected options selectable while a fresh search is in flight, so a chip never
  // disappears from the list mid-typing.
  const items = useMemo(() => {
    const shown = hasSearchTerm ? [...options] : [];
    for (const option of selected) {
      if (!shown.some((candidate) => sameOption(candidate, option))) shown.push(option);
    }
    return shown;
  }, [hasSearchTerm, options, selected]);
  const emptyMessage = hasSearchTerm
    ? loading
      ? t("loading")
      : failed
        ? t("relationshipPicker.failed")
        : t("relationshipPicker.empty")
    : t("relationshipPicker.enterSearchTerm");
  const handleInputValueChange = (nextTerm: string, { reason }: { reason: string }) => {
    if (reason === "input-change") setTerm(nextTerm);
    if (reason === "input-clear" || reason === "clear-press" || reason === "item-press") setTerm("");
  };
  const unavailableStatus = (option: RelationshipOptionWithSource) => availability.unavailable[String(option.value)];
  const optionIsDisabled = (option: RelationshipOptionWithSource) =>
    availabilitySource !== undefined &&
    (availability.checking || availability.failed || unavailableStatus(option) !== undefined);
  const optionContent = (option: RelationshipOptionWithSource) => {
    const status = unavailableStatus(option);
    const details = availability.checking
      ? t("relationshipPicker.availabilityChecking")
      : availability.failed
        ? t("relationshipPicker.availabilityFailed")
        : status === undefined
          ? null
          : availabilitySource?.renderUnavailable(option, status);
    return (
      <div className="min-w-0 flex-1">
        {option.content ?? option.label}
        {details === null ? null : <div className="mt-1 text-xs text-muted-foreground">{details}</div>}
      </div>
    );
  };
  const availabilityActions =
    availabilitySource?.renderAction === undefined
      ? []
      : options.flatMap((option) => {
          const status = unavailableStatus(option);
          return status === undefined
            ? []
            : [
                <div key={`${option.sourceId}:${option.value}`} className="text-xs">
                  {availabilitySource.renderAction?.(option, status)}
                </div>,
              ];
        });
  const actionRegion =
    availabilityActions.length === 0 ? null : <div className="mb-2 space-y-1">{availabilityActions}</div>;

  const common = {
    id,
    name,
    disabled,
    required,
    autoFocus,
    "aria-describedby": ariaDescribedBy,
    "aria-invalid": ariaInvalid || undefined,
    "aria-label": ariaLabel,
    placeholder: t("relationshipPicker.search"),
    ref: inputRef,
    onFocus,
    onBlur,
  };

  if (!multiple) {
    return (
      <>
        {actionRegion}
        <Combobox<RelationshipOptionWithSource>
          items={items}
          filter={null}
          value={selected[0] ?? null}
          disabled={disabled}
          onInputValueChange={handleInputValueChange}
          isItemEqualToValue={sameOption}
          itemToStringLabel={(option: RelationshipOptionWithSource) => option.label}
          onValueChange={(option: RelationshipOptionWithSource | null) => {
            if (option === null) {
              onChange("");
              onOptionChange?.(null);
            } else if (!optionIsDisabled(option)) {
              onChange(String(option.value));
              onOptionChange?.(option);
            }
          }}
        >
          <ComboboxInput
            {...common}
            className={cn(compact && compactInputClasses, className)}
            clearLabel={t("relationshipPicker.clear")}
            showClear={showClear}
            triggerLabel={t("relationshipPicker.openOptions")}
          />
          <ComboboxContent>
            {loading && hasSearchTerm ? (
              <div role="status" className="px-2 py-1 text-xs text-muted-foreground">
                {t("loading")}
              </div>
            ) : null}
            <ComboboxList>
              {(option: RelationshipOptionWithSource) => (
                <ComboboxItem
                  key={`${option.sourceId}:${option.value}`}
                  value={option}
                  disabled={optionIsDisabled(option)}
                  className="py-1.5 pr-7 pl-2"
                >
                  {optionContent(option)}
                </ComboboxItem>
              )}
            </ComboboxList>
            <ComboboxEmpty>{loading && hasSearchTerm ? null : emptyMessage}</ComboboxEmpty>
          </ComboboxContent>
        </Combobox>
      </>
    );
  }

  return (
    <>
      {actionRegion}
      <Combobox<RelationshipOptionWithSource, true>
        items={items}
        filter={null}
        multiple
        value={selected}
        disabled={disabled}
        onInputValueChange={handleInputValueChange}
        isItemEqualToValue={sameOption}
        itemToStringLabel={(option: RelationshipOptionWithSource) => option.label}
        onValueChange={(next: RelationshipOptionWithSource[]) => {
          if (!next.some(optionIsDisabled)) {
            onChange(next.map((option) => String(option.value)).join(","));
            onOptionsChange?.(next);
          }
        }}
      >
        <ComboboxChips
          ref={anchor}
          className={cn(compact && compactChipsClasses, className)}
          aria-busy={selected.some((option) => option.restoreStatus === "loading") || undefined}
        >
          <ComboboxValue>
            {(shown: RelationshipOptionWithSource[] | null) =>
              // base-ui passes null while the multiple value is empty
              (shown ?? []).map((option) => (
                <ComboboxChip
                  key={`${option.sourceId}:${option.value}`}
                  aria-invalid={option.restoreStatus === "invalid" || undefined}
                  aria-busy={option.restoreStatus === "loading" || undefined}
                  removeLabel={t("relationshipPicker.remove", { item: option.label })}
                >
                  {option.label}
                  {option.restoreStatus === "loading" ? (
                    <>
                      <span className="sr-only">{t("loading")}</span>
                      <Spinner aria-hidden="true" className="size-3" />
                    </>
                  ) : null}
                </ComboboxChip>
              ))
            }
          </ComboboxValue>
          <ComboboxChipsInput {...common} />
        </ComboboxChips>
        <ComboboxContent anchor={anchor}>
          {loading && hasSearchTerm ? (
            <div role="status" className="px-2 py-1 text-xs text-muted-foreground">
              {t("loading")}
            </div>
          ) : null}
          <ComboboxList>
            {(option: RelationshipOptionWithSource) => (
              <ComboboxItem
                key={`${option.sourceId}:${option.value}`}
                value={option}
                disabled={optionIsDisabled(option)}
                className="py-1.5 pr-7 pl-2"
              >
                {optionContent(option)}
              </ComboboxItem>
            )}
          </ComboboxList>
          <ComboboxEmpty>{loading && hasSearchTerm ? null : emptyMessage}</ComboboxEmpty>
        </ComboboxContent>
      </Combobox>
    </>
  );
}
