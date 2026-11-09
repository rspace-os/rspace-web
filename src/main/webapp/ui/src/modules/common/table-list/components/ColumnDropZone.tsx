import { useDroppable } from "@dnd-kit/core";
import { rectSortingStrategy, SortableContext } from "@dnd-kit/sortable";
import type { ReactNode } from "react";
import type { FieldName } from "@/modules/common/collection/config";
import { cn } from "@/modules/common/utils/cn";

export type ColumnPlacement = "shown" | "hidden";

export const columnDropZoneIds: Record<ColumnPlacement, string> = {
  shown: "table-list-columns-shown",
  hidden: "table-list-columns-hidden",
};

export function ColumnDropZone<TDocument>({
  placement,
  fields,
  title,
  emptyLabel,
  children,
}: {
  placement: ColumnPlacement;
  fields: readonly FieldName<TDocument>[];
  title: string;
  emptyLabel: string;
  children: ReactNode;
}) {
  const { isOver, setNodeRef } = useDroppable({ id: columnDropZoneIds[placement], data: { section: placement } });
  return (
    <div>
      <div className="mb-2 flex items-center gap-2">
        <h3 className="text-xs font-semibold tracking-wide uppercase">{title}</h3>
        <span className="text-xs text-muted-foreground">{fields.length}</span>
      </div>
      <fieldset
        ref={setNodeRef}
        aria-label={title}
        className={cn(
          "flex min-h-12 flex-wrap content-start items-start gap-2 rounded-sm border border-dashed bg-background p-3",
          isOver && "border-primary bg-primary/5",
        )}
      >
        <SortableContext items={[...fields]} strategy={rectSortingStrategy}>
          {fields.length > 0 ? (
            children
          ) : (
            <span className="self-center text-xs text-muted-foreground">{emptyLabel}</span>
          )}
        </SortableContext>
      </fieldset>
    </div>
  );
}
