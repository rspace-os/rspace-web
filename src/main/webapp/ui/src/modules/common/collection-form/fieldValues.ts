/**
 * Empty nullable fields use `null`; other fields use `undefined`. JSON omission and explicit null
 * have different update semantics, so required-value checks stay in the form schema.
 */
export function emptyNullableValue(nullable: true): null;
export function emptyNullableValue(nullable: false | undefined): undefined;
export function emptyNullableValue(nullable: boolean | undefined): null | undefined;
export function emptyNullableValue(nullable: boolean | undefined): null | undefined {
  return nullable ? null : undefined;
}
