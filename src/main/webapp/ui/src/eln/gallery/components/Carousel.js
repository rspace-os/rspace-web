//@flow

import React, { type Node } from "react";
import { type GalleryFile, idToString } from "../useGalleryListing";
import { Optional } from "../../../util/optional";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import { useGallerySelection } from "../useGallerySelection";
import { useImagePreview } from "./CallableImagePreview";
import { usePdfPreview } from "./CallablePdfPreview";
import { useAsposePreview } from "./CallableAsposePreview";
import usePrimaryAction, {
  useImagePreviewOfGalleryFile,
  usePdfPreviewOfGalleryFile,
  useAsposePreviewOfGalleryFile,
} from "../primaryActionHooks";
import PlaceholderLabel from "./PlaceholderLabel";
import IconButton from "@mui/material/IconButton";
import ZoomInIcon from "@mui/icons-material/ZoomIn";
import ZoomOutIcon from "@mui/icons-material/ZoomOut";
import ButtonGroup from "@mui/material/ButtonGroup";
import Divider from "@mui/material/Divider";
import { take, incrementForever } from "../../../util/iterators";
import { Document, Page, pdfjs } from "react-pdf";
import "react-pdf/dist/esm/Page/TextLayer.css";
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import Result from "../../../util/result";
import * as ArrayUtils from "../../../util/ArrayUtils";
import * as Parsers from "../../../util/parsers";
import axios from "axios";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { styled } from "@mui/material/styles";
import { COLOR } from "../common";

/*
 * When a drag is in progress, this cursor style applied.
 * Note that is relies on an undocumented implementation detail of the Pdf
 * renderer so may not be entirely stable. If this class no longer applied then
 * a default pointer will be shown when the drag operation is in progress.
 */
const StyledDocument = styled(Document)(() => ({
  "& .textLayer .endOfContent": {
    cursor: "grabbing",
  },
}));

/*
 * This snippet is a necessary step in initialising the PDF preview
 * functionality. Taken from the example code for react-pdf
 * https://github.com/wojtekmaj/react-pdf/blob/main/sample/webpack5/Sample.tsx
 */
pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  "pdfjs-dist/build/pdf.worker.min.mjs",
  import.meta.url
).toString();

/*
 * Arbitrary number that determines how much the zoom in and out buttons zoom
 * in and out with each tap. Too small of a number and the user has to tap over
 * and over to reach the level of desired zoom, to much and the user doesn't
 * have sufficiently fine-grained control.
 */
const ZOOM_SCALE_FACTOR = 1.4;

