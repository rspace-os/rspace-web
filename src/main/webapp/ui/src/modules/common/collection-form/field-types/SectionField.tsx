import { useId } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader } from "@/modules/common/ui/card";
import { Separator } from "@/modules/common/ui/separator";
import { cn } from "@/modules/common/utils/cn";
import { RenderFields } from "../RenderFields";
import type { FieldLayout, NestedFieldsProps, SectionFieldConfig } from "../RenderFields.types";
import { RESPONSIVE_INLINE_FIELD_SPAN_CLASS_NAME } from "../responsiveFieldLayout";

export function SectionField<TDocument extends Record<string, unknown>>({
  fieldConfig: { fields, labelKey, variant },
  layout,
  ...nestedProps
}: NestedFieldsProps & { fieldConfig: SectionFieldConfig<TDocument>; layout: FieldLayout }) {
  const { t } = useTranslation("common");
  const headingId = `${useId()}-heading`;

  return (
    <Card
      size="sm"
      role="group"
      aria-labelledby={headingId}
      className={cn(
        "rounded-sm shadow-none",
        variant === "transparent" && "border-0 bg-transparent ring-0",
        layout === "inline" && RESPONSIVE_INLINE_FIELD_SPAN_CLASS_NAME,
      )}
    >
      <CardHeader className="gap-0">
        <h2 id={headingId} className="text-xs font-semibold tracking-wide uppercase">
          {t(labelKey as never)}
        </h2>
        <Separator className="mt-1 border-border border-t bg-transparent" />
      </CardHeader>
      <CardContent>
        <RenderFields {...nestedProps} fields={fields} layout={layout} />
      </CardContent>
    </Card>
  );
}
