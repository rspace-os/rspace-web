import { queryOptions } from "@tanstack/react-query";
import * as v from "valibot";
import axios from "@/common/axios";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

// Reason codes stay plain strings so a code added by the server later still parses.
const BondSchema = v.object({
  label: v.string(),
  hydrogenCount: v.nullish(v.number()),
  supported: v.boolean(),
  unsupportedReason: v.nullish(v.string()),
  bdeKcalMol: v.nullish(v.number()),
  bdeKjMol: v.nullish(v.number()),
  weakest: v.optional(v.boolean(), false),
});
export type Bond = v.InferOutput<typeof BondSchema>;

const MethodSchema = v.object({
  name: v.string(),
  version: v.string(),
  weightsSha256: v.string(),
  meanAbsoluteErrorKcalMol: v.number(),
  crossCheck: v.nullish(v.object({ maxDifferenceKcalMol: v.number() })),
  quantity: v.nullish(v.string()),
  accuracyNote: v.nullish(v.string()),
  referenceMethod: v.nullish(v.string()),
  trainingData: v.nullish(v.string()),
});

const BondStrengthsSchema = v.variant("supported", [
  v.object({
    supported: v.literal(true),
    bonds: v.array(BondSchema),
    supportedBondCount: v.number(),
    totalBondCount: v.number(),
    imageSvg: v.nullish(v.string()),
    method: MethodSchema,
  }),
  v.object({
    supported: v.literal(false),
    unsupportedReason: v.nullish(v.string()),
    unsupportedDetail: v.nullish(v.string()),
  }),
]);
export type BondStrengths = v.InferOutput<typeof BondStrengthsSchema>;
export type SupportedBondStrengths = Extract<BondStrengths, { supported: true }>;
export type UnsupportedBondStrengths = Extract<BondStrengths, { supported: false }>;

const BondStrengthsResponseSchema = v.object({ data: v.nullish(BondStrengthsSchema) });

export function bondStrengthsQuery(chemId: number, revision?: number) {
  return queryOptions({
    queryKey: ["rspace.tinymce.bondStrengths", chemId, revision ?? null] as const,
    queryFn: async (): Promise<BondStrengths> => {
      const response = await axios.get<unknown>("/chemical/ajax/bondStrengths", { params: { chemId, revision } });
      const { data } = parseOrThrow(BondStrengthsResponseSchema, response.data);
      if (!data) throw new Error("The bond strengths response has no data");
      return data;
    },
    // The structure behind a chem id can be edited, so never show a result kept from an earlier open.
    gcTime: 0,
    retry: false,
  });
}

/** Weakest first; bonds without a value keep the server's order and go last. */
export function sortWeakestFirst(bonds: ReadonlyArray<Bond>): Bond[] {
  const strength = (bond: Bond) =>
    bond.supported && typeof bond.bdeKcalMol === "number" ? bond.bdeKcalMol : Number.POSITIVE_INFINITY;
  return bonds.toSorted((a, b) => {
    const [strengthA, strengthB] = [strength(a), strength(b)];
    return strengthA === strengthB ? 0 : strengthA - strengthB;
  });
}

const DOCUMENT_PICTURE_WIDTH = 960;

/** The editor removes SVG data URIs when it saves, so the picture for the document is drawn to a PNG. */
export function svgToPngDataUri(svg: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => {
      // A throw in an event handler would leave the promise pending for ever.
      try {
        const canvas = document.createElement("canvas");
        canvas.width = DOCUMENT_PICTURE_WIDTH;
        canvas.height = Math.round((DOCUMENT_PICTURE_WIDTH * image.naturalHeight) / image.naturalWidth);
        const context = canvas.getContext("2d");
        if (!context) throw new Error("No 2D canvas is available");
        context.drawImage(image, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL("image/png"));
      } catch (error) {
        reject(error instanceof Error ? error : new Error("The picture could not be drawn"));
      }
    };
    image.onerror = () => reject(new Error("The picture could not be drawn"));
    image.src = svgDataUri(svg);
  });
}

/** The picture is only ever used as an `<img>` source, so scripts inside the SVG cannot run. */
export function svgDataUri(svg: string): string {
  // The service declares a single-byte encoding; drop the declaration so the UTF-8 URI is read as UTF-8.
  const withoutXmlDeclaration = svg.replace(/^\s*<\?xml[^>]*\?>\s*/, "");
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(withoutXmlDeclaration)}`;
}
