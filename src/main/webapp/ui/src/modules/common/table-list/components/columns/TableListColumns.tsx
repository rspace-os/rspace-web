import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  type DragOverEvent,
  DragOverlay,
  type DragStartEvent,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  type UniqueIdentifier,
  useDroppable,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  arrayMove,
  rectSortingStrategy,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVerticalIcon, XIcon } from "lucide-react";
import { type CSSProperties, type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { FieldName, ResolvedCollectionConfig } from "@/modules/common/collection/collectionConfig";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";

type Section = "shown" | "hidden";
type Layout<TDocument> = Record<Section, readonly FieldName<TDocument>[]>;

const sectionIds: Record<Section, string> = {
  shown: "table-list-columns-shown",
  hidden: "table-list-columns-hidden",
};

function sectionOf<TDocument>(layout: Layout<TDocument>, id: UniqueIdentifier): Section | null {
  if (layout.shown.includes(id as FieldName<TDocument>)) return "shown";
  if (layout.hidden.includes(id as FieldName<TDocument>)) return "hidden";
  return null;
}

function sameFields<TDocument>(left: readonly FieldName<TDocument>[], right: readonly FieldName<TDocument>[]) {
  return left.length === right.length && left.every((field, index) => field === right[index]);
}

function ColumnSection<TDocument>({
  name,
  fields,
  title,
  emptyLabel,
  children,
}: {
  name: Section;
  fields: readonly FieldName<TDocument>[];
  title: string;
  emptyLabel: string;
  children: ReactNode;
}) {
  const { isOver, setNodeRef } = useDroppable({ id: sectionIds[name], data: { section: name } });
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

function SortableColumn({
  fieldName,
  section,
  label,
  moveLabel,
  hideLabel,
  onHide,
}: {
  fieldName: string;
  section: Section;
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
    zIndex: isDragging ? 1 : undefined,
  };
  return (
    <Badge
      ref={setNodeRef}
      style={style}
      variant="outline"
      className={cn(
        "relative h-6 gap-1.5 rounded-sm bg-background px-1.5 py-0 select-none",
        isDragging && "opacity-60 shadow-sm",
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

export function TableListColumns<TDocument>({
  config,
  visibleFields,
  onChange,
  onClose,
}: {
  config: ResolvedCollectionConfig<TDocument>;
  visibleFields: readonly FieldName<TDocument>[];
  onChange: (fields: readonly FieldName<TDocument>[]) => void;
  onClose: () => void;
}) {
  const { i18n, t } = useTranslation("common");
  const translate = (key: string) => (i18n.exists(key) ? t(key as never) : key);
  const availableFields = useMemo(() => config.fields.filter((field) => field.list !== false), [config.fields]);
  const propsLayout = useMemo<Layout<TDocument>>(() => {
    const shown = visibleFields.filter((name) => availableFields.some((field) => field.name === name));
    return {
      shown,
      hidden: availableFields.map((field) => field.name).filter((name) => !shown.includes(name)),
    };
  }, [availableFields, visibleFields]);
  const [draft, setDraft] = useState(propsLayout);
  const [activeField, setActiveField] = useState<FieldName<TDocument> | null>(null);
  const origin = useRef(propsLayout);
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 5 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  useEffect(() => {
    if (activeField === null) setDraft(propsLayout);
  }, [activeField, propsLayout]);

  const targetSection = (event: DragOverEvent | DragEndEvent): Section | null => {
    const section = event.over?.data.current?.section;
    return section === "shown" || section === "hidden" ? section : event.over ? sectionOf(draft, event.over.id) : null;
  };

  const transfer = ({ active, over }: DragOverEvent) => {
    if (!over) return;
    setDraft((current) => {
      const source = sectionOf(current, active.id);
      const overSection = over.data.current?.section;
      const target: Section | null =
        overSection === "shown" || overSection === "hidden" ? overSection : sectionOf(current, over.id);
      if (!source || !target || source === target) return current;
      const field = active.id as FieldName<TDocument>;
      const sourceFields = current[source].filter((candidate) => candidate !== field);
      const targetFields = [...current[target]];
      const targetIndex = targetFields.indexOf(over.id as FieldName<TDocument>);
      targetFields.splice(targetIndex < 0 ? targetFields.length : targetIndex, 0, field);
      return { ...current, [source]: sourceFields, [target]: targetFields };
    });
  };

  const finishDrag = (event: DragEndEvent) => {
    const section = sectionOf(draft, event.active.id);
    const target = targetSection(event);
    if (!event.over || !section || !target) {
      setDraft(origin.current);
      setActiveField(null);
      return;
    }
    let next = draft;
    if (section === target && event.active.id !== event.over.id) {
      const sourceIndex = draft[section].indexOf(event.active.id as FieldName<TDocument>);
      const targetIndex = draft[section].indexOf(event.over.id as FieldName<TDocument>);
      if (sourceIndex >= 0 && targetIndex >= 0) {
        next = {
          ...draft,
          [section]: arrayMove([...draft[section]], sourceIndex, targetIndex),
        };
      }
    }
    setActiveField(null);
    if (sameFields(next.shown, origin.current.shown)) setDraft(origin.current);
    else {
      setDraft(next);
      onChange(next.shown);
    }
  };

  const column = (fieldName: FieldName<TDocument>, section: Section) => {
    const field = availableFields.find((candidate) => candidate.name === fieldName);
    if (!field) return null;
    const label = translate(field.labelKey);
    return (
      <SortableColumn
        key={fieldName}
        fieldName={fieldName}
        section={section}
        label={label}
        moveLabel={t("tableList.actions.moveColumn", { column: label })}
        hideLabel={t("tableList.actions.hideColumn", { column: label })}
        onHide={() => onChange(draft.shown.filter((name) => name !== fieldName))}
      />
    );
  };

  return (
    <section
      id="table-list-control-panel"
      aria-labelledby="table-list-columns-title"
      className="rounded-sm border bg-popover p-4"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id="table-list-columns-title" className="font-heading text-sm font-medium">
            {t("tableList.columns.title")}
          </h2>
        </div>
        <Button aria-label={t("tableList.actions.closeColumns")} size="icon-xs" variant="ghost" onClick={onClose}>
          <XIcon aria-hidden="true" />
        </Button>
      </div>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        autoScroll
        onDragStart={(event: DragStartEvent) => {
          origin.current = propsLayout;
          setDraft(propsLayout);
          setActiveField(event.active.id as FieldName<TDocument>);
        }}
        onDragOver={transfer}
        onDragEnd={finishDrag}
        onDragCancel={() => {
          setDraft(origin.current);
          setActiveField(null);
        }}
      >
        <div className="mt-4 space-y-4">
          <ColumnSection
            name="shown"
            fields={draft.shown}
            title={t("tableList.columns.shown")}
            emptyLabel={t("tableList.columns.shownEmpty")}
          >
            {draft.shown.map((field) => column(field, "shown"))}
          </ColumnSection>
          <ColumnSection
            name="hidden"
            fields={draft.hidden}
            title={t("tableList.columns.hidden")}
            emptyLabel={t("tableList.columns.hiddenEmpty")}
          >
            {draft.hidden.map((field) => column(field, "hidden"))}
          </ColumnSection>
        </div>
        <DragOverlay dropAnimation={null}>
          {activeField ? (
            <Badge aria-hidden="true" variant="outline" className="h-6 gap-1.5 rounded-sm bg-background px-1.5 py-0">
              <GripVerticalIcon aria-hidden="true" className="size-3" />
              <span>
                {translate(availableFields.find((field) => field.name === activeField)?.labelKey ?? activeField)}
              </span>
            </Badge>
          ) : null}
        </DragOverlay>
      </DndContext>
      <div className="mt-4 flex justify-end border-t pt-3">
        <Button size="sm" variant="ghost" onClick={() => onChange(config.defaultColumns)}>
          {t("tableList.actions.resetColumns")}
        </Button>
      </div>
    </section>
  );
}
