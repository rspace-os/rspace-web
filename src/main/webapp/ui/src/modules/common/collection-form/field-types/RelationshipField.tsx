import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { RelationshipPicker } from "@/modules/common/relationship-picker/RelationshipPicker";
import { databaseId } from "@/modules/common/relationship-picker/relationshipOptionQueries";
import { relationshipSources } from "@/modules/common/relationship-picker/relationshipSources";
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
import { Skeleton } from "@/modules/common/ui/skeleton";
import { FormField } from "../FormField";
import { emptyNullableValue } from "../fieldValues";
import type { ControlProps, FieldRendererProps, RelationshipOption } from "../RenderFields.types";

function relationshipIdentifiers(value: unknown): readonly (string | number)[] {
  if (typeof value === "string" || typeof value === "number") return [value];
  if (typeof value !== "object" || value === null) return [];

  const relationship = value as { globalId?: unknown; value?: unknown };
  const identifiers: (string | number)[] = [];
  if (typeof relationship.globalId === "string") identifiers.push(relationship.globalId);
  if (typeof relationship.value === "string" || typeof relationship.value === "number") {
    identifiers.push(relationship.value);
  } else if (typeof relationship.value === "object" && relationship.value !== null && "id" in relationship.value) {
    const id = relationship.value.id;
    if (typeof id === "string" || typeof id === "number") identifiers.push(id);
  }
  return identifiers;
}

function relationshipGlobalId(value: unknown, prefix: string): string {
  const identifiers = relationshipIdentifiers(value);
  const globalId = identifiers.find(
    (identifier): identifier is string =>
      typeof identifier === "string" && identifier.toUpperCase().startsWith(prefix.toUpperCase()),
  );
  if (globalId) return globalId;

  const id = identifiers.find(
    (identifier) => typeof identifier === "number" || (typeof identifier === "string" && /^\d+$/.test(identifier)),
  );
  return id === undefined ? "" : `${prefix}${id}`;
}

function RelationshipOptionsContent({
  anchor,
  emptyMessage,
  label,
}: {
  anchor?: ReturnType<typeof useComboboxAnchor>;
  emptyMessage: string;
  label: string;
}) {
  return (
    <ComboboxContent anchor={anchor}>
      <ComboboxList aria-label={label}>
        {(option: RelationshipOption) => (
          <ComboboxItem key={option.value} value={option}>
            {option.content ?? option.label}
          </ComboboxItem>
        )}
      </ComboboxList>
      <ComboboxEmpty>{emptyMessage}</ComboboxEmpty>
    </ComboboxContent>
  );
}

