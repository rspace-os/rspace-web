import type { FieldStore, FormSchema, FormStore } from "@formisch/react";
import type { ReactNode } from "react";
import type { ResolvedFieldConfig } from "@/modules/common/collection/collectionConfig";

export type RelationshipOption = {
  content?: ReactNode;
  label: string;
  value: string | number;
};

export type UnavailableRelationshipOption = {
  reason: string;
  relatedRecordId?: string | number;
};

export type RelationshipOptionAvailabilitySource = {
  queryKey: readonly unknown[];
  loadUnavailable: (
    values: readonly string[],
    token: string | undefined,
    signal: AbortSignal,
  ) => Promise<Readonly<Record<string, UnavailableRelationshipOption>>>;
  renderUnavailable: (option: RelationshipOption, status: UnavailableRelationshipOption) => ReactNode;
  renderAction?: (option: RelationshipOption, status: UnavailableRelationshipOption) => ReactNode;
};

export type ToOneRelationshipValue<
  TValue extends string | number | { id: string | number } = string | number | { id: string | number },
> = {
  relationTo: string;
  value: TValue;
  globalId?: string;
};

export type RelationshipOptions = Readonly<Record<string, readonly RelationshipOption[]>>;

export type RelationshipOptionAvailability = Readonly<Record<string, RelationshipOptionAvailabilitySource | undefined>>;

export type SectionFieldConfig<TDocument> = {
  type: "section";
  labelKey: string;
  fields: readonly FormFieldConfig<TDocument>[];
  variant?: "card" | "transparent";
};

export type RowFieldConfig<TDocument> = {
  type: "row";
  fields: readonly ResolvedFieldConfig<TDocument>[];
};

export type FormFieldConfig<TDocument> =
  | ResolvedFieldConfig<TDocument>
  | RowFieldConfig<TDocument>
  | SectionFieldConfig<TDocument>;

export type RenderFieldsProps<TDocument extends Record<string, unknown>> = {
  fields: readonly FormFieldConfig<TDocument>[];
  form: FormStore;
  relationshipOptionAvailability?: RelationshipOptionAvailability;
  relationshipOptions?: RelationshipOptions;
  disabled?: boolean;
  className?: string;
};

export type FieldRendererProps<
  TDocument extends Record<string, unknown>,
  TType extends ResolvedFieldConfig<TDocument>["type"],
> = {
  disabled: boolean;
  fieldConfig: Extract<ResolvedFieldConfig<TDocument>, { type: TType }>;
  form: FormStore;
  relationshipOptionAvailability: RelationshipOptionAvailability;
  relationshipOptions: RelationshipOptions;
};

export type ControlProps<TDocument> = {
  describedBy: string | undefined;
  disabled: boolean;
  fieldApi: FieldStore<FormSchema, [string]>;
  fieldConfig: ResolvedFieldConfig<TDocument>;
  id: string;
  invalid: boolean;
  label: string;
  relationshipOptionAvailability: RelationshipOptionAvailability;
  relationshipOptions: RelationshipOptions;
};
