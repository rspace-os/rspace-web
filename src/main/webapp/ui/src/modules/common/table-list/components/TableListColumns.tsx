import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  type DragOverEvent,
  DragOverlay,
  type DragStartEvent,
  type UniqueIdentifier,
} from "@dnd-kit/core";
import { arrayMove } from "@dnd-kit/sortable";
import { isEqual } from "es-toolkit";
import type { Namespace } from "i18next";
import { GripVerticalIcon } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { FieldName, ResolvedCollectionConfig, ResolvedFieldConfig } from "@/modules/common/collection/config";
import { useFieldLabels } from "@/modules/common/collection/useFieldLabels";
import { runtimeFieldSelector } from "@/modules/common/table-list/api-v2/runtimeFieldCatalog";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { useTableListDragAccessibility } from "../hooks/useTableListDragAccessibility";
import { useTableListDragSensors } from "../hooks/useTableListDragSensors";
import type { TableListRuntimeFields } from "../types";
import { ColumnChip } from "./ColumnChip";
import { ColumnDropZone, type ColumnPlacement, columnDropZoneIds } from "./ColumnDropZone";
import { ControlPanelSection } from "./ControlPanelSection";
import { CustomFieldPicker, customFieldOptionLabel, offeredRuntimeFieldGroups } from "./CustomFieldPicker";

type Layout<TDocument> = Record<ColumnPlacement, readonly FieldName<TDocument>[]>;

function sectionOf<TDocument>(layout: Layout<TDocument>, id: UniqueIdentifier): ColumnPlacement | null {
  if (layout.shown.includes(id as FieldName<TDocument>)) return "shown";
  if (layout.hidden.includes(id as FieldName<TDocument>)) return "hidden";
  return null;
}

function dropSection<TDocument>(layout: Layout<TDocument>, over: DragOverEvent["over"]): ColumnPlacement | null {
  const section = over?.data.current?.section;
  if (section === "shown" || section === "hidden") return section;
  if (!over) return null;
  return sectionOf(layout, over.id);
}

/** `layout` with `field` moved from `source` to `target`, before the field at `overId` there, else at its end. */
function moveAcrossSections<TDocument>(
  layout: Layout<TDocument>,
  field: FieldName<TDocument>,
  source: ColumnPlacement,
  target: ColumnPlacement,
  overId: UniqueIdentifier,
): Layout<TDocument> {
  const sourceFields = layout[source].filter((candidate) => candidate !== field);
  const targetFields = [...layout[target]];
  const targetIndex = targetFields.indexOf(overId as FieldName<TDocument>);
  targetFields.splice(targetIndex < 0 ? targetFields.length : targetIndex, 0, field);
  return { ...layout, [source]: sourceFields, [target]: targetFields };
}

/** `layout` with the field at `activeId` moved to the position of `overId` in `section`; unchanged if either is absent. */
function reorderWithinSection<TDocument>(
  layout: Layout<TDocument>,
  section: ColumnPlacement,
  activeId: UniqueIdentifier,
  overId: UniqueIdentifier,
): Layout<TDocument> {
  const sourceIndex = layout[section].indexOf(activeId as FieldName<TDocument>);
  const targetIndex = layout[section].indexOf(overId as FieldName<TDocument>);
  if (sourceIndex < 0 || targetIndex < 0) return layout;
  return { ...layout, [section]: arrayMove([...layout[section]], sourceIndex, targetIndex) };
}

function activeFieldLabel<TDocument>(
  fields: readonly ResolvedFieldConfig<TDocument>[],
  activeField: FieldName<TDocument>,
  resolveLabel: (field: ResolvedFieldConfig<TDocument>) => string,
): string {
  const field = fields.find((candidate) => candidate.name === activeField);
  return field ? resolveLabel(field) : activeField;
}

