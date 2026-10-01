import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { BookableItemsContent } from "./BookableItemsContent";

export type { BookableItemsBulkAction } from "./BookableItemsContent";
export { mutateBookableItems } from "./BookableItemsContent";

export default function BookableItemsPage() {
  return (
    <Suspense fallback={<BookableItemsSkeleton />}>
      <BookableItemsContent />
    </Suspense>
  );
}

function BookableItemsSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className="p-4 sm:p-8" aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true" className="space-y-4">
        <Skeleton className="h-10 w-56" />
        <Skeleton className="h-24 w-full" />
        {[0, 1, 2, 3].map((row) => (
          <Skeleton key={row} className="h-16 w-full" />
        ))}
      </div>
    </main>
  );
}
