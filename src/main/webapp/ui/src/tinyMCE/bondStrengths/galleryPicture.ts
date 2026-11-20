import axios from "@/common/axios";

type UploadedImage = {
  id: number;
  name: string;
  widthResized?: number | null;
  heightResized?: number | null;
  modificationDate?: number | null;
};

type MustacheRenderer = { render: (template: string, data: object) => string };

/** The height a chemical structure is shown at in a document, so the two sit well together. */
const DOCUMENT_PICTURE_HEIGHT = 250;

/**
 * Stores a picture in the Gallery and returns the editor markup for it, the same markup an image
 * uploaded by the user gets. Putting the picture into the text as a data URI makes the field too
 * large for autosave after a few inserts.
 */
export async function galleryPictureHtml(dataUri: string, fieldId: string, fileName: string): Promise<string> {
  const picture = await (await fetch(dataUri)).blob();
  const form = new FormData();
  form.append("xfile", picture, fileName);
  form.append("fieldId", fieldId);
  const upload = await axios.post<{ data?: UploadedImage | null }>("/gallery/ajax/uploadFile/", form);
  const image = upload.data.data;
  const mustache = (globalThis as { Mustache?: MustacheRenderer }).Mustache;
  if (!image || !mustache) throw new Error("The picture could not be stored in the Gallery");

  const storedWidth = image.widthResized ?? 0;
  const storedHeight = image.heightResized ?? 0;
  const height = Math.min(DOCUMENT_PICTURE_HEIGHT, storedHeight);
  const width = storedHeight > 0 ? Math.round((storedWidth * height) / storedHeight) : 0;

  const template = await axios.get<string>("/fieldTemplates/ajax/insertedImageTemplate");
  return mustache.render(template.data, {
    milliseconds: image.modificationDate ?? Date.now(),
    itemId: image.id,
    name: image.name,
    fieldId,
    width,
    height,
    rotation: 0,
  });
}
