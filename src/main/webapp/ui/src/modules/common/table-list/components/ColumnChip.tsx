import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVerticalIcon, XIcon } from "lucide-react";
import type { CSSProperties } from "react";
import { Badge } from "@/modules/common/ui/badge";
import { cn } from "@/modules/common/utils/cn";
import type { ColumnPlacement } from "./ColumnDropZone";

export function ColumnChip({
  fieldName,
  section,
  label,
  moveLabel,
  hideLabel,
  onHide,
}: {
  fieldName: string;
  section: ColumnPlacement;
  label: string;
  moveLabel: string;
  hideLabel: string;
  onHide: () => void;
}) {
  const { attributes, isDragging, listeners, setNodeRef, transform, transition } = useSortable({
    id: fieldName,
    data: { section },
  });
  const style: CSSProperties = {
    transform: CSS.Translate.toString(transform),
    transition,
  };
  return (
    <Badge
      ref={setNodeRef}
      style={style}
      variant="outline"
      className={cn(
        "relative h-6 gap-1.5 rounded-sm bg-background px-1.5 py-0 select-none",
        isDragging && "z-1 opacity-60 shadow-sm",
      )}
    >
      <button
        type="button"
        aria-label={moveLabel}
        className="-ml-0.5 flex size-4 touch-none cursor-grab items-center justify-center rounded-sm text-muted-foreground hover:bg-muted active:cursor-grabbing"
        {...attributes}
        {...listeners}
      >
        <GripVerticalIcon aria-hidden="true" className="size-3" />
      </button>
      <span>{label}</span>
      {section === "shown" ? (
        <button
          type="button"
          aria-label={hideLabel}
          className="-mr-0.5 flex size-4 items-center justify-center rounded-sm text-muted-foreground hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring"
          onClick={onHide}
        >
          <XIcon aria-hidden="true" className="size-2.5" />
        </button>
      ) : null}
    </Badge>
  );
}
