import type { ReactNode } from "react";
import { cn } from "@/modules/common/utils/cn";
import { BookingKindIcon } from "./BookingKindIcon";

export function BookingSourceRow({
  heading,
  period,
  label,
  maintenance = false,
  truncate = true,
}: {
  heading: string;
  period: ReactNode;
  label?: string;
  maintenance?: boolean;
  truncate?: boolean;
}) {
  return (
    <li className="flex min-w-0 items-center gap-2 rounded-sm border bg-background px-2 py-1.5">
      <BookingKindIcon maintenance={maintenance} />
      <span className="min-w-0 flex-1">
        <span className={cn("block text-xs font-medium", truncate && "truncate")}>{heading}</span>
        <span className={cn("block text-[11px] text-muted-foreground", truncate && "truncate")}>
          {label ? (
            <>
              {label}
              {" · "}
            </>
          ) : null}
          {period}
        </span>
      </span>
    </li>
  );
}
