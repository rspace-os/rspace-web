import { Suspense } from "react";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { BookableItemsContent } from "./BookableItemsContent";
import { PageSkeleton } from "./PageSkeleton";

export default function BookableItemsPage() {
  return (
    <Suspense fallback={<BookableItemsSkeleton />}>
      <BookableItemsContent />
    </Suspense>
  );
}

function BookableItemsSkeleton() {
  return (
    <PageSkeleton rowCount={4} rowClassName="h-16">
      {(rows) => (
        <div className="space-y-4">
          <Skeleton className="h-10 w-56" />
          <Skeleton className="h-24 w-full" />
          {rows}
        </div>
      )}
    </PageSkeleton>
  );
}
