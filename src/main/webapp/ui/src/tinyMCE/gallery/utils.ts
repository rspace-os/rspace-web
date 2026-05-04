import type { WorkspaceRecordInformation } from "@/modules/workspace/schema";

type TinyMceEditor = {
  id: string;
};

type RSGalleryUtils = {
  tinymceInsertInternalLink: (
    id: number,
    globalId: string,
    name: string,
    editor: TinyMceEditor,
  ) => void;
  insertTemplateIntoTinyMCE: (templateName: string, data: unknown) => void;
  blockPage: (message: string) => void;
  unblockPage: () => void;
  tinymceInsertContent: (html: string) => void;
};

declare function generateIconSrc(
  type: string,
  extension?: string | null,
  thumbnailId?: number | null,
  id?: number,
): string;
declare function isPlayableOnJWPlayer(extension?: string | null): boolean;
declare function setUpJWMediaPlayer(
  id: number,
  name: string,
  extension: string,
): string;
declare function insertChemElement(
  id: number,
  fieldId: string,
  fileName: string,
): Promise<unknown>;

declare global {
  interface RSGlobal {
    tinymceInsertInternalLink?: RSGalleryUtils["tinymceInsertInternalLink"];
    insertTemplateIntoTinyMCE?: RSGalleryUtils["insertTemplateIntoTinyMCE"];
    blockPage?: RSGalleryUtils["blockPage"];
    unblockPage?: RSGalleryUtils["unblockPage"];
    tinymceInsertContent?: RSGalleryUtils["tinymceInsertContent"];
  }

  interface Window {
    chemistryAvailable?: boolean;
  }
}

type RequiredRSGalleryUtils = RSGlobal & {
  tinymceInsertInternalLink: NonNullable<RSGlobal["tinymceInsertInternalLink"]>;
  insertTemplateIntoTinyMCE: NonNullable<RSGlobal["insertTemplateIntoTinyMCE"]>;
  blockPage: NonNullable<RSGlobal["blockPage"]>;
  unblockPage: NonNullable<RSGlobal["unblockPage"]>;
  tinymceInsertContent: NonNullable<RSGlobal["tinymceInsertContent"]>;
};

const MEDIA_TYPE_IMAGES = "Images";
const MEDIA_TYPE_VIDEOS = "Videos";
const MEDIA_TYPE_AUDIOS = "Audios";
const MEDIA_TYPE_DOCS = "Documents";
const MEDIA_TYPE_CHEMISTRY = "Chemistry";
const MEDIA_TYPE_MISCDOCS = "Miscellaneous";
const MEDIA_TYPE_EXPORTED = "PdfDocuments";
const MEDIA_TYPE_SNIPPETS = "Snippets";
const MEDIA_TYPE_FILESTORES = "NetworkFiles";
const MEDIA_TYPE_DMPS = "DMPs";
const MEDIA_TYPE_FOLDER = "Folder";
const GENERIC_DOCUMENT_MEDIA_TYPES = new Set([
  MEDIA_TYPE_DOCS,
  MEDIA_TYPE_EXPORTED,
  MEDIA_TYPE_MISCDOCS,
  MEDIA_TYPE_DMPS,
]);

const typeMapping: Record<string, string> = {
  Image: MEDIA_TYPE_IMAGES,
  Video: MEDIA_TYPE_VIDEOS,
  Audio: MEDIA_TYPE_AUDIOS,
  Document: MEDIA_TYPE_DOCS,
  Chemistry: MEDIA_TYPE_CHEMISTRY,
  Miscellaneous: MEDIA_TYPE_MISCDOCS,
  PdfDocuments: MEDIA_TYPE_EXPORTED,
  Snippet: MEDIA_TYPE_SNIPPETS,
  NetworkFile: MEDIA_TYPE_FILESTORES,
  DMP: MEDIA_TYPE_DMPS,
  DMPs: MEDIA_TYPE_DMPS,
  Folder: MEDIA_TYPE_FOLDER,
  "System Folder": MEDIA_TYPE_FOLDER,
};

// @TODO pass this down from React
let chemistryAvailable = false;


export function getFieldIdFromTextFieldId(textFieldId: string) {
  return textFieldId.slice("rtf_".length);
}

function getActiveEditor() {
  const activeEditor = window.tinymce.activeEditor;

  if (activeEditor === null) {
    throw new Error("No active TinyMCE editor is available");
  }

  return activeEditor as TinyMceEditor;
}

function getActiveFieldId() {
  return getFieldIdFromTextFieldId(getActiveEditor().id);
}

function getRS() {
  return window.RS as RequiredRSGalleryUtils;
}

function isChemistryAvailable() {
  return window.chemistryAvailable ?? chemistryAvailable;
}

/*
 * Top level method to handle the insertion of a link to a Gallery item
 * into the document editor.
 * @param data - the record information for an item (returned by getRecordInformation)
 */
