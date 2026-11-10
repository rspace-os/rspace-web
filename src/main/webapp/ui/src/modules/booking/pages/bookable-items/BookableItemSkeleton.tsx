import { useTranslation } from "react-i18next";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { itemColumnsClassName, itemPageClassName } from "./LoadedBookableItemPage";

export function BookableItemSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className={itemPageClassName} aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div className="@container space-y-6" aria-hidden="true">
        <Skeleton className="h-11 w-full" />
        <Skeleton className="h-10 w-full" />
        <div className={itemColumnsClassName}>
          <div className="min-w-0">
            <Skeleton className="h-90 w-full" />
          </div>
          <div data-slot="bookable-item-facts" className="space-y-4">
            <Skeleton className="h-9 w-full" />
            {[0, 1, 2, 3].map((row) => (
              <Skeleton key={row} className="h-12 w-full" />
            ))}
          </div>
        </div>
      </div>
    </main>
  );
}
