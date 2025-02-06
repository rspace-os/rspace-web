//@flow

import React, { type Node } from "react";
import "photoswipe/dist/photoswipe.css";
import { Gallery, Item } from "react-photoswipe-gallery";
import { type URL } from "../util/types";
import { makeStyles } from "tss-react/mui";
import { Global } from "@emotion/react";

function escapeHtml(unsafe: string) {
  return unsafe
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

const useStyles = makeStyles()(() => ({
  image: {
    height: 0,
    width: 0,
  },
}));

export type PreviewSize = {|
  width: number,
  height: number,
|};

type ImagePreviewArgs = {|
  closePreview: () => void,
  link: URL,
  size: ?PreviewSize,
  setSize: (PreviewSize) => void,
  modal?: boolean,

  /*
   * A list of strings, shown from top to bottom, separated by two `<br />`s,
   * placed at the bottom of the viewport
   */
  caption?: null | $ReadOnlyArray<string>,
|};

export default function ImagePreview({
  closePreview,
  link,
  size,
  setSize,
  modal = true,
  caption,
}: ImagePreviewArgs): Node {
  const { classes } = useStyles();
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
        }}
      />
      <Gallery
        options={{
          showAnimationDuration: 0,
          hideAnimationDuration: 0,
          modal,
          escKey: false,
        }}
        withDownloadButton
        onOpen={(pswp) => {
          pswp.on("destroy", () => {
            closePreview();
          });
        }}
        withCaption={(caption ?? []).length > 0}
      >
        <Item
          original={link}
          /* thumbnail isn't shown, but it is used when animating */
          thumbnail={link}
          width={size?.width ?? 100}
          height={size?.height ?? 100}
          caption={(caption ?? []).map(escapeHtml).join("<br /><br />")}
        >
          {({ ref, open: openFn }) => (
            <img
              className={classes.image}
              ref={ref}
              src={link}
              onLoad={() => {
                setSize({
                  width: ref.current?.naturalWidth,
                  height: ref.current?.naturalHeight,
                });
                // for some unknown reason Safari needs 20ms break
                setTimeout(openFn, 20);
              }}
            />
          )}
        </Item>
      </Gallery>
    </>
  );
}