function RelationshipFieldControl<TDocument extends Record<string, unknown>>({
  describedBy,
  disabled,
  fieldApi,
  fieldConfig,
  id,
  invalid,
  label,
  relationshipOptionAvailability,
  relationshipOptions,
}: ControlProps<TDocument, "relationship">) {
  const { t } = useTranslation("common");
  const anchor = useComboboxAnchor();
  const options = relationshipOptions[fieldConfig.relationTo] ?? [];
  const isDisabled = disabled || fieldConfig.readOnly;
  const common = {
    id,
    name: fieldConfig.name,
    disabled: isDisabled,
    required: fieldConfig.required,
  };

  const source = relationshipSources[fieldConfig.relationTo];
  if (source) {
    const globalIdPrefix = source.globalIdPrefix;
    const value = globalIdPrefix
      ? fieldConfig.hasMany
        ? (Array.isArray(fieldApi.input) ? fieldApi.input : [])
            .map((item: unknown) => relationshipGlobalId(item, globalIdPrefix))
            .filter((globalId) => globalId !== "")
            .join(",")
        : relationshipGlobalId(fieldApi.input, globalIdPrefix)
      : "";

    return (
      <Suspense fallback={<Skeleton className="h-9 w-full rounded-sm" />}>
        <RelationshipPicker
          source={source}
          availabilitySource={relationshipOptionAvailability[fieldConfig.relationTo]}
          value={value}
          multiple={fieldConfig.hasMany}
          onChange={(globalId) => {
            if (fieldConfig.hasMany) {
              fieldApi.onChange(
                globalId
                  .split(",")
                  .map((selectedGlobalId) => databaseId(source, selectedGlobalId))
                  .filter((id) => id !== null),
              );
              return;
            }

            const id = databaseId(source, globalId);
            fieldApi.onChange(
              id === null
                ? emptyNullableValue(fieldConfig.nullable)
                : { relationTo: fieldConfig.relationTo, value: id },
            );
          }}
          {...common}
          autoFocus={fieldApi.props.autoFocus}
          ariaDescribedBy={describedBy}
          ariaInvalid={invalid}
          ariaLabel={label}
          className="rounded-sm"
          inputRef={fieldApi.props.ref}
          onFocus={fieldApi.props.onFocus}
          onBlur={fieldApi.props.onBlur}
          showClear={!fieldConfig.required}
        />
      </Suspense>
    );
  }

  if (!fieldConfig.hasMany) {
    const identifiers = relationshipIdentifiers(fieldApi.input);
    const selected = options.find((option) => identifiers.includes(option.value)) ?? null;
    return (
      <Combobox
        items={options}
        value={selected}
        disabled={isDisabled}
        isItemEqualToValue={(option, value) => option.value === value.value}
        itemToStringLabel={(option) => option.label}
        onValueChange={(option) =>
          fieldApi.onChange(
            option
              ? { relationTo: fieldConfig.relationTo, value: option.value }
              : emptyNullableValue(fieldConfig.nullable),
          )
        }
      >
        <ComboboxInput
          {...common}
          aria-label={label}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          className="rounded-sm"
          clearLabel={t("collectionForm.actions.clear", { field: label })}
          placeholder={t("collectionForm.relationship.search", { field: label })}
          showClear={!fieldConfig.required}
          triggerLabel={t("collectionForm.actions.openOptions", { field: label })}
          ref={fieldApi.props.ref}
          onFocus={fieldApi.props.onFocus}
          onBlur={fieldApi.props.onBlur}
        />
        <RelationshipOptionsContent label={label} emptyMessage={t("collectionForm.relationship.empty")} />
      </Combobox>
    );
  }

  const values: readonly unknown[] = Array.isArray(fieldApi.input) ? fieldApi.input : [];
  const selected = options.filter((option) => values.includes(option.value));
  return (
    <Combobox
      items={options}
      value={selected}
      multiple
      disabled={isDisabled}
      isItemEqualToValue={(option, value) => option.value === value.value}
      itemToStringLabel={(option) => option.label}
      onValueChange={(nextOptions) => fieldApi.onChange(nextOptions.map((option) => option.value))}
    >
      <ComboboxChips ref={anchor} className="rounded-sm">
        <ComboboxValue>
          {(nextOptions: RelationshipOption[]) =>
            nextOptions.map((option) => (
              <ComboboxChip
                key={option.value}
                removeLabel={t("collectionForm.relationship.remove", {
                  field: label,
                  item: option.label,
                })}
              >
                {option.label}
              </ComboboxChip>
            ))
          }
        </ComboboxValue>
        <ComboboxChipsInput
          {...common}
          aria-label={label}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          placeholder={t("collectionForm.relationship.search", { field: label })}
          ref={fieldApi.props.ref}
          onFocus={fieldApi.props.onFocus}
          onBlur={fieldApi.props.onBlur}
        />
      </ComboboxChips>
      <RelationshipOptionsContent anchor={anchor} label={label} emptyMessage={t("collectionForm.relationship.empty")} />
    </Combobox>
  );
}

export function RelationshipField<TDocument extends Record<string, unknown>>(
  props: FieldRendererProps<TDocument, "relationship">,
) {
  return (
    <FormField {...props}>
      {(controlProps) => <RelationshipFieldControl {...controlProps} fieldConfig={props.fieldConfig} />}
    </FormField>
  );
}
