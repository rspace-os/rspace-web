import type { CSSProperties, ReactNode } from "react";
import { cn } from "@/modules/common/utils/cn";

export function RowField({ children }: { children: ReactNode }) {
  return (
    <div data-slot="field-row" className="@container flex flex-wrap items-start gap-4">
      {children}
    </div>
  );
}

export function RowFieldItem({ children, width }: { children: ReactNode; width?: CSSProperties["width"] }) {
  const cssWidth = typeof width === "number" ? `${width}px` : width;
  return (
    <div
      data-slot="field-row-item"
      className={cn(
        "min-w-0 w-full max-w-full [flex:0_0_100%] @sm:w-(--field-width) @sm:[flex:var(--field-grow)_0_var(--field-width)]",
        width === undefined && "min-w-[min(12rem,100%)]",
      )}
      style={
        {
          "--field-grow": width === undefined ? 1 : 0,
          "--field-width": cssWidth ?? "12rem",
        } as CSSProperties
      }
    >
      {children}
    </div>
  );
}
