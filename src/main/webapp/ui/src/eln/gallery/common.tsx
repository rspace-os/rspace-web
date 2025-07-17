import React from "react";
import { COLORS as baseThemeColors } from "../../theme";
import Result from "../../util/result";
import * as Parsers from "../../util/parsers";
import ChemistryIcon from "./chemistryIcon";
import FilestoreIcon from "./filestoreIcon";
import { FontAwesomeIcon as FaIcon } from "@fortawesome/react-fontawesome";
import { library } from "@fortawesome/fontawesome-svg-core";
import {
  faImage,
  faFilm,
  faFile,
  faFileInvoice,
  faDatabase,
  faShapes,
  faCircleDown,
  faVolumeLow,
  faScissors,
} from "@fortawesome/free-solid-svg-icons";
library.add(faImage);
library.add(faFilm);
library.add(faFile);
library.add(faFileInvoice);
library.add(faDatabase);
library.add(faShapes);
library.add(faCircleDown);
library.add(faVolumeLow);
library.add(faScissors);

/**
 * Constants for the strings that identify gallery sections.
 */
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
} as const;

/**
 * The Gallery is divided up into a several sections based on the type of the
 * files contained within. Most of these sections provide a file-type specific
 * view onto files stored locally by the RSpace instance. The exception to this
 * is NetworkFiles, which is the section that allows for viewing files stored
 * on external filesystems.
 */
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

/**
 * Given a string, parse it into a gallery section. If the string does not
 * correspond to any of the valid gallery sections, return an error.
 */
export const parseGallerySection = (section: string): Result<GallerySection> =>
  Result.first(
    Parsers.parseString(GALLERY_SECTION.IMAGES, section),
    Parsers.parseString(GALLERY_SECTION.AUDIOS, section),
    Parsers.parseString(GALLERY_SECTION.VIDEOS, section),
    Parsers.parseString(GALLERY_SECTION.DOCUMENTS, section),
    Parsers.parseString(GALLERY_SECTION.CHEMISTRY, section),
    Parsers.parseString(GALLERY_SECTION.DMPS, section),
    Parsers.parseString(GALLERY_SECTION.NETWORKFILES, section),
    Parsers.parseString(GALLERY_SECTION.SNIPPETS, section),
    Parsers.parseString(GALLERY_SECTION.MISCELLANEOUS, section),
    Parsers.parseString(GALLERY_SECTION.PDFDOCUMENTS, section),
  );

/**
 * Given a URLSearchParams, get the gallery section as referred to by the
 * 'mediaType' search parameter. Most of this logic is to satify flow that the
 * string is indeed one of the valid strings that identify a gallery section.
 */
export const parseGallerySectionFromUrlSearchParams = (
  searchParams: URLSearchParams,
): Result<GallerySection> =>
  Result.fromNullable(
    searchParams.get("mediaType"),
    new Error("No search parameter with name 'mediaType'"),
  ).flatMap(parseGallerySection);

/**
 * Mapping of gallery sections to a label that can be shown in the UI.
 */
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

/**
 * Mapping of gallery sections to icons that can be shown in the UI.
 */
export const gallerySectionIcon: Record<string, React.ReactNode> = {
  Images: <FaIcon icon="image" />,
  Audios: <FaIcon icon="volume-low" />,
  Videos: <FaIcon icon="film" />,
  Documents: <FaIcon icon="file" />,
  Chemistry: <ChemistryIcon />,
  DMPs: <FaIcon icon="file-invoice" />,
  NetworkFiles: <FilestoreIcon />,
  Snippets: <FaIcon icon="scissors" />,
  Miscellaneous: <FaIcon icon="shapes" />,
  PdfDocuments: <FaIcon icon="circle-down" />,
};

/**
 * Mapping of gallery sections to pieces of text that can be used to refer to
 * multiple files of the type typically found in the particular gallery
 * section.
 */
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

/**
 * The blue colour used when an element is selected or focussed. Wherever
 * possible, prefer to use the `callToAction` palette colour instead.
 */
export const SELECTED_OR_FOCUS_BLUE = `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`;
/**
 * The blue border used when an element is selected or focussed. Wherever
 * possible, prefer to use the `callToAction` palette colour instead.
 */
export const SELECTED_OR_FOCUS_BORDER = `2px solid ${SELECTED_OR_FOCUS_BLUE}`;
