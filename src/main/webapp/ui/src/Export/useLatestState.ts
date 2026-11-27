import { useCallback, useRef, useState } from "react";

/**
 * React state that is also readable synchronously, outside of render, at its
 * latest value.
 *
 * The export wizard registers validation functions once and calls helpers
 * that read state straight after updating it, within the same event handler.
 * Closures over the rendered value would see stale data in both cases, so
 * those callers read `getLatest()` instead. Render code should keep using the
 * returned value so that React re-renders on change.
 *
 * @param initialState Lazily computes the initial state.
 * @return The state as of this render, an updater that applies a pure
 *         function to the state, and a stable getter for the state with
 *         every update made so far applied.
 */
export default function useLatestState<T>(initialState: () => T): [T, (update: (latest: T) => T) => void, () => T] {
  const [state, setState] = useState(initialState);
  const latest = useRef(state);

  const update = useCallback((f: (latest: T) => T) => {
    latest.current = f(latest.current);
    /*
     * Passing the function, rather than the value just computed, lets React
     * rebase it onto the state of each render. Otherwise a synchronous update
     * would apply the result of an earlier, still pending transition.
     */
    setState(f);
  }, []);

  const getLatest = useCallback(() => latest.current, []);

  return [state, update, getLatest];
}
