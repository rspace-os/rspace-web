import type { ReactNode } from "react";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader } from "@/modules/common/ui/card";
import { Separator } from "@/modules/common/ui/separator";
import { cn } from "@/modules/common/utils/cn";
import type { SectionFieldConfig } from "../RenderFields.types";

export function SectionField<TDocument>({
  children,
  labelKey,
  variant,
}: Pick<SectionFieldConfig<TDocument>, "labelKey" | "variant"> & { children: ReactNode }) {
  const { t } = useTranslation("common");
  const headingId = `${useId()}-heading`;

  return (
    <Card
      size="sm"
      role="group"
      aria-labelledby={headingId}
      className={cn("rounded-sm shadow-none", variant === "transparent" && "border-0 bg-transparent ring-0")}
    >
      <CardHeader className="gap-0">
        <h2 id={headingId} className="text-xs font-semibold tracking-wide uppercase">
          {t(labelKey as never)}
        </h2>
        <Separator className="mt-1 border-border border-t bg-transparent" />
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}
