//@flow

import React, { type Node } from "react";
import {
  DndContext,
  useSensors,
  useSensor,
  MouseSensor,
  TouchSensor,
  KeyboardSensor,
} from "@dnd-kit/core";
import AlertContext, {
  mkAlert,
  type Alert,
} from "../../../../stores/contexts/Alert";
import {
  type Container,
  type Location,
} from "../../../../stores/definitions/Container";
import { type GlobalId } from "../../../../stores/definitions/BaseRecord";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import { runInAction } from "mobx";
import useStores from "../../../../stores/use-stores";
import { useContainerHelpers } from "./common";

export function Context({
  children,
  container,
  supportKeyboard,
  supportMultiple,
}: {|
  children: Node,
  container: Container,

  /**
   * Note that keyboard support relies on each Dragger and Dropzone being
   * rendering inside of HTMLTableCellElements
   */
  supportKeyboard?: boolean,

  supportMultiple?: boolean,
|}): Node {
  const { addAlert, removeAlert } = React.useContext(AlertContext);
  const { getDestinationLocationForSourceLocation } =
    useContainerHelpers(container);
  const { moveStore } = useStores();
  const mouseSensor = useSensor(MouseSensor, {
    activationConstraint: {
      delay: 500,
      tolerance: 5,
    },
  });
  const touchSensor = useSensor(TouchSensor, {
    activationConstraint: {
      delay: 500,
      tolerance: 5,
    },
  });
  const keyboardSensor = useSensor(KeyboardSensor, {
    /*
     * When in the keyboard-driven drag-and-drop mode, using the arrow keys
     * should move the content being dragged by the width/height of the current
     * HTMLTableCell that is being hovered over. This way, only a single tap is
     * required to move by a whole column/row.
     */
    coordinateGetter: (e, { currentCoordinates }) => {
      const { width, height } = e.target.closest("td").getBoundingClientRect();
      switch (e.code) {
        case "ArrowRight":
          return {
            ...currentCoordinates,
            x: currentCoordinates.x + width,
          };
        case "ArrowLeft":
          return {
            ...currentCoordinates,
            x: currentCoordinates.x - width,
          };
        case "ArrowDown":
          return {
            ...currentCoordinates,
            y: currentCoordinates.y + height,
          };
        case "ArrowUp":
          return {
            ...currentCoordinates,
            y: currentCoordinates.y - height,
          };
      }
    },
  });
  const sensors = useSensors(
    ...[mouseSensor, touchSensor, ...(supportKeyboard ? [keyboardSensor] : [])]
  );

  /*
   * We can't support dragging-and-dropping multiple items in visual containers
   * because unlike grid containers the locations are not evenly spaced apart.
   * As such, we show a warning alert for the duration of the time that
   * multiple items are being dragged to explain this. By keeping a reference
   * to the displayed alert, we can remove it when the use releases the mouse
   * button.
   */
  const [multipleAlert, setMultipleAlert] = React.useState<?Alert>(null);

  return (
    <DndContext
      sensors={sensors}
      onDragStart={(event) => {
        if (container.selectedLocations?.length === 0) {
          event.active.data.current.location.toggleSelected(true);
        }
        if (
          !supportMultiple &&
          (container.selectedLocations ?? []).length > 1
        ) {
          const alert = mkAlert({
            variant: "warning",
            message:
              "Dragging-and-dropping multiple items is not supported in visual containers.",
            isInfinite: true,
          });
          setMultipleAlert(alert);
          addAlert(alert);
        }
      }}
      onDragEnd={async (event) => {
        if (multipleAlert) removeAlert(multipleAlert);
        setMultipleAlert(null);

        // event.over is null if the dragging is release when not hovering
        if (!event.over) return;

        if (!supportMultiple && (container.selectedLocations ?? []).length > 1)
          return;

        const [, col, row] = event.over.id.match(/(\d+),(\d+)/);
        const destinationLocation = container.findLocation(
          parseInt(col, 10),
          parseInt(row, 10)
        );
        if (!destinationLocation)
          throw new Error("Cannot find destination location.");
        const sourceLocation = event.active.data.current.location;

        if (!container.selectedLocations)
          throw new Error("Container must have some selected locations");
        const selectedLocations = container.selectedLocations;
        const sourceLocations: {
          [GlobalId]: {| content: InventoryRecord, loc: Location |},
        } = Object.fromEntries(
          selectedLocations.map((l) => {
            if (!l.content)
              throw new Error("Selected location cannot be empty when moving.");
            if (!l.content.globalId)
              throw new Error(
                "Content of selected location must have a Global ID when moving."
              );
            return [l.content.globalId, { content: l.content, loc: l }];
          })
        );
        const destinationLocations: {
          [GlobalId]: {| content: ?InventoryRecord, loc: Location |},
        } = Object.fromEntries(
          selectedLocations.map((l) => {
            if (!l.content)
              throw new Error("Selected location cannot be empty when moving.");
            if (!l.content.globalId)
              throw new Error(
                "Content of selected location must have a Global ID when moving."
              );
            const globalId = l.content.globalId;
            const dest = getDestinationLocationForSourceLocation(event, l);
            return [globalId, { content: dest.content, loc: dest }];
          })
        );

        /*
         * If any of the destination locations contain content that is not
         * in the source locations then fail the move operation. Its fine
         * if one destination location contains content that is in another
         * source location because it will be moved itself by this move
         * operation.
         */
        if (
          Object.values(destinationLocations)
            .filter(
              (l) => !Object.keys(sourceLocations).includes(l.content?.globalId)
            )
            .some((l) => l.content)
        )
          return;

        try {
          if (destinationLocation !== sourceLocation) {
            const moveOperationParameters = Object.entries(sourceLocations).map(
              ([
                globalId,
                {
                  content: { id, type },
                },
              ]) => {
                return {
                  id,
                  type,
                  globalId,
                  parentContainers: [container.paramsForBackend],
                  parentLocation:
                    destinationLocations[globalId].loc.paramsForBackend,
                };
              }
            );
            /*
             * Immediately update the data model so that the UI doesn't bounce
             * the table cell content back, which would make it look like there
             * was an error.
             */
            runInAction(() => {
              Object.entries(destinationLocations).forEach(
                ([globalId, { loc }]) => {
                  loc.content = sourceLocations[globalId].content;
                }
              );
              const destCoords = Object.values(destinationLocations).map(
                ({ loc }) => ({ coordX: loc.coordX, coordY: loc.coordY })
              );
              const sourceLocationsThatAreNotDestinations = Object.values(
                sourceLocations
              ).filter(
                ({ loc }) =>
                  !destCoords.some(
                    ({ coordX, coordY }) =>
                      loc.coordX === coordX && loc.coordY === coordY
                  )
              );
              sourceLocationsThatAreNotDestinations.forEach(({ loc }) => {
                loc.content = null;
              });
            });
            await moveStore.moveRecords(moveOperationParameters);
            /*
             * After successfully moving, we clear all selections. This is
             * because empty locations will otherwise be left selected,
             * which is hardly ever helpful to the user. Plus, if any
             * contents were left selected then the content context menu
             * would still be visible even though the location in which they
             * were located would no longer selected. To avoid any of this
             * confusion and complexity, we just clear all selections.
             */
            container.locations?.forEach((loc) => {
              loc.content?.toggleSelected(false);
              loc.toggleSelected(false);
            });
          }
        } catch (e) {
          /**
           * If there actually is an error, then put back the changes to
           * the data model so that they remain in sync with the server.
           * It is possible that the user has made further changes whilst
           * the network call was in flight, but this seems unlikely given
           * the single network call is pretty fast. If this does become an
           * issue, we may wish to disable drag-and-drop operations whilst
           * the last one is still being processed.
           */
          runInAction(() => {
            Object.values(sourceLocations).forEach(({ loc, content }) => {
              loc.content = content;
            });
            const sourceCoords = Object.values(sourceLocations).map(
              ({ loc }) => ({ coordX: loc.coordX, coordY: loc.coordY })
            );
            const destinationLocationsThatAreNotSources = Object.values(
              destinationLocations
            ).filter(
              ({ loc }) =>
                !sourceCoords.some(
                  ({ coordX, coordY }) =>
                    loc.coordX === coordX && loc.coordY === coordY
                )
            );
            destinationLocationsThatAreNotSources.forEach(({ loc }) => {
              loc.content = null;
            });
          });
          console.error(e);
        }
      }}
    >
      {children}
    </DndContext>
  );
}
