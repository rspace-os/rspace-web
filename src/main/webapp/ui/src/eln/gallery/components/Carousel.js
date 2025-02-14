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
import { useSnapGenePreview } from "./CallableSnapGenePreview";
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
import * as ArrayUtils from "../../../util/ArrayUtils";
import * as Parsers from "../../../util/parsers";
import axios from "axios";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { styled } from "@mui/material/styles";
import { COLOR } from "../common";
import ResetZoomIcon from "./ResetZoomIcon";
import Typography from "@mui/material/Typography";
import { useFolderOpen } from "./OpenFolderProvider";
import { doNotAwait } from "../../../util/Util";
import { type URL as Url } from "../../../util/types";

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
  const { openSnapGenePreview } = useSnapGenePreview();
  const { openFolder } = useFolderOpen();
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
      key={file.key}
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
              openFolder(file);
              return;
            }
            if (action.tag === "image") {
              void action.downloadHref().then(openImagePreview);
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
              void action.downloadHref().then(openPdfPreview);
              return;
            }
            if (action.tag === "aspose") {
              void openAsposePreview(file);
            }
            if (action.tag === "snapgene") {
              void openSnapGenePreview(file);
            }
          });
        }
      }}
      onClick={(e) => {
        if (e.detail > 1) {
          primaryAction(file).do((action) => {
            if (action.tag === "open") {
              openFolder(file);
              return;
            }
            if (action.tag === "image") {
              void action.downloadHref().then(openImagePreview);
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
              void action.downloadHref().then(openPdfPreview);
              return;
            }
            if (action.tag === "aspose") {
              void openAsposePreview(file);
            }
            if (action.tag === "snapgene") {
              void openSnapGenePreview(file);
            }
          });
        }
      }}
    >
      {children}
    </div>
  );
};