export function addFromGallery(data: WorkspaceRecordInformation) {
  const mediaType = typeMapping[data.type];

  if (!mediaType) {
    return;
  }

  switch (mediaType) {
    case MEDIA_TYPE_FOLDER:
      insertFolder(data);
      return;
    case MEDIA_TYPE_IMAGES:
      insertImagesFromGallery(data);
      return;
    case MEDIA_TYPE_VIDEOS:
    case MEDIA_TYPE_AUDIOS:
      insertAVFromGallery(mediaType, data);
      return;
    case MEDIA_TYPE_CHEMISTRY:
      if (isChemistryAvailable()) {
        void insertChemistryFileFromGallery(data);
        return;
      }
      insertGenericDoc(mediaType, data);
      return;
    case MEDIA_TYPE_SNIPPETS:
      void insertSnippetFromGallery(data);
      return;
    case MEDIA_TYPE_FILESTORES:
      throw new Error("Legacy filestore link insertion detected, please remove");
    default:
      if (GENERIC_DOCUMENT_MEDIA_TYPES.has(mediaType)) {
        insertGenericDoc(mediaType, data);
      }
  }
}

/*
 * Import folders from gallery to tinymce, irrelevent to their gallery type
 */
function insertFolder(data: WorkspaceRecordInformation) {
  getRS().tinymceInsertInternalLink(
    data.id,
    data.oid.idString,
    data.name,
    getActiveEditor(),
  );
}

/*
 * Import image(s) from gallery to tinymce
 * @param data - the record information for an item(returned by getRecordInformation)
 */
function insertImagesFromGallery(data: WorkspaceRecordInformation) {
  const json = {
    milliseconds: data.modificationDate ?? Date.now(),
    itemId: data.id,
    name: data.name,
    fieldId: getActiveFieldId(),
    width: data.widthResized ?? 0,
    height: data.heightResized ?? 0,
    rotation: 0,
  };

  getRS().insertTemplateIntoTinyMCE("#insertedImageTemplate", json);
}

/*
 * @param mediaType - either MEDIA_TYPE_AUDIOS or MEDIA_TYPE_VIDEOS
 * @param data - the record information for an item(returned by getRecordInformation)
 */
function insertAVFromGallery(
  mediaType: string,
  data: WorkspaceRecordInformation,
) {
  const fieldId = getActiveFieldId();
  const extension = data.extension ?? "";
  const json = {
    compositeId: `${fieldId}-${data.id}`,
    imgClass:
      mediaType === MEDIA_TYPE_VIDEOS ? "videoDropped" : "audioDropped",
    videoHTML: isPlayableOnJWPlayer(extension)
      ? setUpJWMediaPlayer(data.id, data.name, extension)
      : "",
    iconSrc: generateIconSrc(mediaType, extension, data.thumbnailId, data.id),
    filename: data.name,
    extension,
    id: data.id,
  };

  getRS().insertTemplateIntoTinyMCE("#avTableForTinymceTemplate", json);
}

/*
 * @param mediaType - either MEDIA_TYPE_DOCS, MEDIA_TYPE_EXPORTED, or MEDIA_TYPE_MISCDOCS
 * @param data - the record information for an item(returned by getRecordInformation)
 */
function insertGenericDoc(
  mediaType: string,
  data: WorkspaceRecordInformation,
) {
  const templateId =
    mediaType === MEDIA_TYPE_DOCS
      ? "#insertedDocumentTemplate"
      : "#insertedMiscdocTemplate";
  const extension = data.extension ?? "";
  const json = {
    id: data.id,
    name: data.name,
    iconPath: generateIconSrc(
      mediaType,
      extension,
      data.thumbnailId,
      data.id,
    ),
  };
  getRS().insertTemplateIntoTinyMCE(templateId, json);
}

async function insertChemistryFileFromGallery(data: WorkspaceRecordInformation) {
  getRS().blockPage("Inserting Chemical...");

  try {
    // defined in coreEditor.js
    await insertChemElement(data.id, getActiveFieldId(), data.name);
  } catch (error) {
    console.error("Error while inserting chemicals from Gallery:", error);
  } finally {
    getRS().unblockPage();
  }
}

async function insertSnippetFromGallery(data: WorkspaceRecordInformation) {
  const body = new URLSearchParams({
    snippetId: String(data.id),
    fieldId: getActiveFieldId(),
  });

  try {
    const response = await fetch("/snippet/insertIntoField", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "X-Requested-With": "XMLHttpRequest",
      },
      body: body.toString(),
    });

    if (!response.ok) {
      throw new Error(`Snippet insertion failed with status ${response.status}`);
    }

    const result = await response.text();
    if (result.length > 0) {
      getRS().tinymceInsertContent(result);
    }
  } catch {
    alert(`An error occurred while inserting the snippet ${data.id}`);
  }
}
