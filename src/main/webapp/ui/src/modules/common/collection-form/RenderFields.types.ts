import type { FieldStore, FormSchema, FormStore } from "@formisch/react";
import type React from "react";
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

/** What a ui field is handed. The store is live, so `useField` inside re-renders on edits. */
export type UiFieldProps = {
  form: FormStore;
  disabled: boolean;
};

/**
 * An arbitrary component placed in the field list. Use it for content that reacts to the values
 * being edited rather than editing one of them itself, such as a conflict or preview panel. It is
 * a component rather than a render callback so that the hooks it uses sit behind their own
 * boundary.
 */
/**
 * Decides whether a section, row or ui block is shown, from the values currently being edited.
 * It re-runs on every edit. A hidden group keeps its values; only its controls are removed.
 * A single field uses `form.condition` on its own config instead.
 */
export type GroupCondition<TDocument> = (context: { data: Partial<TDocument> }) => boolean;

export type UiFieldConfig<TDocument = Record<string, unknown>> = {
  type: "ui";
  /** Distinguishes sibling ui fields; also the React key. */
  name: string;
  component: React.ComponentType<UiFieldProps>;
  condition?: GroupCondition<TDocument>;
};

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
  | SectionFieldConfig<TDocument>
  | UiFieldConfig<TDocument>;

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
