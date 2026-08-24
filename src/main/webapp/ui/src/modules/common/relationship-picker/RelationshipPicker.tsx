import { type FocusEventHandler, type Ref, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type {
  RelationshipOption,
  RelationshipOptionAvailabilitySource,
} from "@/modules/common/collection-form/RenderFields.types";
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
import { cn } from "@/modules/common/utils/cn";
import {
  useRelationshipOptionAvailability,
  useRelationshipOptions,
  useSelectedRelationshipOptions,
} from "./relationshipOptionQueries";
import type { RelationshipSource } from "./relationshipSources";

// Matches the h-8 / text-xs controls a filter row uses. The inner input needs its own rules because
// `Input` hard-codes h-9 and md:text-sm, which a class on the wrapper cannot override.
const compactInputClasses = "h-8 w-full text-xs [&_input]:h-8 [&_input]:py-0 [&_input]:text-xs";
const compactChipsClasses = "min-h-8 w-full py-1 text-xs [&_input]:h-6 [&_input]:py-0 [&_input]:text-xs";

function splitValue(value: string): readonly string[] {
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function sameOption(option: RelationshipOption, other: RelationshipOption) {
  return option.value === other.value;
}

/**
 * Selects related entities from one backend collection, by name or by global ID.
 *
 * The value is the comma-separated global ID list the REST API v2 relationship filter accepts, so
 * a caller stores and restores exactly what it sends.
 */
export function RelationshipPicker({
  source,
  availabilitySource,
  value,
  onChange,
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
  source: RelationshipSource;
  availabilitySource?: RelationshipOptionAvailabilitySource;
  value: string;
  onChange: (value: string) => void;
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
  const hasSearchTerm = term.trim() !== "";
  const labels = useMemo(
    () => ({ idLinkLabel: (globalId: string) => t("relationshipPicker.openRecord", { globalId }), compact }),
    [t, compact],
  );

  const selected = useSelectedRelationshipOptions({ source, globalIds: splitValue(value), token, labels });
  const { options, failed } = useRelationshipOptions({
    source,
    term,
    token,
    labels,
    enabled: hasSearchTerm,
  });
  const availability = useRelationshipOptionAvailability({ source, options, availabilitySource, token });
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
    ? failed
      ? t("relationshipPicker.failed")
      : t("relationshipPicker.empty")
    : t("relationshipPicker.enterSearchTerm");
  const handleInputValueChange = (nextTerm: string, { reason }: { reason: string }) => {
    if (reason === "input-change") setTerm(nextTerm);
    if (reason === "input-clear" || reason === "clear-press" || reason === "item-press") setTerm("");
  };
  const unavailableStatus = (option: RelationshipOption) => availability.unavailable[String(option.value)];
  const optionIsDisabled = (option: RelationshipOption) =>
    availabilitySource !== undefined &&
    (availability.checking || availability.failed || unavailableStatus(option) !== undefined);
  const optionContent = (option: RelationshipOption) => {
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
                <div key={option.value} className="text-xs">
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
        <Combobox
          items={items}
          filter={null}
          value={selected[0] ?? null}
          disabled={disabled}
          onInputValueChange={handleInputValueChange}
          isItemEqualToValue={sameOption}
          itemToStringLabel={(option: RelationshipOption) => option.label}
          onValueChange={(option: RelationshipOption | null) => {
            if (option === null) onChange("");
            else if (!optionIsDisabled(option)) onChange(String(option.value));
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
            <ComboboxList>
              {(option: RelationshipOption) => (
                <ComboboxItem
                  key={option.value}
                  value={option}
                  disabled={optionIsDisabled(option)}
                  className="py-1.5 pr-7 pl-2"
                >
                  {optionContent(option)}
                </ComboboxItem>
              )}
            </ComboboxList>
            <ComboboxEmpty>{emptyMessage}</ComboboxEmpty>
          </ComboboxContent>
        </Combobox>
      </>
    );
  }

  return (
    <>
      {actionRegion}
      <Combobox
        items={items}
        filter={null}
        multiple
        value={selected}
        disabled={disabled}
        onInputValueChange={handleInputValueChange}
        isItemEqualToValue={sameOption}
        itemToStringLabel={(option: RelationshipOption) => option.label}
        onValueChange={(next: RelationshipOption[]) => {
          if (!next.some(optionIsDisabled)) onChange(next.map((option) => String(option.value)).join(","));
        }}
      >
        <ComboboxChips ref={anchor} className={cn(compact && compactChipsClasses, className)}>
          <ComboboxValue>
            {(shown: RelationshipOption[] | null) =>
              // base-ui passes null while the multiple value is empty
              (shown ?? []).map((option) => (
                <ComboboxChip key={option.value} removeLabel={t("relationshipPicker.remove", { item: option.label })}>
                  {option.label}
                </ComboboxChip>
              ))
            }
          </ComboboxValue>
          <ComboboxChipsInput {...common} />
        </ComboboxChips>
        <ComboboxContent anchor={anchor}>
          <ComboboxList>
            {(option: RelationshipOption) => (
              <ComboboxItem
                key={option.value}
                value={option}
                disabled={optionIsDisabled(option)}
                className="py-1.5 pr-7 pl-2"
              >
                {optionContent(option)}
              </ComboboxItem>
            )}
          </ComboboxList>
          <ComboboxEmpty>{emptyMessage}</ComboboxEmpty>
        </ComboboxContent>
      </Combobox>
    </>
  );
}
