//@flow
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
  const [storedValue, setStoredValue] = useState(() => {
    const item = window.sessionStorage.getItem(key);
    return item ? JSON.parse(item) : defaultValue;
  });

  const setValue = (value: ((T) => T) | T): void => {
    const valueToStore =
      // $FlowExpectedError[incompatible-use] T can't be a function, so cast is fine
      typeof value === "function" ? (value: (T) => T)(storedValue) : (value: T);

    setStoredValue(valueToStore);
    window.sessionStorage.setItem(key, JSON.stringify(valueToStore));
  };

  return [storedValue, setValue];
}
