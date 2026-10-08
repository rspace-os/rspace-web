import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { Separator } from "@/modules/common/ui/separator";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { BookingPreferencesContent } from "./BookingPreferencesContent";

export default function BookingPreferencesPage() {
  return (
    <Suspense fallback={<PreferencesSkeleton />}>
      <BookingPreferencesContent />
    </Suspense>
  );
}

function PreferencesSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className="space-y-6 p-4 sm:p-8" aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true" className="space-y-6">
        <Skeleton className="h-16 w-full max-w-2xl" />
        <Separator />
        <div className="max-w-2xl space-y-6">
          {[0, 1, 2].map((row) => (
            <Skeleton key={row} className="h-20 w-full" />
          ))}
          <Skeleton className="h-9 w-56" />
        </div>
        <Separator />
        <Skeleton className="h-32 w-full max-w-2xl" />
      </div>
    </main>
  );
}
