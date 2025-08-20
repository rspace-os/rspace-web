import React from "react";
import { useLocalObservable } from "mobx-react-lite";
import { runInAction, makeObservable, observable, computed } from "mobx";
import theme from "../../theme";

class ViewportDimensions {
  width: number;
  height: number;

  constructor({ width, height }: { width: number; height: number }) {
    makeObservable(this, {
      width: observable,
      height: observable,
      viewportSize: computed,
      isViewportVerySmall: computed,
      isViewportSmall: computed,
      isViewportLarge: computed,
    });
    this.width = width;
    this.height = height;
  }

  /**
   * Returns the label of the largest breakpoint that is smaller than the
   * current width.
   */
  get viewportSize(): "xl" | "lg" | "md" | "sm" | "xs" {
    if (this.width > theme.breakpoints.values.xl) return "xl";
    if (this.width > theme.breakpoints.values.lg) return "lg";
    if (this.width > theme.breakpoints.values.md) return "md";
    if (this.width > theme.breakpoints.values.sm) return "sm";
    return "xs";
  }

  /**
   * Pretty much just phones, but also tablets that support side-by-side apps,
   * and very narrow laptop/desktop windows.
   */
  get isViewportVerySmall(): boolean {
    return this.viewportSize === "xs";
  }

  /**
   * Viewports of this size are typically phones or tablets but also when
   * windows are pinned to just one side of laptop screens.
   */
  get isViewportSmall(): boolean {
    return this.viewportSize === "xs" || this.viewportSize === "sm";
  }

  /**
   * Full screen/maximised windows on laptops and most windows on desktops
   */
  get isViewportLarge(): boolean {
    return this.viewportSize === "lg" || this.viewportSize === "xl";
  }

  get isViewportNotLarge(): boolean {
    return (
      this.viewportSize === "xs" ||
      this.viewportSize === "sm" ||
      this.viewportSize === "md"
    );
  }
}

/**
 * The custom hook allows components to react to changes to the dimensions of
 * the browser's layout viewport. The layout viewport is the part of the
 * browser window that display's the document, before any adjustment is made
 * for zooming or virtual keyboards. Naturally, this can change during the
 * lifetime of the page if the size of the window changes, the device is
 * rotated, or a textual zoom is applied. A pinch zoom does not change the
 * layout viewport -- only the visual viewport -- and neither does opening a
 * virtual keyboard. For more information, see
 * https://developer.mozilla.org/en-US/docs/Web/CSS/Viewport_concepts
 *
 * To ensure that all our react pages are both mobile friendly whilst making
 * the most of the screen real estate available we are constantly adapting the
 * layout and behaviour of the page based on the size of the viewport. This
 * custom hook provides a simple way of accessing and reacting to changes to
 * the space available. Computed properties are exposed by the class defined
 * above based on the breakpoints defined in the theme (../theme.js), which can
 * be destructured and used as conditionals:
 *   const { isViewportSmall } = useViewportDimensions();
 *   if (isViewportSmall) return <span>is small</span>;
 *   return <span>is not small</span>;
 *
 * Note that this hook uses mobx to maintain the mutable state. As such, it is
 * necessary that any component that uses this hook be wrapped in mobx's
 * `observer` function. If a component isn't updating when the window width
 * changes its almost certainly because the relevant component isn't wrapped
 * correctly. For more information on that, see
 * https://mobx.js.org/react-integration.html
 */
export default function useViewportDimensions(): ViewportDimensions {
  const viewportDimensions = useLocalObservable(
    () =>
      new ViewportDimensions({
        width: window.innerWidth,
        height: window.innerHeight,
      }),
  );

  React.useEffect(() => {
    const handleResize = () => {
      runInAction(() => {
        viewportDimensions.width = window.innerWidth;
        viewportDimensions.height = window.innerHeight;
      });
    };

    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - viewportDimensions will not change as it is a local observable
     */
  }, []);

  return viewportDimensions;
}
