import React from "react";
import theme from "../../theme";

type ViewportSize = "xl" | "lg" | "md" | "sm" | "xs";

type ViewportDimensions = {
  /**
   * The label of the largest breakpoint that is smaller than the current
   * width.
   */
  readonly viewportSize: ViewportSize;

  /**
   * Pretty much just phones, but also tablets that support side-by-side apps,
   * and very narrow laptop/desktop windows.
   */
  readonly isViewportVerySmall: boolean;

  /**
   * Viewports of this size are typically phones or tablets but also when
   * windows are pinned to just one side of laptop screens.
   */
  readonly isViewportSmall: boolean;

  /**
   * Full screen/maximised windows on laptops and most windows on desktops
   */
  readonly isViewportLarge: boolean;
};

function viewportSizeOf(width: number): ViewportSize {
  if (width > theme.breakpoints.values.xl) return "xl";
  if (width > theme.breakpoints.values.lg) return "lg";
  if (width > theme.breakpoints.values.md) return "md";
  if (width > theme.breakpoints.values.sm) return "sm";
  return "xs";
}

function dimensionsFor(viewportSize: ViewportSize): ViewportDimensions {
  return Object.freeze({
    viewportSize,
    isViewportVerySmall: viewportSize === "xs",
    isViewportSmall: viewportSize === "xs" || viewportSize === "sm",
    isViewportLarge: viewportSize === "lg" || viewportSize === "xl",
  });
}

/*
 * One object per breakpoint, so that the hook returns the same object for as
 * long as the breakpoint does not change and callers can use it as an effect
 * dependency.
 */
const DIMENSIONS_BY_SIZE: Readonly<Record<ViewportSize, ViewportDimensions>> = {
  xl: dimensionsFor("xl"),
  lg: dimensionsFor("lg"),
  md: dimensionsFor("md"),
  sm: dimensionsFor("sm"),
  xs: dimensionsFor("xs"),
};

function subscribe(onChange: () => void): () => void {
  window.addEventListener("resize", onChange);
  return () => window.removeEventListener("resize", onChange);
}

function getSnapshot(): ViewportSize {
  return viewportSizeOf(window.innerWidth);
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
 * the space available. The returned properties are based on the breakpoints
 * defined in the theme (../theme.js), and can be destructured and used as
 * conditionals:
 *   const { isViewportSmall } = useViewportDimensions();
 *   if (isViewportSmall) return <span>is small</span>;
 *   return <span>is not small</span>;
 *
 * A component re-renders only when the window is resized across one of those
 * breakpoints, not on every resize event.
 */
export default function useViewportDimensions(): ViewportDimensions {
  return DIMENSIONS_BY_SIZE[React.useSyncExternalStore(subscribe, getSnapshot)];
}
