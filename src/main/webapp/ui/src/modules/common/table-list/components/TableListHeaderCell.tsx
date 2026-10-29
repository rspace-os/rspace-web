import { FlexRender, type Header } from "@tanstack/react-table";
import { ArrowDownIcon, ArrowUpDownIcon, ArrowUpIcon, CircleHelpIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { fieldLabel } from "@/modules/common/collection/config";
import { TableHead } from "@/modules/common/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { selectionColumnId, type tableListFeatures } from "../utils/features";

function SortIcon({ direction }: { direction: false | "asc" | "desc" }) {
  if (direction === "asc") return <ArrowUpIcon aria-hidden="true" />;
  if (direction === "desc") return <ArrowDownIcon aria-hidden="true" />;
  return <ArrowUpDownIcon aria-hidden="true" className="opacity-45" />;
}

/** A sortable, resizable column header, or the select-all control for the selection column. */
export function TableListHeaderCell<TDocument extends Record<string, unknown>>({
  header,
  resizeId,
  resizeOffset,
  translate,
  selectAllControl,
  onResize,
}: {
  header: Header<typeof tableListFeatures, TDocument, unknown>;
  resizeId: string;
  resizeOffset: number;
  translate: (key: string) => string;
  selectAllControl: ReactNode;
  onResize: (columnId: string, size: number) => void;
}) {
  const { t } = useTranslation("common");
  const direction = header.column.getIsSorted();
  const meta = header.column.columnDef.meta;
  const label = meta ? fieldLabel(meta, translate) : header.column.id;
  const description = meta?.description ?? (meta?.descriptionKey ? translate(meta.descriptionKey) : null);
  const headerLabelId = `${resizeId}-${header.id}-label`;
  const resizeLabelId = `${resizeId}-${header.id}-resize`;
  return (
    <TableHead
      aria-labelledby={headerLabelId}
      aria-sort={
        header.column.getCanSort()
          ? direction === "asc"
            ? "ascending"
            : direction === "desc"
              ? "descending"
              : "none"
          : undefined
      }
      className="relative h-9 bg-muted/35 pr-4 text-xs"
    >
      <div className="flex min-w-0 items-center gap-1.5 overflow-hidden">
        {header.column.id === selectionColumnId && selectAllControl ? (
          <span id={headerLabelId}>{selectAllControl}</span>
        ) : header.column.getCanSort() ? (
          <button
            type="button"
            aria-label={t("tableList.actions.sortBy", { column: label })}
            className="flex min-w-0 items-center gap-1 rounded-sm hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring"
            onClick={header.column.getToggleSortingHandler()}
          >
            <span id={headerLabelId} className="min-w-0 overflow-hidden text-ellipsis">
              <FlexRender header={header} />
            </span>
            <SortIcon direction={direction} />
          </button>
        ) : (
          <span id={headerLabelId} className="min-w-0 overflow-hidden text-ellipsis">
            <FlexRender header={header} />
          </span>
        )}
        {description ? (
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  aria-label={t("tableList.tooltip", { column: label })}
                  className="rounded-sm text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring"
                />
              }
            >
              <CircleHelpIcon aria-hidden="true" className="size-3!" />
            </TooltipTrigger>
            <TooltipContent side="bottom" align="start" className="rounded-sm">
              {description}
            </TooltipContent>
          </Tooltip>
        ) : null}
      </div>
      {header.column.id !== selectionColumnId && header.column.getCanResize() ? (
        <button
          type="button"
          aria-labelledby={`${resizeLabelId} ${headerLabelId}`}
          className="group absolute inset-y-0 right-0 z-10 w-2 cursor-col-resize touch-none select-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          onMouseDown={header.getResizeHandler()}
          onTouchStart={header.getResizeHandler()}
          onDoubleClick={() => header.column.resetSize()}
          onKeyDown={(event) => {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault();
              header.column.resetSize();
              return;
            }
            if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
            event.preventDefault();
            const minimum = header.column.columnDef.minSize ?? 20;
            const maximum = header.column.columnDef.maxSize ?? Number.MAX_SAFE_INTEGER;
            const delta = event.key === "ArrowLeft" ? -16 : 16;
            const size = Math.min(maximum, Math.max(minimum, header.column.getSize() + delta));
            onResize(header.column.id, size);
          }}
        >
          <span id={resizeLabelId} className="sr-only">
            {t("tableList.actions.resizeColumn")}
          </span>
          <span
            aria-hidden="true"
            data-active={header.column.getIsResizing()}
            className="mx-auto block h-full w-px bg-border group-hover:bg-primary group-focus-visible:bg-primary data-[active=true]:bg-primary"
            style={{ transform: `translateX(${resizeOffset}px)` }}
          />
        </button>
      ) : null}
    </TableHead>
  );
}
