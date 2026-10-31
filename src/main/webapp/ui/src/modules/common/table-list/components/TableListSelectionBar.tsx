import { useTranslation } from "react-i18next";
import { Button } from "@/modules/common/ui/button";
import type { TableListSelection } from "../types";

/** The floating bar of actions for the selected rows. */
export function TableListSelectionBar<TDocument>({ selection }: { selection: TableListSelection<TDocument> }) {
  const { t } = useTranslation("common");
  return (
    <section
      aria-label={t("tableList.selection.regionLabel")}
      className="fixed inset-x-4 bottom-4 z-50 mx-auto flex w-fit max-w-[calc(100vw-2rem)] flex-wrap items-center justify-center gap-2 rounded-sm border bg-popover p-2 text-popover-foreground shadow-lg"
    >
      <output aria-live="polite" aria-atomic="true" className="px-1 text-sm font-medium">
        {t("tableList.selection.count", {
          count: selection.value.size,
        })}
      </output>
      {selection.value.size >= selection.maximumCount ? (
        <span className="text-xs text-muted-foreground">
          {t("tableList.selection.limit", {
            count: selection.maximumCount,
          })}
        </span>
      ) : null}
      <div className="flex flex-wrap items-center justify-center gap-1">
        {selection.renderActions({
          selectedRowIds: selection.value,
          clearSelection: () => selection.onChange(new Set()),
        })}
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={selection.disabled}
          onClick={() => selection.onChange(new Set())}
        >
          {t("tableList.selection.clear")}
        </Button>
      </div>
    </section>
  );
}
