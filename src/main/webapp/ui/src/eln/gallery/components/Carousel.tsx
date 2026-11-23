import ZoomInIcon from "@mui/icons-material/ZoomIn";
import ZoomOutIcon from "@mui/icons-material/ZoomOut";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import ButtonGroup from "@mui/material/ButtonGroup";
import Divider from "@mui/material/Divider";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import React from "react";
import { useTranslation } from "react-i18next";
import { Document, Page } from "react-pdf";
import { incrementForever, take } from "../../../util/iterators";
import type { Optional } from "../../../util/optional";
import usePrimaryAction, {
  useAsposePreviewOfGalleryFile,
  useImagePreviewOfGalleryFile,
  usePdfPreviewOfGalleryFile,
} from "../primaryActionHooks";
import { useAsposePdfQuery } from "../queries";
import type { GalleryFile } from "../useGalleryListing";
import { useGallerySelection } from "../useGallerySelection";
import { useAsposePreview } from "./CallableAsposePreview";
import { useImagePreview } from "./CallableImagePreview";
import { usePdfPreview } from "./CallablePdfPreview";
import { useSnapGenePreview } from "./CallableSnapGenePreview";
import { useSnippetPreview } from "./CallableSnippetPreview";
import PlaceholderLabel from "./PlaceholderLabel";
import "react-pdf/dist/Page/TextLayer.css";
import "react-pdf/dist/Page/AnnotationLayer.css";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import Typography from "@mui/material/Typography";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/gallery";
import type { URL as Url } from "../../../util/types";
import { useFolderOpen } from "./OpenFolderProvider";
import ResetZoomIcon from "./ResetZoomIcon";

/*
 * This snippet is a necessary step in initialising the PDF preview
 * functionality. Taken from the example code for react-pdf.
 */
