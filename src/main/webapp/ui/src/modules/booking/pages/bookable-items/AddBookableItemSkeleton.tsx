import { PageSkeleton } from "@/modules/common/app/PageSkeleton";
import { Skeleton } from "@/modules/common/ui/skeleton";

export function AddBookableItemSkeleton() {
  return (
    <PageSkeleton rowCount={1} rowClassName="h-16 w-full max-w-2xl" separator>
      {(rows, separator) => (
        <>
          <Skeleton className="mb-5 h-9 w-56" />
          {separator}
          {rows}
        </>
      )}
    </PageSkeleton>
  );
}
