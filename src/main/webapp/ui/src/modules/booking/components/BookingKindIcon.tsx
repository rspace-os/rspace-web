import { CalendarClockIcon, WrenchIcon } from "lucide-react";
import { cn } from "@/modules/common/utils/cn";

export function BookingKindIcon({ maintenance }: { maintenance: boolean }) {
  return (
    <span
      className={cn(
        "flex size-7 shrink-0 items-center justify-center rounded-sm",
        maintenance
          ? "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200"
          : "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-200",
      )}
    >
      {maintenance ? (
        <WrenchIcon className="size-4" aria-hidden="true" />
      ) : (
        <CalendarClockIcon className="size-4" aria-hidden="true" />
      )}
    </span>
  );
}