// @TODO This may not be necessary
// pdfjs.GlobalWorkerOptions.workerSrc = new URL(
//   "pdfjs-dist/build/pdf.worker.min.mjs",
//   import.meta.url,
// ).toString();

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
}: {
  file: GalleryFile;
  previewingAsPdf: boolean;
  children: React.ReactNode;
  visible: boolean;
}) => {
  const { openFolder } = useFolderOpen();
  const { openImagePreview } = useImagePreview();
  const { openPdfPreview } = usePdfPreview();
  const { openAsposePreview } = useAsposePreview();
  const { openSnapGenePreview } = useSnapGenePreview();
  const { openSnippetPreview } = useSnippetPreview();
  const primaryAction = usePrimaryAction();
  const [scrollPos, setScrollPos] = React.useState<null | {
    scrollLeft: number;
    scrollTop: number;
  }>(null);
  const [cursorOffset, setCursorOffset] = React.useState<null | {
    x: number;
    y: number;
  }>(null);

  function display() {
    if (!visible) return "none";
    if (previewingAsPdf) return "block";
    return "flex";
  }

  return (
    <Box
      role="button"
      tabIndex={0}
      sx={{
        borderRadius: "3px",
        border: `2px solid hsl(${ACCENT_COLOR.background.hue}deg, ${ACCENT_COLOR.background.saturation}%, ${ACCENT_COLOR.background.lightness}%)`,
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
        const thisNode = (e.target as HTMLElement).closest("[role='button']");
        if (!thisNode) return;
        setScrollPos({
          scrollLeft: thisNode.scrollLeft,
          scrollTop: thisNode.scrollTop,
        });
        setCursorOffset({ x: e.nativeEvent.clientX, y: e.nativeEvent.clientY });
      }}
      onMouseMove={(e) => {
        if (!scrollPos || !cursorOffset) return;
        const thisNode = (e.target as HTMLElement).closest("[role='button']");
        const currentOffset = {
          x: e.nativeEvent.clientX,
          y: e.nativeEvent.clientY,
        };
        const moved = {
          x: currentOffset.x - cursorOffset.x,
          y: currentOffset.y - cursorOffset.y,
        };
        thisNode?.scrollTo(scrollPos.scrollLeft - moved.x, scrollPos.scrollTop - moved.y);
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
            if (action.tag === "snippet") {
              void openSnippetPreview(file);
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
    </Box>
  );
};

const Preview = React.memo(function Preview({
  file,
  zoom,
  visible,
}: {
  file: GalleryFile;
  zoom: number;
  visible: boolean;
}) {
  const { t } = useTranslation("gallery");
  const canPreviewAsImage = useImagePreviewOfGalleryFile();
  const canPreviewAsPdf = usePdfPreviewOfGalleryFile();
  const canPreviewWithAspose = useAsposePreviewOfGalleryFile();
  // a new version of the file is loaded afresh; an unchanged file from a refreshed listing keeps its preview
  // filestore files have no version, so their size and modification date stand in for one
  const fileVersion = `${file.key}@${file.version ?? `${file.size}@${file.modificationDate?.getTime() ?? ""}`}`;
  const loadedVersion = React.useRef<string | null>(null);
  const [numPages, setNumPages] = React.useState<number>(0);
  const [imageUrl, setImageUrl] = React.useState<
    { tag: "loading" } | { tag: "loaded"; url: Url } | { tag: "error" } | null
  >(null);
  const [pdfUrl, setPdfUrl] = React.useState<
    { tag: "loading" } | { tag: "loaded"; url: Url } | { tag: "error" } | null
  >(null);

  React.useEffect(() => {
    if (loadedVersion.current === fileVersion) return;
    canPreviewAsImage(file)
      .map((getDownloadHref) => ({ key: "image", getDownloadHref }))
      .orElseTry(() =>
        canPreviewAsPdf(file).map((getDownloadHref) => ({
          key: "pdf",
          getDownloadHref,
        })),
      )
      .do((preview) => {
        loadedVersion.current = fileVersion;
        // drops the result of loading a version that has since been replaced
        const ifStillCurrent = (update: () => void) => {
          if (loadedVersion.current === fileVersion) update();
        };
        void (async () => {
          if (preview.key === "image") {
            setImageUrl({ tag: "loading" });
            try {
              const downloadHref = await preview.getDownloadHref();
              ifStillCurrent(() => setImageUrl({ tag: "loaded", url: downloadHref }));
            } catch {
              ifStillCurrent(() => setImageUrl({ tag: "error" }));
            }
          } else {
            setPdfUrl({ tag: "loading" });
            try {
              const downloadHref = await preview.getDownloadHref();
              ifStillCurrent(() => setPdfUrl({ tag: "loaded", url: downloadHref }));
            } catch {
              ifStillCurrent(() => setPdfUrl({ tag: "error" }));
            }
          }
        })();
      });
  }, [fileVersion]);

  // conversion is expensive, so only the visible file is converted; aspose.enabled may load late
  const asposePdf = useAsposePdfQuery(file, {
    enabled:
      visible && canPreviewAsImage(file).isError && canPreviewAsPdf(file).isError && canPreviewWithAspose(file).isOk,
  });

  function onDocumentLoadSuccess({ numPages: nextNumPages }: { numPages: number }): void {
    setNumPages(nextNumPages);
  }

  let loadingLabel = null;
  if (imageUrl !== null && imageUrl.tag === "loading") loadingLabel = t("carousel.loadingImage");
  if (pdfUrl !== null && pdfUrl.tag === "loading") loadingLabel = t("carousel.loadingPdf");
  if (asposePdf.isLoading) loadingLabel = t("carousel.generatingPdf");
  if (loadingLabel !== null)
    return (
      <PreviewWrapper file={file} previewingAsPdf={true} visible={visible}>
        {loadingLabel}
      </PreviewWrapper>
    );

  let imageSrc = null;
  if (imageUrl === null && pdfUrl === null && asposePdf.data === undefined) imageSrc = file.thumbnailUrl;
  if (imageUrl !== null && imageUrl.tag === "loaded") imageSrc = imageUrl.url;
  if (imageUrl !== null && imageUrl.tag === "error") imageSrc = file.thumbnailUrl;
  if (pdfUrl !== null && pdfUrl.tag === "error") imageSrc = file.thumbnailUrl;
  if (imageSrc !== null)
    return (
      <PreviewWrapper file={file} previewingAsPdf={false} visible={visible}>
        <Box
          component="img"
          alt={t("carousel.previewAlt", { name: file.name })}
          src={imageSrc}
          sx={{
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
  if (asposePdf.data !== undefined) pdfSrc = asposePdf.data;
  if (pdfSrc !== null)
    return (
      <PreviewWrapper file={file} previewingAsPdf={true} visible={visible}>
        <Box
          sx={{
            "& .textLayer .endOfContent": {
              cursor: "grabbing",
            },
          }}
        >
          <Document file={pdfSrc} onLoadSuccess={onDocumentLoadSuccess}>
            {[...take(incrementForever(), numPages)].map((index) => (
              <Page key={`page_${index + 1}`} pageNumber={index + 1} scale={zoom} />
            ))}
          </Document>
        </Box>
      </PreviewWrapper>
    );

  return null;
});

type CarouselArgs = {
  listing:
    | { tag: "empty"; reason: string; refreshing: boolean }
    | {
        tag: "list";
        list: ReadonlyArray<GalleryFile>;
        totalHits: number;
        loadMore: Optional<() => Promise<void>>;
        refreshing: boolean;
      };
};

function indexOfFile(list: ReadonlyArray<GalleryFile>, file: GalleryFile): number {
  return Math.max(
    0,
    list.findIndex(({ id }) => id === file.id),
  );
}

/**
 * The carousel view of files allows the user to view files one at a time,
 * using the preview (image or pdf) of the file to identify it. The user can
 * also zoom in and out of the preview; crucial when working with large image
 * files that vary only in small details.
 */
export default function Carousel({ listing }: CarouselArgs): React.ReactNode {
  const selection = useGallerySelection();
  // start on the selected file so that the first previews mounted are the ones shown
  const [visibleIndex, setVisibleIndex] = React.useState(() =>
    listing.tag === "list"
      ? selection
          .asSet()
          .only.map((selectedFile) => indexOfFile(listing.list, selectedFile))
          .orElse(0)
      : 0,
  );
  const [zoom, setZoom] = React.useState(1);
  const { t } = useTranslation(["gallery", "common"]);

  React.useEffect(() => {
    if (listing.tag !== "list") return;
    // when enter Carousel view, focus on already selected file
    selection.asSet().only.do((selectedFile) => {
      setVisibleIndex(indexOfFile(listing.list, selectedFile));
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
    return (
      <PlaceholderLabel>
        {listing.refreshing ? t("carousel.refreshing") : (listing.reason ?? t("carousel.noFolders"))}
      </PlaceholderLabel>
    );
  return (
    <Stack
      sx={{
        flexWrap: "nowrap",
        /*
         * it looks better if the preview panel's bottom border lines up with
         * the bottom of the InfoPanel divider
         */
        height: "calc(100% + 8px)",
      }}
      spacing={1}
      role="region"
      aria-label={t("carousel.label")}
    >
      <Stack direction="row" spacing={1}>
        <Button
          onClick={() => {
            decrementVisibleIndex();
          }}
          disabled={visibleIndex === 0}
          startIcon={<ArrowBackIcon />}
        >
          {t("carousel.previous")}
        </Button>
        <Typography
          role="status"
          aria-label={t("carousel.currentFileIndex")}
          variant="body2"
          sx={{
            alignSelf: "center",
            pl: 1,
            fontWeight: 500,
          }}
        >
          {`${visibleIndex + 1} / ${listing.totalHits}`}
        </Typography>
        <Box sx={{ flexGrow: 1 }} />
        <ButtonGroup
          variant="outlined"
          sx={{
            border: `2px solid hsl(${ACCENT_COLOR.background.hue}deg, ${ACCENT_COLOR.background.saturation}%, ${ACCENT_COLOR.background.lightness}%)`,
            borderRadius: "8px",
          }}
        >
          <IconButton
            onClick={() => {
              setZoom((z) => z * ZOOM_SCALE_FACTOR);
            }}
            aria-label={t("common:actions.zoomIn")}
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
            aria-label={t("common:actions.resetZoom")}
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
            aria-label={t("common:actions.zoomOut")}
            size="small"
          >
            <ZoomOutIcon />
          </IconButton>
        </ButtonGroup>
        <Box sx={{ flexGrow: 1 }} />
        <Button
          onClick={() => {
            incrementVisibleIndex();
          }}
          disabled={visibleIndex === listing.list.length - 1}
          endIcon={<ArrowForwardIcon />}
        >
          {t("carousel.next")}
        </Button>
      </Stack>
      <Box
        sx={{
          flexGrow: 1,
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
        {/* Only the visible file and its neighbours are mounted, so the rest are not downloaded */}
        {listing.list.map((f, i) =>
          Math.abs(i - visibleIndex) <= 1 ? (
            <Preview file={f} zoom={i === visibleIndex ? zoom : 1} visible={i === visibleIndex} key={f.key} />
          ) : null,
        )}
      </Box>
    </Stack>
  );
}
