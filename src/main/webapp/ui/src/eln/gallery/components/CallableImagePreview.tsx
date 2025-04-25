import React from "react";
import ImagePreview, {
  type PreviewSize,
} from "../../../components/ImagePreview";
import { type URL } from "../../../util/types";

/**
 * Much like how `window.open` allows any JS code on the page to trigger the
 * opening of in a new window/tab with a specified URL, this module provides a
 * mechanism to allow any JS code to trigger the opening of a modal fullscreen
 * lightroom box by providing a URL that points to an image.
 */

const ImagePreviewContext = React.createContext(
  (
    _link: URL,
    _opts: {
      caption?: ReadonlyArray<string>;
    } | null
  ) => {}
);

/**
 * Use the callable image preview component to display an image in a fullscreen
 * lightroom modal.
 */
export function useImagePreview(): {
  /**
   * Preview the image to be found at the passed URL. If the image cannot be
   * loaded then nothing happens.
   */
  openImagePreview: (
    link: URL,
    opts: {
      /*
       * A list of strings, shown from top to bottom, separated by two
       * `<br />`s, placed at the bottom of the viewport
       */
      caption?: ReadonlyArray<string>;
    } | null
  ) => void;
} {
  const openImagePreview = React.useContext(ImagePreviewContext);
  return {
    openImagePreview,
  };
}

/**
 * This components provides a mechanism for any other component that is its
 * descendent to trigger the previewing of an image by passing the image's URL
 * to a call to `useImagePreview`'s `openImagePreview`. Just do something like
 *    const { openImagePreview } = useImagePreview();
 *    openImagePreview("http://example.com/image.jpg");
 *
 * An caption can also be passed, which is shown at the bottom of the vewport.
 *    openImagePreview("http://example.com/image.jpg", {
 *      caption: ["Example image with caption"],
 *    });
 */
export function CallableImagePreview({
  children,
}: {
  children: React.ReactNode;
}): React.ReactNode {
  const [link, setLink] = React.useState<null | URL>(null);
  const [previewSize, setPreviewSize] = React.useState<null | PreviewSize>(
    null
  );
  const [caption, setCaption] = React.useState<null | ReadonlyArray<string>>(
    null
  );

  return (
    <>
      <ImagePreviewContext.Provider
        value={(url, opts) => {
          setLink(url);
          if (opts?.caption) {
            setCaption(opts.caption);
          } else {
            setCaption([]);
          }
        }}
      >
        {children}
      </ImagePreviewContext.Provider>
      {link !== null && (
        <ImagePreview
          closePreview={() => {
            setLink(null);
          }}
          link={link}
          size={previewSize}
          setSize={(s) => setPreviewSize(s)}
          caption={caption}
        />
      )}
    </>
  );
}
