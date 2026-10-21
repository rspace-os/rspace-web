import { Skeleton } from "@/modules/common/ui/skeleton";
import { detailColumnsClassName, detailPageClassName } from "../DetailPageShell";
import { PageSkeleton } from "./PageSkeleton";

export function BookableItemSkeleton() {
  return (
    <PageSkeleton rowCount={4} rowClassName="h-12" className={detailPageClassName}>
      {(rows) => (
        <div className="@container space-y-6">
          <Skeleton className="h-11 w-full" />
          <Skeleton className="h-10 w-full" />
          <div className={detailColumnsClassName}>
            <div className="min-w-0">
              <Skeleton className="h-90 w-full" />
            </div>
            <div data-slot="bookable-item-facts" className="space-y-4">
              <Skeleton className="h-9 w-full" />
              {rows}
            </div>
          </div>
        </div>
      )}
    </PageSkeleton>
  );
}
