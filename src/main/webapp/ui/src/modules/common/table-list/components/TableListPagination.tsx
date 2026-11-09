import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { formatNumber } from "@/modules/common/i18n/localeFormat";
import { Button } from "@/modules/common/ui/button";
import type { PageState } from "../types";

const defaultLimits = [10, 20, 30, 40, 50] as const;

export function TableListPagination({
  value,
  rowCount,
  hasNextPage,
  pageRowCount,
  limits = defaultLimits,
  onChange,
}: {
  value: PageState;
  rowCount?: number;
  hasNextPage?: boolean;
  pageRowCount: number;
  limits?: readonly number[];
  onChange: (value: PageState) => void;
}) {
  const { t, i18n } = useTranslation("common");
  const pageCount = rowCount === undefined ? undefined : Math.max(Math.ceil(rowCount / value.pageSize), 1);
  const first = pageRowCount === 0 ? 0 : value.pageIndex * value.pageSize + 1;
  const last = pageRowCount === 0 ? 0 : value.pageIndex * value.pageSize + pageRowCount;
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t px-3 py-3 text-xs text-muted-foreground">
      <span>
        {rowCount === undefined
          ? t("tableList.resultsUnknown", { first, last })
          : t("tableList.results", { first, last, total: rowCount })}
      </span>
      <div className="flex items-center gap-2">
        <label className="flex items-center gap-2">
          {t("tableList.rows")}
          <select
            aria-label={t("tableList.rowsPerPage")}
            className="h-8 rounded-sm border bg-background px-2 text-foreground focus-visible:ring-2 focus-visible:ring-ring"
            value={value.pageSize}
            onChange={(event) => onChange({ pageIndex: 0, pageSize: Number(event.target.value) })}
          >
            {limits.map((limit) => (
              <option key={limit} value={limit}>
                {formatNumber(limit, i18n.resolvedLanguage ?? i18n.language)}
              </option>
            ))}
          </select>
        </label>
        <span className="min-w-20 text-center text-foreground">
          {pageCount === undefined
            ? t("tableList.pageUnknown", { page: value.pageIndex + 1 })
            : t("tableList.page", { page: value.pageIndex + 1, pages: pageCount })}
        </span>
        <Button
          aria-label={t("tableList.actions.previousPage")}
          size="icon-sm"
          variant="outline"
          disabled={value.pageIndex === 0}
          onClick={() => onChange({ ...value, pageIndex: value.pageIndex - 1 })}
        >
          <ChevronLeftIcon aria-hidden="true" />
        </Button>
        <Button
          aria-label={t("tableList.actions.nextPage")}
          size="icon-sm"
          variant="outline"
          disabled={!(hasNextPage ?? (pageCount !== undefined && value.pageIndex + 1 < pageCount))}
          onClick={() => onChange({ ...value, pageIndex: value.pageIndex + 1 })}
        >
          <ChevronRightIcon aria-hidden="true" />
        </Button>
      </div>
    </div>
  );
}
