declare module "gifenc" {
  type Palette = number[][];

  interface GIFEncoderInstance {
    writeFrame(
      index: Uint8Array,
      width: number,
      height: number,
      opts?: { palette?: Palette; delay?: number; repeat?: number },
    ): void;
    finish(): void;
    bytes(): Uint8Array<ArrayBuffer>;
  }

  export function GIFEncoder(opt?: Record<string, unknown>): GIFEncoderInstance;
  export function quantize(rgba: Uint8ClampedArray, maxColors: number, opts?: Record<string, unknown>): Palette;
  export function applyPalette(rgba: Uint8ClampedArray, palette: Palette, format?: string): Uint8Array;
}
