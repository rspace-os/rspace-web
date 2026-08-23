import type { CSSProperties, ReactElement, ReactNode } from "react";

export type FieldName<TDocument> = Extract<keyof TDocument, string>;

/** A direct field or one field of a configured relationship target. */
export type SearchSelector<TDocument> = FieldName<TDocument> | `${FieldName<TDocument>}.${string}`;

export type SortDirection = "asc" | "desc";

export type SortRule<TDocument> = {
  field: FieldName<TDocument>;
  direction: SortDirection;
};

export type FilterOperator =
  | "equals"
  | "notEquals"
  | "greaterThan"
  | "greaterThanOrEqual"
  | "lessThan"
  | "lessThanOrEqual"
  | "in"
  | "notIn"
  | "contains"
  | "matches"
  | "exists";

export type HeaderRendererContext<TDocument> = {
  config: ResolvedCollectionConfig<TDocument>;
  field: ResolvedFieldConfig<TDocument>;
};

export type CellRendererContext<TDocument> = HeaderRendererContext<TDocument> & {
  row: TDocument;
  value: TDocument[FieldName<TDocument>];
};

export type HeaderRenderer<TDocument> = (context: HeaderRendererContext<TDocument>) => ReactNode;
export type CellRenderer<TDocument> = (context: CellRendererContext<TDocument>) => ReactNode;

export type FieldCapabilities = {
  sortable?: boolean;
  filterOperators?: readonly FilterOperator[];
  supportsWildcards?: boolean;
};

export type ResolvedFieldCapabilities = {
  sortable: boolean;
  filterOperators: readonly FilterOperator[];
  supportsWildcards: boolean;
};

export type FieldListConfig<TDocument> = {
  align?: "start" | "center" | "end";
  /** Initial column width in CSS pixels. */
  width?: number;
  /** Smallest user-selected column width in CSS pixels. */
  minWidth?: number;
  descriptionKey?: string;
  description?: string;
  renderHeader?: HeaderRenderer<TDocument>;
  renderCell?: CellRenderer<TDocument>;
  dependencies?: readonly FieldName<TDocument>[];
  /** Card-presentation layout for this field. */
  card?: {
    /** Stack the label above the value and span the complete card body. */
    fullWidth?: boolean;
  };
};

export type FieldConditionContext<TDocument> = {
  data: Partial<TDocument>;
  value: TDocument[FieldName<TDocument>] | undefined;
  field: {
    errors: readonly string[] | null;
    isDirty: boolean;
    isEdited: boolean;
    isTouched: boolean;
    isValid: boolean;
  };
  form: {
    errors: readonly string[] | null;
    isDirty: boolean;
    isEdited: boolean;
    isSubmitting: boolean;
    isSubmitted: boolean;
    isTouched: boolean;
    isValid: boolean;
    isValidating: boolean;
  };
};

export type FieldFormConfig<TDocument, TWidget extends string> = {
  descriptionKey?: string;
  widget?: TWidget;
  width?: CSSProperties["width"];
  condition?: (context: FieldConditionContext<TDocument>) => boolean;
};

export type SelectOption =
  | string
  | {
      content?: ReactNode;
      label: string;
      value: string;
    }
  | {
      textValue: string;
      label: ReactElement;
      value: string;
    };

export function hierarchicalFieldLabel(viaLabel: string, ownLabel: string): string {
  return viaLabel === "" ? ownLabel : `${viaLabel} \u2192 ${ownLabel}`;
}

export function fieldLabel(field: { labelKey: string; label?: string }, translate: (key: string) => string): string {
  return field.label ?? translate(field.labelKey);
}

export function selectOptionText(option: SelectOption): string {
  if (typeof option === "string") return option;
  return "textValue" in option ? option.textValue : option.label;
}

export type SelectFieldFormConfig<TDocument> = FieldFormConfig<TDocument, "select" | "card"> & {
  isOptionDisabled?: (option: SelectOption, data: Partial<TDocument>) => boolean;
};