const PreviewWrapper = ({
  file,
  previewingAsPdf,
  children,
  visible,
}: {|
  file: GalleryFile,
  previewingAsPdf: boolean,
  children: Node,
  visible: boolean,
|}) => {
  const { openImagePreview } = useImagePreview();
  const { openPdfPreview } = usePdfPreview();
  const { openAsposePreview } = useAsposePreview();
  const primaryAction = usePrimaryAction();
  const [scrollPos, setScrollPos] = React.useState<null | {|
    scrollLeft: number,
    scrollTop: number,
  |}>(null);
  const [cursorOffset, setCursorOffset] = React.useState<null | {|
    x: number,
    y: number,
  |}>(null);

  function display() {
    if (!visible) return "none";
    if (previewingAsPdf) return "block";
    return "flex";
  }

  return (
    <div
      role="button"
      tabIndex={0}
      style={{
        borderRadius: "3px",
        border: `2px solid hsl(${COLOR.background.hue}deg, ${COLOR.background.saturation}%, ${COLOR.background.lightness}%)`,
        position: "relative",
        height: "100%",
        overflow: "auto",
        justifyContent: "center",
        alignItems: "center",
        display: display(),
        // we override the Pdf rendering internals to change to "grabbing" when
        // a drag is in operation
        cursor: "grab",
      }}
      key={idToString(file.id)}
      onMouseDown={(e) => {
        const thisNode = e.target.closest("[role='button']");
        setScrollPos({
          scrollLeft: thisNode.scrollLeft,
          scrollTop: thisNode.scrollTop,
        });
        setCursorOffset({ x: e.nativeEvent.clientX, y: e.nativeEvent.clientY });
      }}
      onMouseMove={(e) => {
        if (!scrollPos || !cursorOffset) return;
        const thisNode = e.target.closest("[role='button']");
        const currentOffset = {
          x: e.nativeEvent.clientX,
          y: e.nativeEvent.clientY,
        };
        const moved = {
          x: currentOffset.x - cursorOffset.x,
          y: currentOffset.y - cursorOffset.y,
        };
        thisNode.scrollTo(
          scrollPos.scrollLeft - moved.x,
          scrollPos.scrollTop - moved.y
        );
      }}
      onMouseUp={() => {
        setCursorOffset(null);
        setScrollPos(null);
      }}
      onKeyDown={(e) => {
        if (e.key === "Enter") {
          primaryAction(file).do((action) => {
            if (action.tag === "open") {
              action.open();
              return;
            }
            if (action.tag === "image") {
              openImagePreview(action.downloadHref);
              return;
            }
            if (action.tag === "collabora") {
              window.open(action.url);
              return;
            }
            if (action.tag === "officeonline") {
              window.open(action.url);
              return;
            }
            if (action.tag === "pdf") {
              openPdfPreview(action.downloadHref);
              return;
            }
            if (action.tag === "aspose") {
              void openAsposePreview(file);
            }
          });
        }
      }}
      onClick={(e) => {
        if (e.detail > 1) {
          primaryAction(file).do((action) => {
            if (action.tag === "open") {
              action.open();
              return;
            }
            if (action.tag === "image") {
              openImagePreview(action.downloadHref);
              return;
            }
            if (action.tag === "collabora") {
              window.open(action.url);
              return;
            }
            if (action.tag === "officeonline") {
              window.open(action.url);
              return;
            }
            if (action.tag === "pdf") {
              openPdfPreview(action.downloadHref);
              return;
            }
            if (action.tag === "aspose") {
              void openAsposePreview(file);
            }
          });
        }
      }}
    >
      {children}
    </div>
  );
};

