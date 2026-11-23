import React from "react";
import { Document, Page, type PageProps } from "react-pdf";
import { incrementForever, take } from "../../../util/iterators";
import "react-pdf/dist/Page/TextLayer.css";
import "react-pdf/dist/Page/AnnotationLayer.css";

type PageSize = { width: number; height: number };

/*
 * US Letter, in PDF points. Only used to size placeholders until a page of the
 * document has loaded and reported its real size.
 */
const FALLBACK_PAGE_SIZE: PageSize = { width: 612, height: 792 };

/*
 * Pages within half a viewport of the scroll container's visible area are
 * mounted, so the next page is usually painted before it scrolls into view.
 */
const ROOT_MARGIN = "50% 0px";

function findScrollContainer(element: HTMLElement): HTMLElement | null {
  for (let node = element.parentElement; node; node = node.parentElement) {
    const { overflowY } = window.getComputedStyle(node);
    if (overflowY === "auto" || overflowY === "scroll") return node;
  }
  return null;
}

type WindowedPdfDocumentProps = {
  file: string;
  scale: number;

  /**
   * The width of each page at a scale of 1, in CSS pixels. When omitted, each
   * page is rendered at its own size.
   */
  width?: number;
};

function WindowedPages({ file, scale, width }: WindowedPdfDocumentProps): React.ReactNode {
  const [numPages, setNumPages] = React.useState(0);
  const [pageSizes, setPageSizes] = React.useState<ReadonlyMap<number, PageSize>>(new Map());
  const [nearbyPages, setNearbyPages] = React.useState<ReadonlySet<number>>(new Set());
  const pagesRef = React.useRef<HTMLDivElement>(null);

  React.useEffect(() => {
    const container = pagesRef.current;
    if (!container) return;
    const pageNumberBySlot = new Map<Element, number>();
    const observer = new IntersectionObserver(
      (entries) => {
        setNearbyPages((previous) => {
          const next = new Set(previous);
          for (const entry of entries) {
            const pageNumber = pageNumberBySlot.get(entry.target);
            if (pageNumber === undefined) continue;
            if (entry.isIntersecting) next.add(pageNumber);
            else next.delete(pageNumber);
          }
          return next;
        });
      },
      { root: findScrollContainer(container), rootMargin: ROOT_MARGIN },
    );
    Array.from(container.children).forEach((slot, index) => {
      pageNumberBySlot.set(slot, index + 1);
      observer.observe(slot);
    });
    return () => observer.disconnect();
  }, [numPages]);

  const recordPageSize: PageProps["onLoadSuccess"] = ({ pageNumber, originalWidth, originalHeight }) => {
    setPageSizes((previous) => {
      const known = previous.get(pageNumber);
      if (known?.width === originalWidth && known.height === originalHeight) return previous;
      return new Map(previous).set(pageNumber, { width: originalWidth, height: originalHeight });
    });
  };

  /*
   * Mirrors react-pdf's own sizing: a fixed width overrides the page's natural
   * width, and scale applies on top of that.
   */
  function renderedSize(size: PageSize): PageSize {
    const pageScale = (width ? width / size.width : 1) * scale;
    return { width: size.width * pageScale, height: size.height * pageScale };
  }

  /*
   * Pages that have not loaded yet are assumed to be the same size as the
   * first page, which is right for the vast majority of documents.
   */
  const estimatedSize = pageSizes.get(1) ?? FALLBACK_PAGE_SIZE;

  return (
    <Document file={file} onLoadSuccess={({ numPages: loadedNumPages }) => setNumPages(loadedNumPages)}>
      <div ref={pagesRef}>
        {[...take(incrementForever(), numPages)].map((index) => {
          const pageNumber = index + 1;
          const { width: minWidth, height: minHeight } = renderedSize(pageSizes.get(pageNumber) ?? estimatedSize);
          return (
            <div key={pageNumber} style={{ minWidth, minHeight }}>
              {nearbyPages.has(pageNumber) && (
                <Page pageNumber={pageNumber} width={width} scale={scale} onLoadSuccess={recordPageSize} />
              )}
            </div>
          );
        })}
      </div>
    </Document>
  );
}

/**
 * Renders a PDF as a vertical stack of pages, but only mounts the pages that
 * are in or near the visible area of the closest scrollable ancestor. Every
 * other page is an empty placeholder of the same size, so the scroll height
 * matches the full document and scrolling does not jump as pages mount and
 * unmount.
 */
export default function WindowedPdfDocument(props: WindowedPdfDocumentProps): React.ReactNode {
  // Remount on a new file so page sizes and visibility do not carry over.
  return <WindowedPages key={props.file} {...props} />;
}
