import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Separator } from "@/modules/common/ui/separator";
import { Skeleton } from "@/modules/common/ui/skeleton";

type PageSkeletonProps = {
  rowCount: number;
  rowClassName: string;
  separator?: boolean;
  className?: string;
  children: (rows: ReactNode, separator: ReactNode) => ReactNode;
};

export function PageSkeleton({
  rowCount,
  rowClassName,
  separator = false,
  className = "p-4 sm:p-8",
  children,
}: PageSkeletonProps) {
  const { t } = useTranslation("common");
  const rows = Array.from({ length: rowCount }, (_, row) => <Skeleton key={row} className={rowClassName} />);

  return (
    <main className={className} aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true">{children(rows, separator ? <Separator className="mb-8" /> : null)}</div>
    </main>
  );
}
