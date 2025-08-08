import { useState } from "react";
import { type UseState } from "./types";

/**
 * Like useState, but synchronised with session storage so that the value is
 * persisted across page refreshes.
 */
export default function useLocalStorage<T>(
  key: string,
  defaultValue: T
): UseState<T> {
  const [storedValue, setStoredValue] = useState<T>(() => {
    const item = window.sessionStorage.getItem(key);
    return item ? (JSON.parse(item) as T) : defaultValue;
  });

  const setValue = (value: ((t: T) => T) | T): void => {
    const valueToStore =
      typeof value === "function" ? (value as (t: T) => T)(storedValue) : value;

    setStoredValue(valueToStore);
    window.sessionStorage.setItem(key, JSON.stringify(valueToStore));
  };

  return [storedValue, setValue];
}
