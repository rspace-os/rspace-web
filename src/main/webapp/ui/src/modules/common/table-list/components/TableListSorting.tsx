import { closestCenter, DndContext, type DragEndEvent, type UniqueIdentifier } from "@dnd-kit/core";
import { restrictToVerticalAxis } from "@dnd-kit/modifiers";
import { arrayMove, SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import type { Namespace } from "i18next";
import { PlusIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import type { ResolvedCollectionConfig, SortRule } from "@/modules/common/collection/config";
import { useFieldLabels } from "@/modules/common/collection/useFieldLabels";
import { Button } from "@/modules/common/ui/button";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "@/modules/common/ui/menu";
import { useTableListDragAccessibility } from "../hooks/useTableListDragAccessibility";
import { useTableListDragSensors } from "../hooks/useTableListDragSensors";
import { ControlPanelSection } from "./ControlPanelSection";
import { SortRuleRow } from "./SortRuleRow";

export function TableListSorting<TDocument>({
  controlPanelId,
  config,
  sorting,
  onChange,
  onClose,
}: {
  /** Unique per table, so several tables on one page have distinct panel ids. */
  controlPanelId: string;
  config: ResolvedCollectionConfig<TDocument>;
  sorting: readonly SortRule<TDocument>[];
  onChange: (sorting: readonly SortRule<TDocument>[]) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation<Namespace>("common");
  const { fieldLabel } = useFieldLabels();
  const availableFields = config.fields.filter(
    (field) => field.capabilities.sortable && !sorting.some((rule) => rule.field === field.name),
  );
  const sensors = useTableListDragSensors();
  const ruleLabel = (fieldName: UniqueIdentifier) => {
    const field = config.fields.find((candidate) => candidate.name === fieldName);
    return field ? fieldLabel(field) : String(fieldName);
  };
  const dragAccessibility = useTableListDragAccessibility(ruleLabel);
  const reorder = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    const sourceIndex = sorting.findIndex((rule) => rule.field === active.id);
    const targetIndex = sorting.findIndex((rule) => rule.field === over.id);
    if (sourceIndex >= 0 && targetIndex >= 0) onChange(arrayMove([...sorting], sourceIndex, targetIndex));
  };

  return (
    <ControlPanelSection
      controlPanelId={controlPanelId}
      title={t("tableList.sorting.title")}
      closeLabel={t("tableList.actions.closeSorting")}
      onClose={onClose}
    >
      {sorting.length === 0 ? (
        <p className="mt-4 text-xs text-muted-foreground">{t("tableList.sorting.empty")}</p>
      ) : (
        <DndContext
          accessibility={dragAccessibility}
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
                const label = ruleLabel(rule.field);
                return (
                  <SortRuleRow
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
      <Menu>
        <MenuTrigger
          render={<Button type="button" variant="ghost" size="sm" className="mt-3" />}
          disabled={availableFields.length === 0}
        >
          <PlusIcon aria-hidden="true" />
          {t("tableList.actions.addSort")}
        </MenuTrigger>
        <MenuContent align="start">
          {availableFields.map((field) => (
            <MenuItem key={field.name} onClick={() => onChange([...sorting, { field: field.name, direction: "asc" }])}>
              {fieldLabel(field)}
            </MenuItem>
          ))}
        </MenuContent>
      </Menu>
    </ControlPanelSection>
  );
}
