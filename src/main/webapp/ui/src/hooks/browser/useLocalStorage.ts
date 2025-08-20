import { useState } from "react";
import { type UseState } from "../../util/types";

/**
 * Like useState, but synchronised with local storage.
 *
 * Note that the code assumes that the value stored in local storage is a valid
 * JSON string of the same type as the default value.
 */
export default function useLocalStorage<T>(
  key: string,
  defaultValue: T,
): UseState<T> {
  const [storedValue, setStoredValue] = useState(() => {
    const item = window.localStorage.getItem(key);
    return item ? (JSON.parse(item) as T) : defaultValue;
  });

  const setValue = (value: ((oldValue: T) => T) | T): void => {
    const valueToStore =
      typeof value === "function"
        ? (value as (oldValue: T) => T)(storedValue)
        : value;

    setStoredValue(valueToStore);
    window.localStorage.setItem(key, JSON.stringify(valueToStore));
  };

  return [storedValue, setValue];
}
