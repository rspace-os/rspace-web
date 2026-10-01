import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { Separator } from "@/modules/common/ui/separator";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { BookingSettingsContent } from "./BookingSettingsContent";

export default function BookingSettingsPage() {
  return (
    <Suspense fallback={<SettingsSkeleton />}>
      <BookingSettingsContent />
    </Suspense>
  );
}

function SettingsSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className="p-4 sm:p-8" aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true">
        <Skeleton className="mb-5 h-16 w-full max-w-2xl" />
        <Separator className="mb-8" />
        <div className="max-w-2xl space-y-8">
          {[0, 1, 2, 3, 4].map((row) => (
            <Skeleton key={row} className="h-20 w-full" />
          ))}
        </div>
      </div>
    </main>
  );
}
