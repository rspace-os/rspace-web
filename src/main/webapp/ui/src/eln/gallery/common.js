//@flow

import { COLORS as baseThemeColors } from "../../theme";
import Result from "../../util/result";
import * as Parsers from "../../util/parsers";

export type GallerySection =
  | "Images"
  | "Audios"
  | "Videos"
  | "Documents"
  | "Chemistry"
  | "DMPs"
  | "NetworkFiles"
  | "Snippets"
  | "Miscellaneous"
  | "PdfDocuments";

export const GALLERY_SECTION = {
  IMAGES: "Images",
  AUDIOS: "Audios",
  VIDEOS: "Videos",
  DOCUMENTS: "Documents",
  CHEMISTRY: "Chemistry",
  DMPS: "DMPs",
  NETWORKFILES: "NetworkFiles",
  SNIPPETS: "Snippets",
  MISCELLANEOUS: "Miscellaneous",
  PDFDOCUMENTS: "PdfDocuments",
};

export const parseGallerySectionFromUrlSearchParams = (
  searchParams: URLSearchParams
): Result<GallerySection> =>
  Result.fromNullable(
    searchParams.get("mediaType"),
    new Error("No search parameter with name 'mediaType'")
  ).flatMap((mediaType) =>
    Result.first(
      Parsers.parseString(GALLERY_SECTION.IMAGES, mediaType),
      Parsers.parseString(GALLERY_SECTION.AUDIOS, mediaType),
      Parsers.parseString(GALLERY_SECTION.VIDEOS, mediaType),
      Parsers.parseString(GALLERY_SECTION.DOCUMENTS, mediaType),
      Parsers.parseString(GALLERY_SECTION.CHEMISTRY, mediaType),
      Parsers.parseString(GALLERY_SECTION.DMPS, mediaType),
      Parsers.parseString(GALLERY_SECTION.NETWORKFILES, mediaType),
      Parsers.parseString(GALLERY_SECTION.SNIPPETS, mediaType),
      Parsers.parseString(GALLERY_SECTION.MISCELLANEOUS, mediaType),
      Parsers.parseString(GALLERY_SECTION.PDFDOCUMENTS, mediaType)
    )
  );

export const gallerySectionLabel = {
  Images: "Images",
  Audios: "Audio",
  Videos: "Videos",
  Documents: "Documents",
  Chemistry: "Chemistry",
  DMPs: "DMPs",
  NetworkFiles: "Filestores",
  Snippets: "Snippets",
  Miscellaneous: "Miscellaneous",
  PdfDocuments: "Exports",
};

export const gallerySectionCollectiveNoun = {
  Images: "images",
  Audios: "audio files",
  Videos: "video files",
  Documents: "documents",
  Chemistry: "chemistry files",
  DMPs: "DMPs",
  NetworkFiles: "filestores",
  Snippets: "snippets",
  Miscellaneous: "miscellaneous files",
  PdfDocuments: "exports",
};

export const COLOR = {
  main: {
    hue: 280,
    saturation: 14,
    lightness: 81,
  },
  darker: {
    hue: 280,
    saturation: 13,
    lightness: 22,
  },
  contrastText: {
    hue: 280,
    saturation: 13,
    lightness: 19,
  },
  background: {
    hue: 280,
    saturation: 10,
    lightness: 81,
  },
  backgroundContrastText: {
    hue: 280,
    saturation: 4,
    lightness: 29,
  },
};

export const SELECTED_OR_FOCUS_BLUE = `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`;
export const SELECTED_OR_FOCUS_BORDER = `2px solid ${SELECTED_OR_FOCUS_BLUE}`;
