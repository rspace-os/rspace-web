import { Optional } from "./optional";

/**
 * Safely get a value from a map, returning an optional value.
 */
export const get = <K, V>(map: Map<K, V>, key: K): Optional<V> => {
  const v = map.get(key);
  if (v === null || typeof v === "undefined") return Optional.empty();
  return Optional.present(v);
};
