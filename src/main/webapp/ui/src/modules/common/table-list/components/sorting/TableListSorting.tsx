import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { restrictToVerticalAxis } from "@dnd-kit/modifiers";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVerticalIcon, Trash2Icon, XIcon } from "lucide-react";
import type { CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import type { ResolvedCollectionConfig, SortDirection, SortRule } from "@/modules/common/collection/collectionConfig";
import { Button } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";

function SortableSortingRow({
  id,
  label,
  moveLabel,
  direction,
  directionLabel,
  ascendingLabel,
  descendingLabel,
  removeLabel,
  onDirectionChange,
  onRemove,
}: {
  id: string;
  label: string;
  moveLabel: string;
  direction: SortDirection;
  directionLabel: string;
  ascendingLabel: string;
  descendingLabel: string;
  removeLabel: string;
  onDirectionChange: (direction: SortDirection) => void;
  onRemove: () => void;
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
        "grid grid-cols-[1.5rem_minmax(8rem,1fr)_minmax(8rem,12rem)_2rem] items-center gap-2 rounded-sm border bg-background p-2",
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
      <span className="text-xs font-medium">{label}</span>
      <select
        aria-label={directionLabel}
        className="h-8 min-w-0 rounded-sm border bg-background px-2 text-xs"
        value={direction}
        onChange={(event) => onDirectionChange(event.target.value as SortDirection)}
      >
        <option value="asc">{ascendingLabel}</option>
        <option value="desc">{descendingLabel}</option>
      </select>
      <Button aria-label={removeLabel} size="icon-xs" variant="ghost" onClick={onRemove}>
        <Trash2Icon aria-hidden="true" />
      </Button>
    </li>
  );
}

export function TableListSorting<TDocument>({
  config,
  sorting,
  onChange,
  onClose,
}: {
  config: ResolvedCollectionConfig<TDocument>;
  sorting: readonly SortRule<TDocument>[];
  onChange: (sorting: readonly SortRule<TDocument>[]) => void;
  onClose: () => void;
}) {
  const { i18n, t } = useTranslation("common");
  const translate = (key: string) => (i18n.exists(key) ? t(key as never) : key);
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 5 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const reorder = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    const sourceIndex = sorting.findIndex((rule) => rule.field === active.id);
    const targetIndex = sorting.findIndex((rule) => rule.field === over.id);
    if (sourceIndex >= 0 && targetIndex >= 0) onChange(arrayMove([...sorting], sourceIndex, targetIndex));
  };

  return (
    <section
      id="table-list-control-panel"
      aria-labelledby="table-list-sorting-title"
      className="rounded-sm border bg-popover p-4"
    >
      <div className="flex items-start justify-between gap-4">
        <h2 id="table-list-sorting-title" className="font-heading text-sm font-medium">
          {t("tableList.sorting.title")}
        </h2>
        <Button aria-label={t("tableList.actions.closeSorting")} size="icon-xs" variant="ghost" onClick={onClose}>
          <XIcon aria-hidden="true" />
        </Button>
      </div>
      {sorting.length === 0 ? (
        <p className="mt-4 text-xs text-muted-foreground">{t("tableList.sorting.empty")}</p>
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          modifiers={[restrictToVerticalAxis]}
          autoScroll
          onDragEnd={reorder}
        >
          <SortableContext items={sorting.map((rule) => rule.field)} strategy={verticalListSortingStrategy}>
            <ol
              aria-label={t("tableList.sorting.title")}
              className="mt-4 max-h-72 list-none space-y-2 overflow-y-auto p-0"
            >
              {sorting.map((rule, index) => {
                const field = config.fields.find((candidate) => candidate.name === rule.field);
                const label = field ? translate(field.labelKey) : rule.field;
                return (
                  <SortableSortingRow
                    key={rule.field}
                    id={rule.field}
                    label={label}
                    moveLabel={t("tableList.actions.moveSort", {
                      number: index + 1,
                    })}
                    direction={rule.direction}
                    directionLabel={t("tableList.sorting.direction", {
                      column: label,
                    })}
                    ascendingLabel={t("tableList.sorting.ascending")}
                    descendingLabel={t("tableList.sorting.descending")}
                    removeLabel={t("tableList.actions.removeSort", {
                      column: label,
                    })}
                    onDirectionChange={(direction) =>
                      onChange(
                        sorting.map((candidate) =>
                          candidate.field === rule.field ? { ...candidate, direction } : candidate,
                        ),
                      )
                    }
                    onRemove={() => onChange(sorting.filter((candidate) => candidate.field !== rule.field))}
                  />
                );
              })}
            </ol>
          </SortableContext>
        </DndContext>
      )}
    </section>
  );
}
