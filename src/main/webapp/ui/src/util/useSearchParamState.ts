import { useSearchParams } from "react-router-dom";

/**
 * Single-page applications often have pieces of state that should be
 * synchronised with the browser's location search params so that back,
 * forward, and bookmarkering work. This custom hook abstracts over that
 * synchronisation, providing an interface similar to React.useState but where
 * the initial value is populated from the search params and the passed
 * `fallback` is only used where the keys are not present in the current URL.
 */
export function useSearchParamState<
  T extends { [key: string]: string | string[] }
>(fallback: T): [T, (newState: T) => void] {
  const [searchParams, setSearchParams] = useSearchParams();

  function calculateState(sp: URLSearchParams, fb: T) {
    const tmp = { ...fb };
    for (const [key, value] of sp.entries()) {
      if (key in tmp) {
        (tmp as any)[key] = value;
      }
    }
    return tmp;
  }

  return [calculateState(searchParams, fallback), setSearchParams];
}
