import type { CSSProperties, ReactNode } from "react";
import { cn } from "@/modules/common/utils/cn";
import { RenderFields } from "../RenderFields";
import type { NestedFieldsProps, RowFieldConfig } from "../RenderFields.types";

// A row is its own horizontal grouping, so its fields stay stacked even inside
// an inline list; that is why it takes no layout.
export function RowField<TDocument extends Record<string, unknown>>({
  fieldConfig,
  ...nestedProps
}: NestedFieldsProps & { fieldConfig: RowFieldConfig<TDocument> }) {
  return (
    <div data-slot="field-row" className="@container flex flex-wrap items-start gap-4">
      {fieldConfig.fields
        .filter((field) => field.form !== false)
        .map((field) => (
          <RowFieldItem key={field.name} width={field.form ? field.form.width : undefined}>
            <RenderFields {...nestedProps} fields={[field]} />
          </RowFieldItem>
        ))}
    </div>
  );
}

function RowFieldItem({ children, width }: { children: ReactNode; width?: CSSProperties["width"] }) {
  const cssWidth = typeof width === "number" ? `${width}px` : width;
  return (
    <div
      data-slot="field-row-item"
      className={cn(
        "min-w-0 w-full max-w-full [flex:0_0_100%] @sm:w-(--field-width) @sm:[flex:var(--field-grow)_0_var(--field-width)]",
        width === undefined && "min-w-[min(12rem,100%)]",
        // A field hidden by its condition leaves an empty group; drop the slot so the row closes up.
        "has-[>[data-slot=field-group]:empty]:hidden",
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
