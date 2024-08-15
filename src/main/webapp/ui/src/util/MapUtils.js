//@flow strict

import { Optional } from "./optional";

export const get = <K, V>(map: Map<K, V>, key: K): Optional<V> => {
  const v = map.get(key);
  if (v === null || typeof v === "undefined") return Optional.empty();
  return Optional.present(v);
};
