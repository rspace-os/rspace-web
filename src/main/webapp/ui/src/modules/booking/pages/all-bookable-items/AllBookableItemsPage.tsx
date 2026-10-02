import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { AllBookableItemsContent, type AllBookableItemsContentProps } from "./AllBookableItemsContent";

function AllItemsSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className="space-y-5 p-4 sm:p-8" aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true" className="space-y-5">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-24 w-full" />
        {[0, 1, 2, 3].map((row) => (
          <Skeleton key={row} className="h-20 w-full" />
        ))}
      </div>
    </main>
  );
}

export default function AllBookableItemsPage(props: AllBookableItemsContentProps = {}) {
  return (
    <Suspense fallback={<AllItemsSkeleton />}>
      <AllBookableItemsContent {...props} />
    </Suspense>
  );
}
