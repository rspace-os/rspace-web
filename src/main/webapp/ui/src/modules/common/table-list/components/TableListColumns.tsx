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
import { GripVerticalIcon, XIcon } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { FieldName, ResolvedCollectionConfig, ResolvedFieldConfig } from "@/modules/common/collection/config";
import { useFieldLabels } from "@/modules/common/collection/useFieldLabels";
import type {
  RuntimeFieldCatalogForNamespace,
  RuntimeFieldDefinition,
} from "@/modules/common/table-list/api-v2/runtimeFieldCatalog";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { useTableListDragAccessibility } from "../hooks/useTableListDragAccessibility";
import { useTableListDragSensors } from "../hooks/useTableListDragSensors";
import { ColumnChip } from "./ColumnChip";
import { ColumnDropZone, type ColumnPlacement, columnDropZoneIds } from "./ColumnDropZone";
import { CustomFieldPicker, groupRuntimeFieldSources } from "./CustomFieldPicker";

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
  onSelectRuntimeField,
  runtimeFieldDefinitions,
  onClose,
}: {
  /** Unique per table, so several tables on one page have distinct panel ids. */
  controlPanelId: string;
  config: ResolvedCollectionConfig<TDocument>;
  visibleFields: readonly FieldName<TDocument>[];
  onChange: (fields: readonly FieldName<TDocument>[]) => void;
  onSelectRuntimeField?: (namespace: string, definition: RuntimeFieldDefinition) => void;
  runtimeFieldDefinitions?: readonly RuntimeFieldCatalogForNamespace[];
  onClose: () => void;
}) {
  const { t } = useTranslation<Namespace>("common");
  const { text, fieldLabel, fieldOptionLabel } = useFieldLabels();
  const availableFields = config.fields.filter((field) => field.list !== false);
  const runtimeSources =
    onSelectRuntimeField === undefined ? [] : (config.runtimeSources ?? []).filter((source) => source.columnSelectable);
  const runtimeSourceGroups = groupRuntimeFieldSources(runtimeSources);
  const shown = visibleFields.filter((name) => availableFields.some((field) => field.name === name));
  const propsLayout: Layout<TDocument> = {
    shown,
    hidden: availableFields.map((field) => field.name).filter((name) => !shown.includes(name)),
  };
  const [dragLayout, setDraft] = useState(propsLayout);
  const [drag, setDrag] = useState<{
    activeField: FieldName<TDocument> | null;
    origin: Layout<TDocument>;
  }>(() => ({ activeField: null, origin: propsLayout }));
  const activeField = drag.activeField;
  const draft = activeField === null ? propsLayout : dragLayout;
  const endDrag = () => setDrag((current) => ({ ...current, activeField: null }));
  const sensors = useTableListDragSensors();
  const dragAccessibility = useTableListDragAccessibility((id) => {
    if (id === columnDropZoneIds.shown) return t("tableList.columns.shown");
    if (id === columnDropZoneIds.hidden) return t("tableList.columns.hidden");
    const field = availableFields.find((candidate) => candidate.name === id);
    return field ? fieldOptionLabel(field) : String(id);
  });

  const transfer = ({ active, over }: DragOverEvent) => {
    if (!over) return;
    setDraft((current) => {
      const source = sectionOf(current, active.id);
      const target = dropSection(current, over);
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
    const target = dropSection(draft, event.over);
    if (!event.over || !section || !target) {
      setDraft(drag.origin);
      endDrag();
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
    endDrag();
    if (isEqual(next.shown, drag.origin.shown)) setDraft(drag.origin);
    else {
      setDraft(next);
      onChange(next.shown);
    }
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
    <section
      id={controlPanelId}
      aria-labelledby={`${controlPanelId}-title`}
      className="rounded-sm border bg-popover p-4"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id={`${controlPanelId}-title`} className="font-heading text-sm font-medium">
            {t("tableList.columns.title")}
          </h2>
        </div>
        <Button aria-label={t("tableList.actions.closeColumns")} size="icon-xs" variant="ghost" onClick={onClose}>
          <XIcon aria-hidden="true" />
        </Button>
      </div>
      <DndContext
        accessibility={dragAccessibility}
        sensors={sensors}
        collisionDetection={closestCenter}
        autoScroll
        onDragStart={(event: DragStartEvent) => {
          setDraft(propsLayout);
          setDrag({
            activeField: event.active.id as FieldName<TDocument>,
            origin: propsLayout,
          });
        }}
        onDragOver={transfer}
        onDragEnd={finishDrag}
        onDragCancel={() => {
          setDraft(drag.origin);
          endDrag();
        }}
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
          {activeField ? (
            <Badge aria-hidden="true" variant="outline" className="h-6 gap-1.5 rounded-sm bg-background px-1.5 py-0">
              <GripVerticalIcon aria-hidden="true" className="size-3" />
              <span>{activeFieldLabel(availableFields, activeField, fieldLabel)}</span>
            </Badge>
          ) : null}
        </DragOverlay>
      </DndContext>
      {runtimeSourceGroups.length > 0 && onSelectRuntimeField ? (
        <div className="mt-4 space-y-3 border-t pt-3">
          <h3 className="text-xs font-semibold tracking-wide uppercase">{t("tableList.fieldGroups.customFields")}</h3>
          {runtimeSourceGroups.map((group) => (
            <CustomFieldPicker
              key={group.key}
              sources={group.sources}
              ariaLabel={
                group.via === undefined
                  ? t("tableList.filters.customField.option")
                  : t("tableList.filters.customField.optionVia", { via: text(group.via.label) })
              }
              known={runtimeFieldDefinitions ?? []}
              isDefinitionAvailable={(definition) => definition.columnSelectable}
              onSelect={(namespace, definition) => {
                onSelectRuntimeField(namespace, definition);
                const selector = `${namespace}.${definition.id}` as FieldName<TDocument>;
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
    </section>
  );
}