export function TableListColumns<TDocument>({
  controlPanelId,
  config,
  visibleFields,
  onChange,
  runtimeFields,
  onClose,
}: {
  /** Unique per table, so several tables on one page have distinct panel ids. */
  controlPanelId: string;
  config: ResolvedCollectionConfig<TDocument>;
  visibleFields: readonly FieldName<TDocument>[];
  onChange: (fields: readonly FieldName<TDocument>[]) => void;
  runtimeFields?: TableListRuntimeFields;
  onClose: () => void;
}) {
  const { t } = useTranslation<Namespace>("common");
  const { text, fieldLabel, fieldOptionLabel } = useFieldLabels();
  const availableFields = config.fields.filter((field) => field.list !== false);
  const runtimeSourceGroups = offeredRuntimeFieldGroups(config.runtimeSources, runtimeFields, "columnSelectable");
  const shown = visibleFields.filter((name) => availableFields.some((field) => field.name === name));
  const propsLayout: Layout<TDocument> = {
    shown,
    hidden: availableFields.map((field) => field.name).filter((name) => !shown.includes(name)),
  };
  const [drag, setDrag] = useState<{
    field: FieldName<TDocument>;
    /** The layout when the drag started, to tell a real change from a drop back in place. */
    origin: Layout<TDocument>;
    layout: Layout<TDocument>;
  } | null>(null);
  const draft = drag?.layout ?? propsLayout;
  const sensors = useTableListDragSensors();
  const dragAccessibility = useTableListDragAccessibility((id) => {
    if (id === columnDropZoneIds.shown) return t("tableList.columns.shown");
    if (id === columnDropZoneIds.hidden) return t("tableList.columns.hidden");
    const field = availableFields.find((candidate) => candidate.name === id);
    return field ? fieldOptionLabel(field) : String(id);
  });

  const transfer = ({ active, over }: DragOverEvent) => {
    if (!over) return;
    setDrag((current) => {
      if (!current) return current;
      const { layout } = current;
      const source = sectionOf(layout, active.id);
      const target = dropSection(layout, over);
      if (!source || !target || source === target) return current;
      return {
        ...current,
        layout: moveAcrossSections(layout, active.id as FieldName<TDocument>, source, target, over.id),
      };
    });
  };

  const finishDrag = (event: DragEndEvent) => {
    if (!drag) return;
    setDrag(null);
    const section = sectionOf(draft, event.active.id);
    const target = dropSection(draft, event.over);
    if (!event.over || !section || !target) return;
    const next =
      section === target && event.active.id !== event.over.id
        ? reorderWithinSection(draft, section, event.active.id, event.over.id)
        : draft;
    if (!isEqual(next.shown, drag.origin.shown)) onChange(next.shown);
  };

  const column = (fieldName: FieldName<TDocument>, section: ColumnPlacement) => {
    const field = availableFields.find((candidate) => candidate.name === fieldName);
    if (!field) return null;
    const label = fieldOptionLabel(field);
    return (
      <ColumnChip
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
    <ControlPanelSection
      controlPanelId={controlPanelId}
      title={t("tableList.columns.title")}
      closeLabel={t("tableList.actions.closeColumns")}
      onClose={onClose}
    >
      <DndContext
        accessibility={dragAccessibility}
        sensors={sensors}
        collisionDetection={closestCenter}
        autoScroll
        onDragStart={(event: DragStartEvent) =>
          setDrag({ field: event.active.id as FieldName<TDocument>, origin: propsLayout, layout: propsLayout })
        }
        onDragOver={transfer}
        onDragEnd={finishDrag}
        onDragCancel={() => setDrag(null)}
      >
        <div className="mt-4 space-y-4">
          <ColumnDropZone
            placement="shown"
            fields={draft.shown}
            title={t("tableList.columns.shown")}
            emptyLabel={t("tableList.columns.shownEmpty")}
          >
            {draft.shown.map((field) => column(field, "shown"))}
          </ColumnDropZone>
          <ColumnDropZone
            placement="hidden"
            fields={draft.hidden}
            title={t("tableList.columns.hidden")}
            emptyLabel={t("tableList.columns.hiddenEmpty")}
          >
            {draft.hidden.map((field) => column(field, "hidden"))}
          </ColumnDropZone>
        </div>
        <DragOverlay dropAnimation={null}>
          {drag ? (
            <Badge aria-hidden="true" variant="outline" className="h-6 gap-1.5 rounded-sm bg-background px-1.5 py-0">
              <GripVerticalIcon aria-hidden="true" className="size-3" />
              <span>{activeFieldLabel(availableFields, drag.field, fieldLabel)}</span>
            </Badge>
          ) : null}
        </DragOverlay>
      </DndContext>
      {runtimeSourceGroups.length > 0 && runtimeFields ? (
        <div className="mt-4 space-y-3 border-t pt-3">
          <h3 className="text-xs font-semibold tracking-wide uppercase">{t("tableList.fieldGroups.customFields")}</h3>
          {runtimeSourceGroups.map((group) => (
            <CustomFieldPicker
              key={group.key}
              sources={group.sources}
              ariaLabel={customFieldOptionLabel(group.via, t, text)}
              known={runtimeFields.definitions}
              isDefinitionAvailable={(definition) => definition.columnSelectable}
              onSelect={(namespace, definition) => {
                runtimeFields.onSelect(namespace, definition);
                const selector = runtimeFieldSelector(namespace, definition.id) as FieldName<TDocument>;
                if (!draft.shown.includes(selector)) onChange([...draft.shown, selector]);
              }}
            />
          ))}
        </div>
      ) : null}
      <div className="mt-4 flex justify-end border-t pt-3">
        <Button size="sm" variant="ghost" onClick={() => onChange(config.defaultColumns)}>
          {t("tableList.actions.resetColumns")}
        </Button>
      </div>
    </ControlPanelSection>
  );
}
