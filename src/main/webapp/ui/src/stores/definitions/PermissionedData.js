// @flow

/*
 * This general purpose type wraps given type, T, in a disjoint object union
 * to model the fact that the wrapped value is not available when the user
 * lacks the necessary permissions.
 */

type HasAccess<T> = {|
  isAccessible: true,
  value: T,
|};

type NoAccess = {|
  isAccessible: false,
|};

export type Permissioned<T> = HasAccess<T> | NoAccess;

/*
 * Below are some utility functions for working with instances of
 * Permissioned data
 */

export function mapPermissioned<T, U>(
  permissionedData: Permissioned<T>,
  f: (T) => U
): Permissioned<U> {
  if (!permissionedData.isAccessible) return permissionedData;
  return {
    isAccessible: true,
    value: f(permissionedData.value),
  };
}

export function orElseIfNoAccess<T>(
  permissionedData: Permissioned<T>,
  orElse: T
): T {
  if (!permissionedData.isAccessible) return orElse;
  return permissionedData.value;
}
