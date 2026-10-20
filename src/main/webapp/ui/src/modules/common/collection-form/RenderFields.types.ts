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

/**
 * `stacked` puts each label above its control. `inline` stacks in narrow
 * containers, then uses a shared 12rem label column with descriptions and
 * errors beneath the controls. Inline expects a flat field list.
 */
export type FieldLayout = "stacked" | "inline";

/**
 * `comfortable` is the page default. `compact` tightens the gaps so a form fits a
 * constrained surface such as a popover, without changing any control's own size.
 */
export type FieldDensity = "comfortable" | "compact";

/**
 * Decides whether a section or row is shown, from the values currently being edited.
 * It re-runs on every edit. A hidden group keeps its values; only its controls are removed.
 * A single field uses `form.condition` on its own config instead.
 */
export type GroupCondition<TDocument> = (context: { data: Partial<TDocument> }) => boolean;

export type SectionFieldConfig<TDocument> = {
  type: "section";
  labelKey: string;
  fields: readonly FormFieldConfig<TDocument>[];
  variant?: "card" | "transparent";
  condition?: GroupCondition<TDocument>;
};

export type RowFieldConfig<TDocument> = {
  type: "row";
  fields: readonly ResolvedFieldConfig<TDocument>[];
  condition?: GroupCondition<TDocument>;
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
  layout?: FieldLayout;
  density?: FieldDensity;
  className?: string;
};

/** What a section or row hands on to the RenderFields it nests. */
export type NestedFieldsProps = Required<
  Pick<
    RenderFieldsProps<Record<string, unknown>>,
    "form" | "relationshipOptionAvailability" | "relationshipOptions" | "disabled" | "density"
  >
>;

export type FieldRendererProps<
  TDocument extends Record<string, unknown>,
  TType extends ResolvedFieldConfig<TDocument>["type"],
> = {
  disabled: boolean;
  fieldConfig: Extract<ResolvedFieldConfig<TDocument>, { type: TType }>;
  form: FormStore;
  layout?: FieldLayout;
  relationshipOptionAvailability: RelationshipOptionAvailability;
  relationshipOptions: RelationshipOptions;
};

export type ControlProps<
  TDocument,
  TType extends ResolvedFieldConfig<TDocument>["type"] = ResolvedFieldConfig<TDocument>["type"],
> = {
  describedBy: string | undefined;
  disabled: boolean;
  fieldApi: FieldStore<FormSchema, [string]>;
  fieldConfig: Extract<ResolvedFieldConfig<TDocument>, { type: TType }>;
  id: string;
  invalid: boolean;
  label: string;
  relationshipOptionAvailability: RelationshipOptionAvailability;
  relationshipOptions: RelationshipOptions;
};
