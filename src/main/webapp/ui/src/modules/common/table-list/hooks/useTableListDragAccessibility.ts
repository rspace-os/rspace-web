import type { Announcements, ScreenReaderInstructions, UniqueIdentifier } from "@dnd-kit/core";
import { useTranslation } from "react-i18next";

/**
 * Screen-reader instructions and live announcements for a table-list drag list, in the UI language. dnd-kit's
 * defaults are English and name items by their internal ids; `describe` names a dragged item or drop target.
 */
export function useTableListDragAccessibility(describe: (id: UniqueIdentifier) => string): {
  announcements: Announcements;
  screenReaderInstructions: ScreenReaderInstructions;
} {
  const { t } = useTranslation("common");
  return {
    screenReaderInstructions: { draggable: t("tableList.dragAndDrop.instructions") },
    announcements: {
      onDragStart: ({ active }) => t("tableList.dragAndDrop.pickedUp", { item: describe(active.id) }),
      onDragOver: ({ active, over }) =>
        over
          ? t("tableList.dragAndDrop.movedOver", { item: describe(active.id), target: describe(over.id) })
          : t("tableList.dragAndDrop.notOver", { item: describe(active.id) }),
      onDragEnd: ({ active, over }) =>
        over
          ? t("tableList.dragAndDrop.droppedOver", { item: describe(active.id), target: describe(over.id) })
          : t("tableList.dragAndDrop.dropped", { item: describe(active.id) }),
      onDragCancel: ({ active }) => t("tableList.dragAndDrop.cancelled", { item: describe(active.id) }),
    },
  };
}
