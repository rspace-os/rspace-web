//@flow strict

import { useEffect } from "react";
import { useLocalObservable } from "mobx-react-lite";
import { runInAction, makeObservable, observable, computed } from "mobx";
import theme from "../theme";

class ViewportDimensions {
  width: number;
  height: number;

  constructor({ width, height }: {| width: number, height: number |}) {
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

  get viewportSize(): "xl" | "lg" | "md" | "sm" | "xs" {
    if (this.width > theme.breakpoints.values.xl) return "xl";
    if (this.width > theme.breakpoints.values.lg) return "lg";
    if (this.width > theme.breakpoints.values.md) return "md";
    if (this.width > theme.breakpoints.values.sm) return "sm";
    return "xs";
  }

  get isViewportVerySmall(): boolean {
    return this.viewportSize === "xs";
  }

  get isViewportSmall(): boolean {
    return this.viewportSize === "xs" || this.viewportSize === "sm";
  }

  get isViewportLarge(): boolean {
    return this.viewportSize === "lg" || this.viewportSize === "xl";
  }
}

export default function useViewportDimensions(): ViewportDimensions {
  const viewportDimensions = useLocalObservable(
    () =>
      new ViewportDimensions({
        width: window.innerWidth,
        height: window.innerHeight,
      })
  );

  useEffect(() => {
    const handleResize = () => {
      runInAction(() => {
        viewportDimensions.width = window.innerWidth;
        viewportDimensions.height = window.innerHeight;
      });
    };

    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  return viewportDimensions;
}
