import { useMemo, useState } from "react";
import {
  Combobox,
  ComboboxChip,
  ComboboxChips,
  ComboboxChipsInput,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxItem,
  ComboboxList,
  ComboboxValue,
  useComboboxAnchor,
} from "./combobox";

export type MultiSelectOption = string | { label: string; value: string };

type Option = { label: string; value: string };

function sameOption(option: Option, other: Option) {
  return option.value === other.value;
}

export function MultiSelect({
  options,
  value,
  onValueChange,
  allowCustomValues = false,
  ariaLabel,
  placeholder,
  emptyMessage,
  removeLabel,
  className,
  disabled = false,
}: {
  options: readonly MultiSelectOption[];
  value: readonly string[];
  onValueChange: (value: readonly string[]) => void;
  allowCustomValues?: boolean;
  ariaLabel: string;
  placeholder: string;
  emptyMessage: string;
  removeLabel: (label: string) => string;
  className?: string;
  disabled?: boolean;
}) {
  const anchor = useComboboxAnchor();
  const [inputValue, setInputValue] = useState("");
  const normalizedOptions = useMemo<Option[]>(
    () => options.map((option) => (typeof option === "string" ? { label: option, value: option } : option)),
    [options],
  );
  const selected = useMemo(
    () =>
      value.map(
        (selectedValue) =>
          normalizedOptions.find((option) => option.value === selectedValue) ?? {
            label: selectedValue,
            value: selectedValue,
          },
      ),
    [normalizedOptions, value],
  );
  const items = useMemo(() => {
    const customValue = inputValue.trim();
    const next = [...normalizedOptions];
    for (const option of selected) {
      if (!next.some((candidate) => candidate.value === option.value)) next.push(option);
    }
    if (allowCustomValues && customValue !== "" && !next.some((option) => option.value === customValue)) {
      next.unshift({ label: customValue, value: customValue });
    }
    return next;
  }, [allowCustomValues, inputValue, normalizedOptions, selected]);

  return (
    <Combobox
      items={items}
      multiple
      autoHighlight
      value={selected}
      inputValue={inputValue}
      disabled={disabled}
      isItemEqualToValue={sameOption}
      onInputValueChange={setInputValue}
      onValueChange={(next: Option[]) => {
        onValueChange(next.map((option) => option.value));
        setInputValue("");
      }}
    >
      <ComboboxChips ref={anchor} className={className}>
        <ComboboxValue>
          {(next: Option[]) =>
            next.map((option) => (
              <ComboboxChip key={option.value} removeLabel={removeLabel(option.label)}>
                {option.label}
              </ComboboxChip>
            ))
          }
        </ComboboxValue>
        <ComboboxChipsInput aria-label={ariaLabel} placeholder={placeholder} disabled={disabled} />
      </ComboboxChips>
      <ComboboxContent anchor={anchor}>
        <ComboboxList>
          {(option: Option) => (
            <ComboboxItem key={option.value} value={option}>
              {option.label}
            </ComboboxItem>
          )}
        </ComboboxList>
        <ComboboxEmpty>{emptyMessage}</ComboboxEmpty>
      </ComboboxContent>
    </Combobox>
  );
}
