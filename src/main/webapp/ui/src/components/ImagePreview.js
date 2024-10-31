//@flow

import React, { type Node } from "react";
import "photoswipe/dist/photoswipe.css";
import { Gallery, Item } from "react-photoswipe-gallery";
import { type URL } from "../util/types";
import { makeStyles } from "tss-react/mui";

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
  );
}
