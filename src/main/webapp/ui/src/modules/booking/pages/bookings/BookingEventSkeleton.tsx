import { useTranslation } from "react-i18next";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { detailColumnsClassName, detailPageClassName } from "../DetailPageShell";

export function BookingEventSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className={detailPageClassName} aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true" className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-14 w-full" />
        <div className={detailColumnsClassName}>
          <div className="space-y-4">
            <Skeleton className="h-9 w-full" />
            {[0, 1, 2, 3].map((row) => (
              <Skeleton key={row} className="h-8 w-full" />
            ))}
          </div>
          <div className="space-y-4">
            <Skeleton className="h-9 w-full" />
            {[0, 1, 2].map((row) => (
              <Skeleton key={row} className="h-12 w-full" />
            ))}
          </div>
        </div>
      </div>
    </main>
  );
}
