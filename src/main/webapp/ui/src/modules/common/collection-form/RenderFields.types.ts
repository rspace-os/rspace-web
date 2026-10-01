import type { ReactNode } from "react";

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
