//@flow

/**
 * Scales the dimensions of images larger than 1MB down to approximately 1MB.
 * All strings here are data URLs.
 * `canvas` must be a Canvas element, hidden, somewhere in the DOM.
 */
export function capImageAt1MB(
  file: Blob,
  dataURL: string,
  canvasId: string
): Promise<string> {
  const canvas = document.getElementById(canvasId);
  if (canvas instanceof HTMLCanvasElement) {
    const _1MB = 1000000;
    return new Promise((resolve) => {
      const areaScaleFactor = Math.min(1, _1MB / file.size);
      const lengthScaleFactor = Math.sqrt(areaScaleFactor);
      const image = new Image();
      image.onload = function () {
        const ctx = canvas.getContext("2d");
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        image.width *= lengthScaleFactor;
        image.height *= lengthScaleFactor;
        canvas.width = image.width;
        canvas.height = image.height;
        ctx.drawImage(image, 0, 0, image.width, image.height);
        resolve(canvas.toDataURL(file.type));
      };
      image.src = dataURL;
    });
  }
  throw new Error("Could not find canvas");
}
