//@flow
import React, {
  useContext,
  useRef,
  useState,
  useLayoutEffect,
  type Node,
  type ComponentType,
} from "react";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import { type UseState } from "../../../../util/types";
import SearchContext from "../../../../stores/contexts/Search";
import Dragger from "../Dragger";
import RelativeBox from "../../../../components/RelativeBox";
import OverlayLoadingSpinner from "../../../components/OverlayLoadingSpinner";
import EmptyListing from "../../../Search/components/EmptyListing";
import ContainerModel from "../../../../stores/models/ContainerModel";
import LocationWrapper from "./LocationWrapper";
import LocationContent from "../LocationContent";
import { pick } from "../../../../util/unsafeUtils";
import * as DragAndDrop from "../DragAndDrop";

const useStyles = makeStyles()(() => ({
  rounded: {
    width: "auto",
    height: "auto",
    maxWidth: "100%",
    userSelect: "none",
    cursor: "crosshair",
  },
}));

function PreviewImage(): Node {
  const { scopedResult, search } = useContext(SearchContext);
  const { classes } = useStyles();

  const noSelection = search.uiConfig.selectionMode === "NONE";
  if (!(scopedResult && scopedResult instanceof ContainerModel))
    throw new Error("Search context's scopedResult must be a ContainerModel");
  const container: ContainerModel = scopedResult;
  if (!container.locations)
    throw new Error("Container locations must be known.");

  const [img, setImg] = useState(null);
  // Observe changes to the size of the image
  const [imageDimensions, setImageDimensions]: UseState<?{|
    width: number,
    height: number,
  |}> = useState(null);
  const resizeObserver = useRef(
    new ResizeObserver((entries) => {
      setImageDimensions(
        pick("width", "height")(entries[0].target.getBoundingClientRect())
      );
    })
  );
  const imgRef = useRef<?HTMLElement>();

  useLayoutEffect(() => {
    if (imgRef.current) {
      resizeObserver.current.observe(imgRef.current);
    }
    return () => {
      if (imgRef.current) resizeObserver.current.unobserve(imgRef.current);
    };
  }, [img, resizeObserver]);

  /*
   * When the user taps anywhere inside the image, initially we want to do
   * nothing but record where they tapped. If after 500ms they have neither
   * moved the cursor nor releases the click then drag-and-drop should be
   * begin. If they release the cursor without moving it then just that one
   * tapped location should be selected. If they move the cursor without
   * releasing the tap then drag selection should instead start.
   */
  const [mouseDownPoint, setMouseDownPoint] = React.useState<{|
    event: MouseEvent,
    clickTimeout: TimeoutID,
  |} | null>(null);

  return (
    <DragAndDrop.Context container={container}>
      <RelativeBox
        m={1}
        onMouseDown={(e: MouseEvent) => {
          if (noSelection) return;
          // $FlowExpectedError[incompatible-call]
          setMouseDownPoint({
            event: { ...e },
            clickTimeout: setTimeout(() => {
              // if after 300ms the click is still being held
              // then cancel it because drag-and-drop is starting
              setMouseDownPoint(null);
            }, 300),
          });
        }}
        onMouseUp={() => {
          if (noSelection) return;

          if (mouseDownPoint) {
            clearTimeout(mouseDownPoint.clickTimeout);
            container.startSelection(mouseDownPoint.event);
            setMouseDownPoint(null);
          }
          container.stopSelection({
            selectionLimit: search.uiConfig.selectionLimit,
            allowSelectingEmptyLocations:
              search.uiConfig.allowSelectingEmptyLocations,
          });
        }}
        onMouseMove={(e: MouseEvent) => {
          if (noSelection) return;
          if (mouseDownPoint) {
            clearTimeout(mouseDownPoint.clickTimeout);
            container.startSelection(mouseDownPoint.event);
            setMouseDownPoint(null);
          }
          container.moveSelection(e);
        }}
      >
        <img
          src={container.locationsImage}
          className={classes.rounded}
          onLoad={({ target }) => {
            setImg(target);
          }}
          onMouseDown={(e) => {
            e.preventDefault();
          }}
          ref={imgRef}
        />
        {container.initializedLocations && !container.loading && (
          <>
            {img && imageDimensions ? (
              container.locations?.map((location) => (
                <LocationWrapper
                  key={`${location.coordX}-${location.coordY}`}
                  location={location}
                  parentRect={imageDimensions}
                >
                  <LocationContent
                    location={location}
                    container={container}
                    /*
                     * PreviewImage is not keyboard accessible (although it ought
                     * to be to be compliant with the WCAG standard. As such,
                     * there is never a time when the locations have focus.
                     */
                    hasFocus={false}
                  />
                </LocationWrapper>
              ))
            ) : (
              <>
                {container.globalId && (
                  <EmptyListing parentGlobalId={container.globalId} />
                )}
              </>
            )}
            <Dragger container={container} parentRef={imgRef} />
          </>
        )}
        {container.locationsImage && container.loading && (
          <OverlayLoadingSpinner />
        )}
      </RelativeBox>
    </DragAndDrop.Context>
  );
}

export default (observer(PreviewImage): ComponentType<{||}>);