export type FieldOrigin = {
  kind: "relationshipTarget" | "runtimeField";
  groupLabelKey: string;
  sourceLabel?: string;
  stableId?: string;
  namespace?: string;
  viaLabel?: string;
};

type BaseFieldConfig<
  TDocument,
  TWidget extends string,
  TFormConfig extends FieldFormConfig<TDocument, TWidget> = FieldFormConfig<TDocument, TWidget>,
> = {
  name: FieldName<TDocument>;
  labelKey: string;
  label?: string;
  required?: boolean;
  nullable?: boolean;
  readOnly?: boolean;
  capabilities?: FieldCapabilities;
  origin?: FieldOrigin;
  list?: false | FieldListConfig<TDocument>;
  form?: false | TFormConfig;
};

export type FieldConfig<TDocument> =
  | (BaseFieldConfig<TDocument, "text" | "textarea"> & {
      type: "text";
      maximumLength?: number;
    })
  | (BaseFieldConfig<TDocument, "number"> & {
      type: "number";
    })
  | (BaseFieldConfig<TDocument, "checkbox"> & {
      type: "boolean";
    })
  | (BaseFieldConfig<TDocument, "dateTime"> & {
      type: "dateTime";
    })
  | (BaseFieldConfig<TDocument, "select" | "card", SelectFieldFormConfig<TDocument>> & {
      type: "select";
      options: readonly SelectOption[];
    })
  | (BaseFieldConfig<TDocument, "relationship"> & {
      type: "relationship";
      relationTo: string;
      hasMany: boolean;
    });

type ResolveField<TField> = TField extends { capabilities?: FieldCapabilities }
  ? Omit<TField, "capabilities"> & {
      capabilities: ResolvedFieldCapabilities;
    }
  : never;

export type ResolvedFieldConfig<TDocument> = ResolveField<FieldConfig<TDocument>>;

/**
 * `TId` and `TTitle` carry the literal `idField` and `useAsTitle` names. A request always selects
 * these two fields, so `CollectionRow` uses them to state which fields a row always carries. Declare
 * a configuration with `satisfies` rather than a type annotation, because an annotation widens both
 * names back to every field name and the row type then states nothing.
 */
export type CollectionConfig<
  TDocument,
  TId extends FieldName<TDocument> = FieldName<TDocument>,
  TTitle extends FieldName<TDocument> = FieldName<TDocument>,
> = {
  slug: string;
  idField: TId;
  defaultSort?: readonly SortRule<TDocument>[];
  useAsTitle: TTitle;
  defaultColumns: readonly FieldName<TDocument>[];
  listSearchableFields?: readonly SearchSelector<TDocument>[];
  pagination?: {
    defaultLimit: number;
    limits: readonly number[];
  };
  labels: {
    singularKey: string;
    pluralKey: string;
    descriptionKey?: string;
  };
  runtimeNamespaces?: readonly string[];
  fields: readonly FieldConfig<TDocument>[];
};

export type ResolvedCollectionConfig<
  TDocument,
  TId extends FieldName<TDocument> = FieldName<TDocument>,
  TTitle extends FieldName<TDocument> = FieldName<TDocument>,
> = Omit<CollectionConfig<TDocument, TId, TTitle>, "fields"> & {
  fields: readonly ResolvedFieldConfig<TDocument>[];
  runtimeSources?: readonly RuntimeNamespaceSummary[];
};

export type RuntimeNamespaceSummary = {
  namespace: string;
  viaLabel: string;
  catalog: string;
  maximumLimit: number;
  filterable: boolean;
  columnSelectable: boolean;
};

/**
 * One row of a collection. A row carries `TAlways` at all times and each other field only when the
 * request projection selected it.
 */
export type CollectionRow<TDocument, TAlways extends FieldName<TDocument>> = Pick<TDocument, TAlways> &
  Partial<Omit<TDocument, TAlways>>;
