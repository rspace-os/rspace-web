import { useTranslation } from "react-i18next";
import { Separator } from "@/modules/common/ui/separator";
import { Skeleton } from "@/modules/common/ui/skeleton";

export function AddBookableItemSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className="p-4 sm:p-8" aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true">
        <Skeleton className="mb-5 h-9 w-56" />
        <Separator className="mb-8" />
        <Skeleton className="h-16 w-full max-w-2xl" />
      </div>
    </main>
  );
}
