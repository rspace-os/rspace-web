import type React from "react";
import "photoswipe/dist/photoswipe.css";
import { Global } from "@emotion/react";
import Box from "@mui/material/Box";
import { Gallery, Item } from "react-photoswipe-gallery";
import type { URL } from "../util/types";

/**
 * The dimensions of the image being previewed.
 */
export type PreviewSize = {
  width: number;
  height: number;
};

type ImagePreviewArgs = {
  closePreview: () => void;
  link: URL;
  size: PreviewSize | null;
  setSize: (newSize: PreviewSize) => void;

  /*
   * A list of strings, shown as plain text from top to bottom with a blank
   * line between each, placed at the bottom of the viewport. Empty strings
   * are skipped.
   */
  caption?: null | ReadonlyArray<string>;
};

/**
 * A full-screen image previewer.
 */
export default function ImagePreview({
  closePreview,
  link,
  size,
  setSize,
  caption,
}: ImagePreviewArgs): React.ReactNode {
  const captionLines = (caption ?? []).filter((line) => line !== "");
  return (
    <>
      <Global
        styles={{
          /*
           * If an image cannot be zoomed in, photoswipe applies the "zoom-out"
           * cursor when tapping will actually cause the gallery to close:
           * "default" would be a more intuitive cursor. However, as soon as we
           * apply "default !important" none of the other, correct, cursors
           * get applied when an image that can be zoomed-in is being viewed at
           * full size, zoomed-in, or is being dragged. As such, we have to
           * reimplement all four of these states.
           */
          ".pswp__img": {
            cursor: "default !important",
            /*
             * When the image doesn't have a background, we apply a checkerboard
             * pattern to make the image as clear as possible. We assume that
             * when there is text, it will be dark and thus a lighter background
             * is preferable.
             */
            background:
              "-webkit-linear-gradient(45deg, rgba(0, 0, 0, 0.0980392) 25%, transparent 25%, transparent 75%, rgba(0, 0, 0, 0.0980392) 75%, rgba(0, 0, 0, 0.0980392) 0), -webkit-linear-gradient(45deg, rgba(0, 0, 0, 0.0980392) 25%, transparent 25%, transparent 75%, rgba(0, 0, 0, 0.0980392) 75%, rgba(0, 0, 0, 0.0980392) 0), white",
            backgroundPosition: "0px 0, 5px 5px",
            backgroundSize: "10px 10px, 10px 10px",
          },
          ".pswp--click-to-zoom.pswp--zoom-allowed .pswp__img": {
            cursor: "zoom-in !important",
          },
          ".pswp--click-to-zoom.pswp--zoomed-in .pswp__img": {
            cursor: "grab !important",
          },
          ".pswp--click-to-zoom.pswp--zoomed-in .pswp__img:active": {
            cursor: "grabbing !important",
          },
          /*
           * The library sets the caption as plain text, so line breaks in it
           * only show if whitespace is preserved.
           */
          ".pswp__default-caption": {
            whiteSpace: "pre-line",
          },
        }}
      />
      <Gallery
        options={{
          showAnimationDuration: 0,
          hideAnimationDuration: 0,
          escKey: false,
        }}
        withDownloadButton
        onOpen={(pswp) => {
          pswp.on("destroy", () => {
            closePreview();
          });
        }}
        withCaption={captionLines.length > 0}
      >
        <Item
          original={link}
          /* thumbnail isn't shown, but it is used when animating */
          thumbnail={link}
          width={size?.width ?? 100}
          height={size?.height ?? 100}
          caption={captionLines.join("\n\n")}
        >
          {({ ref, open: openFn }) => {
            return (
              <Box
                component="img"
                sx={{ height: 0, width: 0 }}
                ref={(node) => {
                  const imageNode = node instanceof HTMLImageElement ? node : null;
                  if (typeof ref === "function") {
                    ref(imageNode);
                  }
                }}
                src={link}
                onLoad={(event: React.SyntheticEvent<HTMLImageElement>) => {
                  const image = event.currentTarget;
                  setSize({
                    width: image.naturalWidth || 100,
                    height: image.naturalHeight || 100,
                  });
                  // for some unknown reason Safari needs 20ms break
                  setTimeout(openFn, 20);
                }}
              />
            );
          }}
        </Item>
      </Gallery>
    </>
  );
}
