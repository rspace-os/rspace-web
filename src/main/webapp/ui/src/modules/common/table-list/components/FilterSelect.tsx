import type { Namespace } from "i18next";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { CollectionTranslationKey } from "@/modules/common/collection/config";
import {
  Combobox,
  ComboboxCollection,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxGroup,
  ComboboxInput,
  ComboboxItem,
  ComboboxLabel,
  ComboboxList,
} from "@/modules/common/ui/combobox";

export type FilterSelectOption = {
  value: string;
  label: string;
  selectedLabel?: string;
  groupLabelKey: CollectionTranslationKey | null;
  groupLabel?: string;
};

type OptionGroup = {
  key: string;
  heading: string | null;
  items: readonly FilterSelectOption[];
};

export function FilterSelect({
  options,
  value,
  ariaLabel,
  labels,
  onChange,
}: {
  options: readonly FilterSelectOption[];
  value: string;
  ariaLabel: string;
  labels: { placeholder: string; noMatch: string; clear: string; trigger: string };
  onChange: (value: string) => void;
}) {
  const { t } = useTranslation<Namespace>("common");
  const [open, setOpen] = useState(false);

  const byKey = new Map<string, OptionGroup & { items: FilterSelectOption[] }>();
  for (const option of options) {
    const key = option.groupLabel ?? option.groupLabelKey ?? "";
    const group = byKey.get(key) ?? {
      key,
      heading: option.groupLabel ?? (option.groupLabelKey === null ? null : t(option.groupLabelKey)),
      items: [],
    };
    group.items.push(option);
    byKey.set(key, group);
  }
  const groups: OptionGroup[] = [...byKey.values()];

  const selected = options.find((option) => option.value === value) ?? null;

  return (
    <Combobox
      items={groups}
      open={open}
      onOpenChange={setOpen}
      value={selected}
      itemToStringLabel={(option: FilterSelectOption) => option.selectedLabel ?? option.label}
      onValueChange={(option: FilterSelectOption | null) => {
        if (option) onChange(option.value);
      }}
    >
      <ComboboxInput
        aria-label={ariaLabel}
        className="h-8 rounded-sm text-xs"
        clearLabel={labels.clear}
        placeholder={labels.placeholder}
        showClear={false}
        triggerLabel={labels.trigger}
      />
      <ComboboxContent>
        <ComboboxList>
          {(group: OptionGroup) => (
            <ComboboxGroup key={group.key} items={group.items}>
              {group.heading === null ? null : <ComboboxLabel>{group.heading}</ComboboxLabel>}
              <ComboboxCollection>
                {(option: FilterSelectOption) => (
                  <ComboboxItem key={option.value} value={option} className="py-1.5 text-xs">
                    {option.label}
                  </ComboboxItem>
                )}
              </ComboboxCollection>
            </ComboboxGroup>
          )}
        </ComboboxList>
        <ComboboxEmpty>{labels.noMatch}</ComboboxEmpty>
      </ComboboxContent>
    </Combobox>
  );
}
