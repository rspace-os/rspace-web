import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { AddBookingContent } from "./AddBookingContent";

export default function AddBookingPage() {
  return (
    <Suspense fallback={<AddBookingSkeleton />}>
      <AddBookingContent />
    </Suspense>
  );
}

function AddBookingSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className="space-y-6 p-4 sm:p-8" aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true" className="space-y-6">
        <Skeleton className="h-9 w-56" />
        <div className="@container">
          <div className="grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_24rem]">
            <div className="min-w-0 space-y-6">
              {[0, 1, 2].map((row) => (
                <Skeleton key={row} className="h-16 w-full" />
              ))}
              <Skeleton className="h-24 w-full" />
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}
