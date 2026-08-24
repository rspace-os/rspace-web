import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import type { ResolvedFieldConfig } from "@/modules/common/collection/collectionConfig";
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
import type { ControlProps, FieldRendererProps, RelationshipOption } from "../RenderFields.types";

function clearedValue(nullable: boolean | undefined): null | undefined {
  return nullable ? null : undefined;
}

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
}: Omit<ControlProps<TDocument>, "fieldConfig"> & {
  fieldConfig: Extract<ResolvedFieldConfig<TDocument>, { type: "relationship" }>;
}) {
  const { t } = useTranslation("common");
  const anchor = useComboboxAnchor();
  const options = relationshipOptions[fieldConfig.relationTo] ?? [];
  const isDisabled = disabled || fieldConfig.readOnly;
  const common = {
    id,
    name: fieldConfig.name,
    disabled: isDisabled,
    required: fieldConfig.required,
    "aria-describedby": describedBy,
    "aria-invalid": invalid || undefined,
  };

  const source = relationshipSources[fieldConfig.relationTo];
  if (source) {
    return (
      <Suspense fallback={<Skeleton className="h-9 w-full rounded-sm" />}>
        <RelationshipPicker
          source={source}
          availabilitySource={relationshipOptionAvailability[fieldConfig.relationTo]}
          value={relationshipGlobalId(fieldApi.input, source.globalIdPrefix)}
          onChange={(globalId) => {
            const id = databaseId(source, globalId);
            fieldApi.onChange(
              id === null ? clearedValue(fieldConfig.nullable) : { relationTo: fieldConfig.relationTo, value: id },
            );
          }}
          disabled={isDisabled}
          id={id}
          name={fieldConfig.name}
          required={fieldConfig.required}
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
            option ? { relationTo: fieldConfig.relationTo, value: option.value } : clearedValue(fieldConfig.nullable),
          )
        }
      >
        <ComboboxInput
          {...common}
          className="rounded-sm"
          clearLabel={t("collectionForm.actions.clear", { field: label })}
          placeholder={t("collectionForm.relationship.search", { field: label })}
          showClear={!fieldConfig.required}
          triggerLabel={t("collectionForm.actions.openOptions", { field: label })}
          ref={fieldApi.props.ref}
          onFocus={fieldApi.props.onFocus}
          onBlur={fieldApi.props.onBlur}
        />
        <ComboboxContent>
          <ComboboxList>
            {(option: RelationshipOption) => (
              <ComboboxItem key={option.value} value={option}>
                {option.content ?? option.label}
              </ComboboxItem>
            )}
          </ComboboxList>
          <ComboboxEmpty>{t("collectionForm.relationship.empty")}</ComboboxEmpty>
        </ComboboxContent>
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
          placeholder={t("collectionForm.relationship.search", { field: label })}
          ref={fieldApi.props.ref}
          onFocus={fieldApi.props.onFocus}
          onBlur={fieldApi.props.onBlur}
        />
      </ComboboxChips>
      <ComboboxContent anchor={anchor}>
        <ComboboxList>
          {(option: RelationshipOption) => (
            <ComboboxItem key={option.value} value={option}>
              {option.content ?? option.label}
            </ComboboxItem>
          )}
        </ComboboxList>
        <ComboboxEmpty>{t("collectionForm.relationship.empty")}</ComboboxEmpty>
      </ComboboxContent>
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
