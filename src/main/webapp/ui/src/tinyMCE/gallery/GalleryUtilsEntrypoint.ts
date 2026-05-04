import {
  addFromGallery,
  getFieldIdFromTextFieldId,
} from "@/tinyMCE/gallery/utils";

type AddFromGalleryHandler = typeof addFromGallery;
type GetFieldIdFromTextFieldIdHandler = typeof getFieldIdFromTextFieldId;

declare global {
  interface Window {
    addFromGallery: AddFromGalleryHandler;
    getFieldIdFromTextFieldId: GetFieldIdFromTextFieldIdHandler;
  }
}

window.addFromGallery = addFromGallery;
window.getFieldIdFromTextFieldId = getFieldIdFromTextFieldId;

