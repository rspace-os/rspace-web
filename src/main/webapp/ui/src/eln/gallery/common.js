//@flow

import { COLORS as baseThemeColors } from "../../theme";

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
  | "PdfDocuments"

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
    saturation: 7,
    lightness: 81,
  },
  darker: {
    hue: 280,
    saturation: 8,
    lightness: 22,
  },
  contrastText: {
    hue: 280,
    saturation: 8,
    lightness: 19,
  },
  background: {
    hue: 280,
    saturation: 7,
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