const Preview = ({
  file,
  zoom,
  visible,
}: {|
  file: GalleryFile,
  zoom: number,
  visible: boolean,
|}) => {
  const canPreviewAsImage = useImagePreviewOfGalleryFile();
  const canPreviewAsPdf = usePdfPreviewOfGalleryFile();
  const canPreviewWithAspose = useAsposePreviewOfGalleryFile();
  const [numPages, setNumPages] = React.useState<number>(0);
  const [asposePdfUrl, setAsposePdfUrl] = React.useState<
    | {| tag: "loading" |}
    | {| tag: "loaded", url: string |}
    | {| tag: "error" |}
    | null
  >(null);

  const { key, url, message } = canPreviewAsImage(file)
    .map((downloadHref) => ({
      key: "image",
      url: downloadHref,
      message: "",
    }))
    .orElseTry(() =>
      canPreviewAsPdf(file).map((downloadHref) => ({
        key: "pdf",
        url: downloadHref,
        message: "",
      }))
    )
    .orElseTry(() => {
      if (asposePdfUrl === null)
        return Result.Error<{| key: string, url: string, message: string |}>([
          new Error("Can't preview with aspose"),
        ]);
      if (asposePdfUrl.tag === "loaded")
        return Result.Ok({ key: "pdf", url: asposePdfUrl.url, message: "" });
      if (asposePdfUrl.tag === "error")
        return Result.Ok({
          key: "image",
          url: file.thumbnailUrl,
          message: "",
        });
      return Result.Ok({
        key: "aspose_message",
        url: "",
        message: "Generating preview",
      });
    })
    .orElseGet(() => ({
      key: "image",
      url: file.thumbnailUrl,
      message: "",
    }));

  React.useEffect(() => {
    canPreviewWithAspose(file).do(() => {
      void (async () => {
        setAsposePdfUrl({ tag: "loading" });
        try {
          const { data } = await axios.get<mixed>(
            "/Streamfile/ajax/convert/" +
              idToString(file.id) +
              "?outputFormat=pdf"
          );
          const fileName = Parsers.isObject(data)
            .flatMap(Parsers.isNotNull)
            .flatMap(Parsers.getValueWithKey("data"))
            .flatMap(Parsers.isString)
            .orElse(null);
          if (fileName) {
            setAsposePdfUrl({
              tag: "loaded",
              url:
                "/Streamfile/direct/" +
                idToString(file.id) +
                "?fileName=" +
                fileName,
            });
          } else {
            Parsers.isObject(data)
              .flatMap(Parsers.isNotNull)
              .flatMap(Parsers.getValueWithKey("exceptionMessage"))
              .flatMap(Parsers.isString)
              .do((msg) => {
                throw new Error(msg);
              });
            Parsers.objectPath(["error", "errorMessages"], data)
              .flatMap(Parsers.isArray)
              .flatMap(ArrayUtils.head)
              .flatMap(Parsers.isString)
              .do((msg) => {
                throw new Error(msg);
              });
          }
        } catch (error) {
          setAsposePdfUrl({
            tag: "error",
          });
        }
      })();
    });
  }, []);

  function onDocumentLoadSuccess({
    numPages: nextNumPages,
  }: {
    numPages: number,
    ...
  }): void {
    setNumPages(nextNumPages);
  }

  if (key === "image")
    return (
      <PreviewWrapper file={file} previewingAsPdf={false} visible={visible}>
        <img
          src={url}
          style={{
            maxHeight: "100%",
            maxWidth: "100%",
            transform: `scale(${zoom})`,
            transition: "transform .5s ease-in-out",
            transformOrigin: "left top",
          }}
          key={url}
        />
      </PreviewWrapper>
    );
  if (key === "pdf")
    return (
      <PreviewWrapper file={file} previewingAsPdf={true} visible={visible}>
        <StyledDocument file={url} onLoadSuccess={onDocumentLoadSuccess}>
          {[...take(incrementForever(), numPages)].map((index) => (
            <Page
              key={`page_${index + 1}`}
              pageNumber={index + 1}
              scale={zoom}
            />
          ))}
        </StyledDocument>
      </PreviewWrapper>
    );
  if (key === "aspose_message") {
    return (
      <PreviewWrapper file={file} previewingAsPdf={true} visible={visible}>
        {message}
      </PreviewWrapper>
    );
  }
  throw new Error("Don't know how to render that preview");
};

type CarouselArgs = {
  listing:
    | {| tag: "empty", reason: string |}
    | {|
        tag: "list",
        list: $ReadOnlyArray<GalleryFile>,
        loadMore: Optional<() => Promise<void>>,
      |},
};

