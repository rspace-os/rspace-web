//@flow

import React from "react";
import { modulo } from "../util/Util";

/**
 * A roving tab index is a pattern used to allow a keyboard-centric user to
 * more efficiently move around the web page by grouping particular controls
 * together, such that tab and shift-tab enter and leave the group and the
 * arrow keys are used to move between the elements. As such, pressing tab
 * twice will skip over all of the elements in the group, which is much faster
 * than tabbing through them all.
 *
 * This custom hook provides an abstraction over implementing a roving tab
 * index for a one-dimensional list of elements, so that either the up and down
 * arrow keys provide navigation between the elements in the list, or the left
 * and right arrow keys dow. Tab and shift-tab move the user's focus out of the
 * list, and when they return the focus resumes on the last element in the list
 * that had focus.
 *
 * There are two parts to how this custom hook is to be used:
 *
 *   1. Attach the `eventHandlers` to the component that wraps all of the
 *      elements in the list. It is this that captures the keyboard events.
 *
 *   2. Pass a `tabIndex` and a `ref` to each element in the list by calling
 *      the `getTabIndex` and `getRef` functions respectively with each
 *      element's index.
 *
 * For more information on roving tab index, see these sites:
 *  - https://web.dev/articles/control-focus-with-tabindex#use_roving_tabindex
 *  - https://www.w3.org/WAI/ARIA/apg/patterns/radio/examples/radio/
 *  - https://www.youtube.com/watch?v=uCIC2LNt0bk
 */
export default function useOneDimensionalRovingTabIndex<
  RefComponent: HTMLElement
>({
  max,
  direction = "column",
}: {|
  /**
   * The index of the last element of the vertical list, where the indexing is
   * 0-based.
   */
  max: number,

  /**
   * The dimension in which the elements of the list are laid out. Defaults to "column"
   */
  direction?: "row" | "column",
|}): {|
  /**
   * The set of the event handlers that must be attached to the container
   * component of all of the elements of the vertical list.
   */
  eventHandlers: {|
    onFocus: () => void,
    onBlur: () => void,
    onKeyDown: (KeyboardEvent) => void,
  |},
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
|} {
  const [rovingTabIndex, setRovingTabIndex] = React.useState(0);
  const [hasFocus, setHasFocus] = React.useState(false);
  const refOfRovingTabIndex = React.useRef<null | RefComponent>(null);

  React.useEffect(() => {
    if (hasFocus) refOfRovingTabIndex.current?.focus();
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * We don't need this useEffect to run when hasFocus changes because by
     * setting the tabIndex we ensure that `refOfRovingTabIndex.current` will
     * already have focus.
     */
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
    /*
     * By using modulo rather than min and max the user's focus wraps around
     * when reaching the end.
     */
    if (e.key === "ArrowUp" && direction === "column") {
      setRovingTabIndex(modulo(rovingTabIndex - 1, max + 1));
    } else if (e.key === "ArrowDown" && direction === "column") {
      setRovingTabIndex(modulo(rovingTabIndex + 1, max + 1));
    } else if (e.key === "ArrowLeft" && direction === "row") {
      setRovingTabIndex(modulo(rovingTabIndex - 1, max + 1));
    } else if (e.key === "ArrowRight" && direction === "row") {
      setRovingTabIndex(modulo(rovingTabIndex + 1, max + 1));
    }
  }

  return { getTabIndex, getRef, eventHandlers: { onFocus, onBlur, onKeyDown } };
}
