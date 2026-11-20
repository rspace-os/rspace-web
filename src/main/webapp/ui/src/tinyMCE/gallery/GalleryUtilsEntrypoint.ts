import { addFromGallery, getFieldIdFromTextFieldId, getGenericDocTemplate } from "@/tinyMCE/gallery/utils";

type AddFromGalleryHandler = typeof addFromGallery;
type GetFieldIdFromTextFieldIdHandler = typeof getFieldIdFromTextFieldId;
type GetGenericDocTemplateHandler = typeof getGenericDocTemplate;

declare global {
  interface Window {
    addFromGallery: AddFromGalleryHandler;
    getFieldIdFromTextFieldId: GetFieldIdFromTextFieldIdHandler;
    getGenericDocTemplate: GetGenericDocTemplateHandler;
  }
}

window.addFromGallery = addFromGallery;
window.getFieldIdFromTextFieldId = getFieldIdFromTextFieldId;
window.getGenericDocTemplate = getGenericDocTemplate;