export default function Carousel({ listing }: CarouselArgs): Node {
  const [visibleIndex, setVisibleIndex] = React.useState(0);
  const selection = useGallerySelection();
  const [zoom, setZoom] = React.useState(1);

  React.useEffect(() => {
    if (listing.tag !== "list") return;
    // when enter Carousel view, focus on already selected file
    selection.asSet().only.do((selectedFile) => {
      setVisibleIndex(
        Math.max(
          0,
          listing.list.findIndex(({ id }) => id === selectedFile.id)
        )
      );
    });
    // otherwise select the first file
    if (selection.isEmpty) {
      setVisibleIndex(0);
      selection.append(listing.list[0]);
    }
  }, [listing]);

  function incrementVisibleIndex() {
    if (listing.tag !== "list") return;
    if (visibleIndex + 1 < listing.list.length) {
      const newIndex = visibleIndex + 1;
      setVisibleIndex(newIndex);
      setZoom(1);
      selection.clear();
      selection.append(listing.list[newIndex]);
    }
    if (visibleIndex + 2 === listing.list.length) {
      // before the user gets to the end, pre-emptively load the next page
      listing.loadMore.do((loadMore) => void loadMore());
    }
  }

  function decrementVisibleIndex() {
    if (listing.tag !== "list") return;
    if (visibleIndex - 1 >= 0) {
      const newIndex = visibleIndex - 1;
      setVisibleIndex(newIndex);
      setZoom(1);
      selection.clear();
      selection.append(listing.list[newIndex]);
    }
  }

  React.useEffect(() => {
    const f = (e: KeyboardEvent) => {
      if (e.key === "ArrowRight") {
        incrementVisibleIndex();
      }
      if (e.key === "ArrowLeft") {
        decrementVisibleIndex();
      }
    };
    window.addEventListener("keydown", f);
    return () => window.removeEventListener("keydown", f);
  }, [visibleIndex, listing]);

  if (listing.tag === "empty")
    return <PlaceholderLabel>{listing.reason}</PlaceholderLabel>;
  return (
    <Grid
      container
      direction="column"
      sx={{
        /*
         * it looks better if the preview panel's bottom border lines up with
         * the bottom of the InfoPanel divider
         */
        height: "calc(100% + 8px)",
      }}
      spacing={1}
      flexWrap="nowrap"
    >
      <Grid item container direction="row" spacing={1}>
        <Grid item>
          <Button
            onClick={() => {
              decrementVisibleIndex();
            }}
            disabled={visibleIndex === 0}
            startIcon={<ArrowBackIcon />}
          >
            Previous
          </Button>
        </Grid>
        <Grid item flexGrow={1}></Grid>
        <Grid item>
          <ButtonGroup
            variant="outlined"
            sx={{
              border: `2px solid hsl(${COLOR.background.hue}deg, ${COLOR.background.saturation}%, ${COLOR.background.lightness}%)`,
              borderRadius: "8px",
            }}
          >
            <IconButton
              onClick={() => {
                setZoom((z) => z * ZOOM_SCALE_FACTOR);
              }}
              aria-label="zoom in"
              size="small"
            >
              <ZoomInIcon />
            </IconButton>
            <Divider
              orientation="vertical"
              sx={{
                height: "26px",
                marginTop: "4px",
                borderRightWidth: "1px",
              }}
            />
            <IconButton
              onClick={() => {
                setZoom((z) => z / ZOOM_SCALE_FACTOR);
              }}
              disabled={zoom === 1}
              aria-label="zoom out"
              size="small"
            >
              <ZoomOutIcon />
            </IconButton>
          </ButtonGroup>
        </Grid>
        <Grid item flexGrow={1}></Grid>
        <Grid item>
          <Button
            onClick={() => {
              incrementVisibleIndex();
            }}
            disabled={visibleIndex === listing.list.length - 1}
            endIcon={<ArrowForwardIcon />}
          >
            Next
          </Button>
        </Grid>
      </Grid>
      <Grid
        item
        flexGrow={1}
        sx={{
          /*
           * This minHeight is necessary to ensure that the image and wrapping
           * div shrink so that they don't cause scrollbars. Once the user
           * initiates a zoom then there will be scrollbars, but not before.
           * The need for a minHeight of 0 is explained in
           * https://moduscreate.com/blog/how-to-fix-overflow-issues-in-css-flex-layouts/
           * which references the "Automatic Minimum Size of Flex Items" part of
           * the flexbox spec:
           * https://drafts.csswg.org/css-flexbox/#min-size-auto
           */
          minHeight: "0",
        }}
      >
        {listing.list.map((f, i) => (
          <Preview
            file={f}
            zoom={zoom}
            visible={i === visibleIndex}
            key={idToString(f.id)}
          />
        ))}
      </Grid>
    </Grid>
  );
}
