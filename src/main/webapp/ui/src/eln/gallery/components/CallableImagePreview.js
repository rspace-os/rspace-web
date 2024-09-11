//@flow

import React, { type Node } from "react";
import ImagePreview, {
  type PreviewSize,
} from "../../../Inventory/components/ImagePreview";
import { type URL } from "../../../util/types";

/**
 * Much like how `window.open` allows any JS code on the page to trigger the
 * opening of in a new window/tab with a specified URL, this module provides a
 * mechanism to allow any JS code to trigger the opening of a modal fullscreen
 * lightroom box by providing a URL that points to an image.
 */

const ImagePreviewContext = React.createContext((_link: URL) => {});

/**
 * Use the callable image preview component to display an image in a fullscreen
 * lightroom modal.
 */
export function useImagePreview(): {|
  /**
   * Preview the image to be found at the passed URL. If the image cannot be
   * loaded then nothing happens.
   */
  openImagePreview: (URL) => void,
|} {
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
 */
export function CallableImagePreview({ children }: {| children: Node |}): Node {
  const [link, setLink] = React.useState<null | URL>(null);
  const [previewSize, setPreviewSize] = React.useState<null | PreviewSize>(
    null
  );
  return (
    <>
      <ImagePreviewContext.Provider value={setLink}>
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
        />
      )}
    </>
  );
}
