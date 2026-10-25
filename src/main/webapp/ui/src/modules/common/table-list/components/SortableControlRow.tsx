import type { UniqueIdentifier } from "@dnd-kit/core";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVerticalIcon } from "lucide-react";
import type { CSSProperties, ReactNode } from "react";
import { cn } from "@/modules/common/utils/cn";

export function SortableControlRow({
  id,
  moveLabel,
  className,
  children,
}: {
  id: UniqueIdentifier;
  moveLabel: string;
  className?: string;
  children: ReactNode;
}) {
  const { attributes, isDragging, listeners, setNodeRef, transform, transition } = useSortable({ id });
  const style: CSSProperties = {
    transform: CSS.Translate.toString(transform),
    transition,
    zIndex: isDragging ? 1 : undefined,
  };
  return (
    <li
      ref={setNodeRef}
      style={style}
      className={cn(
        "grid items-center gap-2 rounded-sm border bg-background p-2",
        className,
        isDragging && "opacity-60 shadow-sm",
      )}
    >
      <button
        type="button"
        aria-label={moveLabel}
        className="flex size-6 touch-none cursor-grab items-center justify-center rounded-sm text-muted-foreground hover:bg-muted active:cursor-grabbing"
        {...attributes}
        {...listeners}
      >
        <GripVerticalIcon aria-hidden="true" className="size-3" />
      </button>
      {children}
    </li>
  );
}
