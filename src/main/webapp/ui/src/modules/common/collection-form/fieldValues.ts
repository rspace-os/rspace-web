export function emptyNullableValue(nullable: true): null;
export function emptyNullableValue(nullable: false | undefined): undefined;
export function emptyNullableValue(nullable: boolean | undefined): null | undefined;
export function emptyNullableValue(nullable: boolean | undefined): null | undefined {
  return nullable ? null : undefined;
}
