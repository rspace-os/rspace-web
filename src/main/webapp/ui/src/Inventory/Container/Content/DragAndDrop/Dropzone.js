//@flow

import React, { type Node, type ComponentType } from "react";
import { styled } from "@mui/material/styles";
import TickIcon from "@mui/icons-material/Done";
import CrossIcon from "@mui/icons-material/Clear";
import Box from "@mui/material/Box";
import { useDroppable } from "@dnd-kit/core";
import { type Location } from "../../../../stores/definitions/Container";
import { observer } from "mobx-react-lite";
import { useHelpers, mkDroppableId } from "./common";

const allowedColor = (
  allowed: boolean,
  theme: {
    palette: {
      success: { light: string, ... },
      error: { light: string, ... },
      ...
    },
  }
) => theme.palette[allowed ? "success" : "error"].light;

const WrapperDiv = styled(
  //eslint-disable-next-line react/display-name
  React.forwardRef(
    (
      {
        children,
        dragAndDropInProgress: _dragAndDropInProgress,
        isChoosing: _isChoosing,
        allowed: _allowed,
        ...props
      }: {|
        children: Node,
        dragAndDropInProgress: boolean,
        isChoosing: boolean,
        allowed: boolean,
      |},
      ref
    ) => (
      <div {...props} ref={ref}>
        {children}
      </div>
    )
  )
)(({ theme, dragAndDropInProgress, isChoosing, allowed }) => ({
  position: "relative",
  ...(() => {
    if (!dragAndDropInProgress) return {};
    return {
      border: `3px solid ${
        isChoosing ? allowedColor(allowed, theme) : "white"
      }`,
    };
  })(),
}));

const AllowedIcon = styled(({ allowed, className }) => (
  <Box className={className}>{allowed ? <TickIcon /> : <CrossIcon />}</Box>
))(({ theme, allowed }) => ({
  color: allowedColor(allowed, theme),
  position: "absolute",
  fontSize: "1.1rem",
  top: "calc(50% - 15px)",
  left: "calc(50% - 12px)",
  transform: "scale(1.5)",
}));

type DropzoneProps = {|
  children: Node,

  /**
   * The location of the visual or grid container whose content is to be set by
   * dropping an item in this dropzone. For
   */
  location: Location,
|};

export const Dropzone: ComponentType<DropzoneProps> = observer(
  ({ children, location }: DropzoneProps): Node => {
    const {
      thisLocationIsTheOrigin,
      dragAndDropInProgress,
      anItemIsBeingMoveOutOfLocation,
      isChoosing,
    } = useHelpers();

    const { setNodeRef } = useDroppable({
      id: mkDroppableId(location),
      /*
       * we don't disable the drop zones so that we can display red 'X's when
       * they are being hovered over. Instead the onDragEnd handler checks that
       * the destination locations are empty.
       */
      disabled: false,
    });

    const locationIsEmpty = !location.content;

    /*
     * Is the user allowed to choose this dropzone? We don't disable the
     * dropzones using DndKit's `disabled` functionality as that would prevent us
     * from being able to alter the rendering of a disallowed dropzone when the
     * user is hovering over. Instead we have our own mechanism for valdiating
     * the drop. This property is used to alter the dropzone when the user is
     * hovering over; displaying a red 'X' to indicate that dropping here is not
     * allowed and a green tick when it is.
     */
    const allowed = locationIsEmpty || anItemIsBeingMoveOutOfLocation(location);

    function renderContent() {
      if (!dragAndDropInProgress) return children;
      if (!anItemIsBeingMoveOutOfLocation(location))
        /*
         * Locations unrelated to the drag-and-drop are styled to reduce visual
         * noise and allow the user to focus on the information about the move
         * operation.
         */
        return (
          <div
            style={{
              filter: "grayscale(0.65) opacity(0.75)",
            }}
          >
            {children}
          </div>
        );
      if (thisLocationIsTheOrigin(location))
        /*
         * The content of the origin is rendered unchanged so that the
         * animation for dragging the content of the location is seamless.
         * The content ought to render a badge to signify the number of other
         * locations which are also being moved, if there is more than one.
         */
        return children;
      /*
       * The other items that are being moved are hidden. This is so that it is
       * obvious that those locations will now be empty and that other content
       * may be moved into these locations during the same drag-and-drop
       * operation i.e. consider a list of items all being moved one along.
       */
      return <div style={{ opacity: 0 }}>{children}</div>;
    }

    return (
      <WrapperDiv
        dragAndDropInProgress={dragAndDropInProgress}
        isChoosing={isChoosing(location)}
        allowed={allowed}
        ref={(node) => {
          setNodeRef(node);
        }}
      >
        {renderContent()}
        {dragAndDropInProgress && isChoosing(location) && (
          <AllowedIcon allowed={allowed} />
        )}
      </WrapperDiv>
    );
  }
);
