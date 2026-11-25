import { useState } from "react";
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
  const normalizedOptions: Option[] = options.map((option) =>
    typeof option === "string" ? { label: option, value: option } : option,
  );
  const selected = value.map(
    (selectedValue) =>
      normalizedOptions.find((option) => option.value === selectedValue) ?? {
        label: selectedValue,
        value: selectedValue,
      },
  );
  const customValue = inputValue.trim();
  const items = [...normalizedOptions];
  for (const option of selected) {
    if (!items.some((candidate) => candidate.value === option.value)) items.push(option);
  }
  if (allowCustomValues && customValue !== "" && !items.some((option) => option.value === customValue)) {
    items.unshift({ label: customValue, value: customValue });
  }

  return (
    <Combobox
      items={items}
      multiple
      autoHighlight
      value={selected}
      inputValue={inputValue}
      disabled={disabled}
      isItemEqualToValue={(option, other) => option.value === other.value}
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
