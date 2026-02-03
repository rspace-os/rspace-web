/**
 * Mocks for HTMLImageElement and HTMLCanvasElement for testing
 * These mocks provide proper behavior in JSDOM test environment
 */

const getMockProperty = (
  obj: unknown,
  key: string,
  defaultValue: number,
): number => {
  return ((obj as Record<string, unknown>)[key] as number) ?? defaultValue;
};

const setMockProperty = (obj: unknown, key: string, value: number): void => {
  (obj as Record<string, unknown>)[key] = value;
};

// Mock HTMLImageElement properties
if (typeof HTMLImageElement !== "undefined") {
  Object.defineProperty(HTMLImageElement.prototype, "width", {
    configurable: true,
    get() {
      return getMockProperty(this, "__mockWidth", 600);
    },
    set(value: number) {
      setMockProperty(this, "__mockWidth", value);
    },
  });

  Object.defineProperty(HTMLImageElement.prototype, "height", {
    configurable: true,
    get() {
      return getMockProperty(this, "__mockHeight", 600);
    },
    set(value: number) {
      setMockProperty(this, "__mockHeight", value);
    },
  });

  Object.defineProperty(HTMLImageElement.prototype, "complete", {
    configurable: true,
    get() {
      return true;
    },
  });

  Object.defineProperty(HTMLImageElement.prototype, "naturalWidth", {
    configurable: true,
    get() {
      return getMockProperty(this, "__mockNaturalWidth", 600);
    },
  });

  Object.defineProperty(HTMLImageElement.prototype, "naturalHeight", {
    configurable: true,
    get() {
      return getMockProperty(this, "__mockNaturalHeight", 600);
    },
  });

  const originalDescriptor = Object.getOwnPropertyDescriptor(
    HTMLImageElement.prototype,
    "src",
  );

  Object.defineProperty(HTMLImageElement.prototype, "src", {
    configurable: true,
    get() {

      return originalDescriptor?.get
        ? (originalDescriptor.get as (this: HTMLImageElement) => string).call(
            this as HTMLImageElement,
          )
        : "";
    },
    set(_value: string) {
      // Set default dimensions when src is set
      setMockProperty(
        this,
        "__mockWidth",
        getMockProperty(this, "__mockWidth", 600),
      );
      setMockProperty(
        this,
        "__mockHeight",
        getMockProperty(this, "__mockHeight", 600),
      );
      setMockProperty(
        this,
        "__mockNaturalWidth",
        getMockProperty(this, "__mockNaturalWidth", 600),
      );
      setMockProperty(
        this,
        "__mockNaturalHeight",
        getMockProperty(this, "__mockNaturalHeight", 600),
      );

      queueMicrotask(() => {
        (this as { dispatchEvent?: (event: Event) => void }).dispatchEvent?.(
          new Event("load"),
        );
      });
    },
  });
}

// Mock HTMLCanvasElement methods and properties
if (typeof HTMLCanvasElement !== "undefined") {
  // @ts-expect-error - We're intentionally overriding getContext method for testing
  HTMLCanvasElement.prototype.getContext = function (contextId: string) {
    if (contextId === "2d") {
      return {
        canvas: this,
        save: () => {},
        restore: () => {},
        drawImage: () => {},
        clearRect: () => {},
        translate: () => {},
        rotate: () => {},
        scale: () => {},
        setTransform: () => {},
        measureText: (text: string) => ({ width: String(text).length }),
        beginPath: () => {},
        rect: () => {},
        clip: () => {},
        fillRect: () => {},
        getImageData: () => ({ data: [] }),
        putImageData: () => {},
        createLinearGradient: () => ({
          addColorStop: () => {},
        }),
        createRadialGradient: () => ({
          addColorStop: () => {},
        }),
        createPattern: () => ({
          setTransform: () => {},
        }),
        toString: () => "CanvasRenderingContext2D",
      } as unknown as CanvasRenderingContext2D;
    }
    return null;
  };

  HTMLCanvasElement.prototype.toDataURL = function () {
    return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";
  };

  HTMLCanvasElement.prototype.toBlob = function (callback: BlobCallback) {
    // Create a 1x1 pixel PNG blob
    const bytes = [
      0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d,
      0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
      0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53, 0xde, 0x00, 0x00, 0x00,
      0x0c, 0x49, 0x44, 0x41, 0x54, 0x08, 0x57, 0x63, 0xf8, 0x0f, 0x00, 0x00,
      0x01, 0x00, 0x01, 0x48, 0x89, 0x63, 0xf8, 0x0f, 0x00, 0x00, 0x00, 0x00,
      0x49, 0x45, 0x4e, 0x44, 0xae, 0x42, 0x60, 0x82,
    ];
    callback(new Blob([new Uint8Array(bytes)], { type: "image/png" }));
  };

  // Add width and height properties
  Object.defineProperty(HTMLCanvasElement.prototype, "width", {
    configurable: true,
    get() {
      return getMockProperty(this, "__mockWidth", 600);
    },
    set(value: number) {
      setMockProperty(this, "__mockWidth", value);
    },
  });

  Object.defineProperty(HTMLCanvasElement.prototype, "height", {
    configurable: true,
    get() {
      return getMockProperty(this, "__mockHeight", 400);
    },
    set(value: number) {
      setMockProperty(this, "__mockHeight", value);
    },
  });
}
