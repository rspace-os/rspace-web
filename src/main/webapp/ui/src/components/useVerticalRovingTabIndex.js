//@flow

import React from "react";
import { modulo } from "../util/Util";

export default function useVerticalRovingTabIndex<RefComponent: HTMLElement>({
  max,
}: {|
  /**
   * The index of the last element of the vertical list, where the indexing is
   * 0-based.
   */
  max: number,
|}): {|
  /**
   * Given the index of an element of the vertical list, return that element's
   * current tab index.
   */
  getTabIndex: (number) => -1 | 0,

  /**
   * Given the index of an element of the vertical list, return a ref if it has
   * a tab index of 0. Otherwise returns null.
   */
  getRef: (number) => null | {| current: RefComponent | null |},

  /**
   * The set of the event handlers that must be attached to the container
   * component of all of the elements of the vertical list.
   */
  eventHandlers: {|
    onFocus: () => void,
    onBlur: () => void,
    onKeyDown: (KeyboardEvent) => void,
  |},
|} {
  const [rovingTabIndex, setRovingTabIndex] = React.useState(0);
  const [hasFocus, setHasFocus] = React.useState(false);
  const refOfRovingTabIndex = React.useRef<null | RefComponent>(null);

  React.useEffect(() => {
    if (hasFocus) refOfRovingTabIndex.current?.focus();
  }, [rovingTabIndex]);

  function getTabIndex(i: number) {
    if (i === rovingTabIndex) return 0;
    return -1;
  }

  function getRef(i: number) {
    if (i === rovingTabIndex) return refOfRovingTabIndex;
    return null;
  }

  function onFocus() {
    setHasFocus(true);
  }

  function onBlur() {
    setHasFocus(false);
  }

  function onKeyDown(e: KeyboardEvent) {
    if (e.key === "ArrowUp") {
      setRovingTabIndex(modulo(rovingTabIndex - 1, max + 1));
    } else if (e.key === "ArrowDown") {
      setRovingTabIndex(modulo(rovingTabIndex + 1, max + 1));
    }
  }

  return { getTabIndex, getRef, eventHandlers: { onFocus, onBlur, onKeyDown } };
}