// eslint-disable-next-line complexity -- Lots of options of what to show in preview
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
    | {| tag: "loaded", url: Url |}
    | {| tag: "error" |}
    | null
  >(null);
  const [imageUrl, setImageUrl] = React.useState<
    | {| tag: "loading" |}
    | {| tag: "loaded", url: Url |}
    | {| tag: "error" |}
    | null
  >(null);
  const [pdfUrl, setPdfUrl] = React.useState<
    | {| tag: "loading" |}
    | {| tag: "loaded", url: Url |}
    | {| tag: "error" |}
    | null
  >(null);

  React.useEffect(() => {
    canPreviewAsImage(file)
      .map((getDownloadHref) => ({ key: "image", getDownloadHref }))
      .orElseTry(() =>
        canPreviewAsPdf(file).map((getDownloadHref) => ({
          key: "pdf",
          getDownloadHref,
        }))
      )
      .orElseTry<
        | {| key: "image", getDownloadHref: () => Promise<Url> |}
        | {| key: "pdf", getDownloadHref: () => Promise<Url> |}
        | {| key: "aspose" |}
      >(() =>
        canPreviewWithAspose(file).map(() => ({
          key: "aspose",
        }))
      )
      .do(
        doNotAwait(async (preview) => {
          if (preview.key === "image") {
            setImageUrl({ tag: "loading" });
            try {
              const downloadHref = await preview.getDownloadHref();
              setImageUrl({ tag: "loaded", url: downloadHref });
            } catch (error) {
              setImageUrl({ tag: "error" });
            }
          } else if (preview.key === "pdf") {
            setPdfUrl({ tag: "loading" });
            try {
              const downloadHref = await preview.getDownloadHref();
              setPdfUrl({ tag: "loaded", url: downloadHref });
            } catch (error) {
              setPdfUrl({ tag: "error" });
            }
          } else {
            setAsposePdfUrl({ tag: "loading" });
            try {
              const { data } = await axios.get<mixed>(
                "/Streamfile/ajax/convert/" +
                  idToString(file.id).elseThrow() +
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
                    idToString(file.id).elseThrow() +
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
          }
        })
      );
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - canPreviewWithAspose will not change
     * - file will not change because the call site use `file.id` as the key
     */
  }, []);

  function onDocumentLoadSuccess({
    numPages: nextNumPages,
  }: {
    numPages: number,
    ...
  }): void {
    setNumPages(nextNumPages);
  }

  let loadingLabel = null;
  if (imageUrl !== null && imageUrl.tag === "loading")
    loadingLabel = "Loading image...";
  if (pdfUrl !== null && pdfUrl.tag === "loading")
    loadingLabel = "Loading PDF...";
  if (asposePdfUrl !== null && asposePdfUrl.tag === "loading")
    loadingLabel = "Generating PDF...";
  if (loadingLabel !== null)
    return (
      <PreviewWrapper file={file} previewingAsPdf={true} visible={visible}>
        {loadingLabel}
      </PreviewWrapper>
    );

  let imageSrc = null;
  if (imageUrl === null && pdfUrl === null && asposePdfUrl === null)
    imageSrc = file.thumbnailUrl;
  if (imageUrl !== null && imageUrl.tag === "loaded") imageSrc = imageUrl.url;
  if (imageUrl !== null && imageUrl.tag === "error")
    imageSrc = file.thumbnailUrl;
  if (pdfUrl !== null && pdfUrl.tag === "error") imageSrc = file.thumbnailUrl;
  if (asposePdfUrl !== null && asposePdfUrl.tag === "error")
    imageSrc = file.thumbnailUrl;
  if (imageSrc !== null)
    return (
      <PreviewWrapper file={file} previewingAsPdf={false} visible={visible}>
        <img
          alt={`Preview of ${file.name}`}
          src={imageSrc}
          style={{
            maxHeight: "100%",
            maxWidth: "100%",
            transform: `scale(${zoom})`,
            transition: "transform .5s ease-in-out",
            transformOrigin: "left top",
          }}
          key={imageSrc}
        />
      </PreviewWrapper>
    );

  let pdfSrc = null;
  if (pdfUrl !== null && pdfUrl.tag === "loaded") pdfSrc = pdfUrl.url;
  if (asposePdfUrl !== null && asposePdfUrl.tag === "loaded")
    pdfSrc = asposePdfUrl.url;
  if (pdfSrc !== null)
    return (
      <PreviewWrapper file={file} previewingAsPdf={true} visible={visible}>
        <StyledDocument file={pdfSrc} onLoadSuccess={onDocumentLoadSuccess}>
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

  return null;
};

type CarouselArgs = {|
  listing:
    | {| tag: "empty", reason: string, refreshing: boolean |}
    | {|
        tag: "list",
        list: $ReadOnlyArray<GalleryFile>,
        totalHits: number,
        loadMore: Optional<() => Promise<void>>,
        refreshing: boolean,
      |},
|};

/**
 * The carousel view of files allows the user to view files one at a time,
 * using the preview (image or pdf) of the file to identify it. The user can
 * also zoom in and out of the preview; crucial when working with large image
 * files that vary only in small details.
 */
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
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - selection will not change
     */
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
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - decrementVisibleIndex will not meaningfully change
     * - incrementVisibleIndex will not meaningfully change
     */
  }, [visibleIndex, listing]);

  if (listing.tag === "empty")
    return (
      <PlaceholderLabel>
        {listing.refreshing
          ? "Refreshing..."
          : listing.reason ?? "There are no folders."}
      </PlaceholderLabel>
    );
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
      role="region"
      aria-label="Carousel view of files"
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
        <Typography
          variant="body2"
          sx={{
            alignSelf: "center",
            pl: 1,
            fontWeight: 500,
          }}
        >
          {visibleIndex + 1} / {listing.totalHits}
        </Typography>
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
                setZoom(1);
              }}
              disabled={zoom === 1}
              aria-label="reset zoom"
              size="small"
            >
              <ResetZoomIcon />
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
            key={f.key}
          />
        ))}
      </Grid>
    </Grid>
  );
}
