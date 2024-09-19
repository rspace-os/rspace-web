//@flow

import React from "react";
import { useSearchParams } from "react-router-dom";

/*
 * Single-page applications often have pieces of state that should be
 * synchronised with the browser's location search params so that back,
 * forward, and bookmarkering work. This custom hook abstracts over that
 * synchronisation, providing an interface similar to React.useState but where
 * the initial value is populated from the search params and the passed
 * `fallback` is only used where the keys are not present in the current URL.
 */

export function useSearchParamState<
  T: { [key: string]: string | Array<string>, ... }
>(fallback: T): [T, (T) => void] {
  const [searchParams, setSearchParams] = useSearchParams();

  function calculateState(sp: URLSearchParams, fb: T) {
    const tmp: T = { ...fb };
    Object.assign(tmp, Object.fromEntries(sp.entries()));
    return tmp;
  }

  const [state, setState] = React.useState<T>(
    calculateState(searchParams, fallback)
  );

  /*
   * If the browser changes the searchParams -- such as because the user taps
   * the back button -- or the JS code changes the fallback object, then we
   * should synchronise those changes
   */
  React.useEffect(() => {
    setState(calculateState(searchParams, fallback));
  }, [searchParams, fallback]);

  return [
    state,
    (newValue: T) => {
      setState(newValue);
      /*
       * If the JS code changes the state then we should synchronise those
       * changes back to the URL.
       */
      setSearchParams(newValue);
    },
  ];
}
