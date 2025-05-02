import {
  observable,
  action,
  computed,
  makeObservable,
  runInAction,
} from "mobx";
import { type URL as Url, type _LINK } from "../../util/types";
import { mkAlert } from "../contexts/Alert";
import ApiService from "../../common/InvApiService";
import { type RecordDetails } from "../../stores/definitions/Record";
import getRootStore from "../stores/RootStore";
import { justFilenameExtension } from "../../util/files";
import { type Person } from "../definitions/Person";
import { type Attachment } from "../definitions/Attachment";
import { type GlobalId, type Id } from "../definitions/BaseRecord";
import { type GalleryFile } from "../../eln/gallery/useGalleryListing";
import { type LinkableRecord } from "../definitions/LinkableRecord";

type AttachmentId = ?number;
type Bytes = number;

type CommonAttrs = {|
  id: AttachmentId,
  name: string,
  size: Bytes,
|};

/**
 * This is the shape of the JSON object that the server will respond with to
 * model an attachment that was created by the user uploading a file from their
 * device.
 */
type FromServer = {|
  ...CommonAttrs,
  globalId: GlobalId,
  contentMimeType: string,
  _links: Array<_LINK>,
|};

/**
 * This is the shape of the JSON object that the server will respond with to
 * model an attachment that was created by the user choosing a file that is
 * already in the Gallery
 */
type FromServerFromGallery = {|
  ...FromServer,
  mediaFileGlobalId: string,
|};

/**
 * This is the shape of the JSON object that the server will respond with to
 * model an attachment. It is exported so that other model classes can describe
 * the shape of the JSON they expect to get from the server where attachments
 * are just one component part.
 */
export type AttachmentJson = FromServer | FromServerFromGallery;

/**
 * This is the shape of the object that the code below expects to receive from
 * the react code when a user has selecting a file on their device for use as a
 * new attachment.
 */
type FromUpload = {|
  ...CommonAttrs,
  file: File,
|};

/**
 * This is the shape of the object that the code below expects to receive from
 * the react code when a user chooses a file in the Gallery for use as a new
 * attachment.
 */
type FromGallery = {|
  ...CommonAttrs,
  galleryId: string,
  downloadHref: null | (() => Promise<Url>),
|};

/**
 * Attachments that were created from Gallery files refer back those files via
 * the mediaFileGlobalId property. This class facilitates rendering that Global
 * Id by providing all of the information required to render a Global Id link
 * including icon, permalink, and tooltip label.
 */
class LinkableGalleryFile implements LinkableRecord {
  globalId: ?string;

  /*
   * Note that the name and id are not required as part of rendering the Global
   * Id are simply required for the LinkableRecord interface to be useful in
   * other circumstances. As such, any random value will suffice for these properties.
   */
  id: ?number;
  name: string;

  constructor({
    id,
    globalId,
    name,
  }: {|
    id: number,
    globalId: string,
    name: string,
  |}) {
    this.id = id;
    this.globalId = globalId;
    this.name = name;
  }

  get recordTypeLabel(): string {
    return "Gallery File";
  }

  get iconName(): string {
    return "gallery";
  }

  get permalinkURL(): string {
    if (!this.globalId) throw new Error("Impossible");
    return `/globalId/${this.globalId}`;
  }
}

const chemExtensions = new Set([
  "cdx",
  "cml",
  "csmol",
  "cxsmarts",
  "mol",
  "mol2",
  "mrv",
  "pdb",
  "rdf",
  "rxn",
  "sdf",
  "smarts",
  "smiles",
]);

/**
 * This is an attachment that is already associated with a particular
 * container/sample/subsample/template/field, having previously been uploaded.
 */
export class ExistingAttachment implements Attachment {
  id: AttachmentId;
  globalId: ?GlobalId;
  name: string;
  size: Bytes;
  link: ?Url;
  file: ?File;
  imageLink: ?Url;
  chemicalString: string = "";
  removed: boolean;
  loadingImage: boolean = false;
  loadingString: boolean = false;
  contentMimeType: string;
  onRemoveCallback: (Attachment) => void;
  permalinkURL: ?Url;
  owner: ?Person; // For the info button

  constructor(
    attrs: FromServer,
    permalinkURL: ?Url,
    onRemoveCallback: (Attachment) => void
  ) {
    makeObservable(this, {
      name: observable,
      size: observable,
      link: observable,
      file: observable,
      imageLink: observable,
      chemicalString: observable,
      removed: observable,
      loadingImage: observable,
      loadingString: observable,
      permalinkURL: observable,
      cardTypeLabel: computed,
      deleted: computed,
      recordTypeLabel: computed,
      iconName: computed,
      isImageFile: computed,
      isChemicalFile: computed,
      previewSupported: computed,
      chemistrySupported: computed,
      recordDetails: computed,
      setImageLink: action,
      setChemicalString: action,
    });
    this.id = attrs.id;
    this.name = attrs.name;
    this.owner = null;
    this.size = attrs.size;
    this.globalId = attrs.globalId;
    this.contentMimeType = attrs.contentMimeType;
    this.link = attrs._links.find(({ rel }) => rel === "enclosure")?.link;
    this.removed = false;
    this.onRemoveCallback = onRemoveCallback;
    this.permalinkURL = permalinkURL;
  }

  async getFile(): Promise<File> {
    if (this.file) return this.file;
    if (!this.link) throw new Error("No file specified");
    const { data } = await ApiService.query<{||}, Blob>(
      this.link,
      new URLSearchParams(),
      true
    );
    return runInAction(() => {
      const file = new File([data], this.name, { type: this.contentMimeType });
      this.file = file;
      return file;
    });
  }

  setLoadingImage(value: boolean): void {
    this.loadingImage = value;
  }

  setLoadingString(value: boolean): void {
    this.loadingString = value;
  }

  async setImageLink(): Promise<void> {
    const file = await this.getFile();
    if (!file) throw new Error("File is not yet known.");
    let imageFile;
    if (this.isChemicalFile) imageFile = await this.fetchChemicalImage();
    runInAction(() => {
      this.imageLink = URL.createObjectURL(imageFile || file);
    });
  }

  async createAuthenticatedLink(): Promise<Url> {
    const file = await this.getFile();
    if (!file) throw new Error("File is not yet known.");
    return URL.createObjectURL(file);
  }

  revokeAuthenticatedLink(link: Url): void {
    if (this.imageLink) {
      this.imageLink = null;
    }
    return URL.revokeObjectURL(link);
  }

  setChemicalString(chemicalString: string): void {
    this.chemicalString = chemicalString;
  }

  async createChemicalPreview(): Promise<void> {
    await this.fetchChemicalString();
  }

  revokeChemicalPreview(): void {
    this.setChemicalString("");
  }

  remove() {
    this.removed = true;
    this.onRemoveCallback(this);
    getRootStore().trackingStore.trackEvent("RemovedAttachment");
  }

  async download() {
    const anchor = document.createElement("a");
    anchor.href = await this.createAuthenticatedLink();
    anchor.download = this.name;
    if (document.body) document.body.appendChild(anchor);
    anchor.click();
    getRootStore().trackingStore.trackEvent("DownloadAttachment");
    this.revokeAuthenticatedLink(anchor.download);
    if (document.body) document.body.removeChild(anchor);
  }

  fetchChemicalImage(): Promise<Blob> {
    if (!this.id) {
      throw new Error("Cannot get chemical image without file id");
    } else {
      const id = this.id;
      this.setLoadingImage(true);
      return ApiService.query<
        {| imageParams: { width: ?number, height: ?number } |},
        Blob
      >(
        `/files/${id}/file/image`,
        new URLSearchParams({
          imageParams: JSON.stringify({
            width: 800,
            height: null, // optional (h equals w if missing)
            // scale: 1.0, // optional
          }),
        }),
        true
      )
        .then((r) => {
          return r.data;
        })
        .finally(() => {
          this.setLoadingImage(false);
        });
    }
  }

  fetchChemicalString(): Promise<void> {
    const id = this.id;
    if (!id) {
      throw new Error("Cannot generate chemical preview without file id");
    } else {
      this.setLoadingString(true);
      const response = ApiService.get<
        void,
        {| data: { chemElements: string } |}
      >(`/files/${id}/chemFileDetails`);
      return response
        .then((r) => {
          if (r.status === 200) {
            const chemicalString = r.data.data.chemElements;
            this.setChemicalString(chemicalString);
          }
        })
        .catch((error) => {
          getRootStore().uiStore.addAlert(
            mkAlert({
              title: "Fetching chemical file failed.",
              message:
                error?.response?.data.message ??
                error.message ??
                "Unknown reason.",
              variant: "error",
            })
          );
          console.error(
            `Could not get a string representation of chemical file ${id}.`,
            error
          );
        })
        .finally(() => {
          this.setLoadingString(false);
        });
    }
  }

  get cardTypeLabel(): string {
    return "Attachment";
  }

  get deleted(): boolean {
    return this.removed;
  }

  get recordTypeLabel(): string {
    return this.cardTypeLabel;
  }

  get iconName(): string {
    return "attachment";
  }

  get isImageFile(): boolean {
    return /^image/.test(this.contentMimeType);
  }

  get isChemicalFile(): boolean {
    const fileExt = justFilenameExtension(this.name);
    return chemExtensions.has(fileExt);
  }

  get hasId(): boolean {
    return Boolean(this.id);
  }

  get chemistrySupported(): boolean {
    return this.isChemicalFile && this.hasId;
  }

  get previewSupported(): boolean {
    return this.isImageFile || this.chemistrySupported;
  }

  get recordDetails(): RecordDetails {
    return {
      hideGlobalId: true,
      size: this.size,
    };
  }

  async save(_parentGlobalId: GlobalId): Promise<void> {
    if (typeof this.id === "number" && this.removed) {
      await ApiService.delete<mixed, mixed>("files", this.id);
      return;
    }
    return Promise.resolve();
  }
}

/**
 * This is an attachment that is already associated with a particular
 * container/sample/subsample/template/field, having previously been created
 * from a gallery file.
 */
export class ExistingAttachmentFromGallery extends ExistingAttachment {
  mediaFileGlobalId: string;

  constructor(
    attrs: FromServerFromGallery,
    permalinkURL: ?Url,
    onRemoveCallback: (Attachment) => void
  ) {
    const { mediaFileGlobalId, ...rest } = attrs;
    super(rest, permalinkURL, onRemoveCallback);
    makeObservable(this, {
      mediaFileGlobalId: observable,
    });
    this.mediaFileGlobalId = mediaFileGlobalId;
    this.permalinkURL = `/globalId/${mediaFileGlobalId}`;
  }

  get recordDetails(): RecordDetails {
    return {
      hideGlobalId: true,
      ...super.recordDetails,
      galleryFile: new LinkableGalleryFile({
        id: 0,
        globalId: this.mediaFileGlobalId,
        name: "foo",
      }),
    };
  }
}

/**
 * Initialise attachment model classes after they have been received from API
 * calls.
 */
export function newExistingAttachment(
  attrs: AttachmentJson,
  permalinkURL: ?Url,
  onRemoveCallback: (Attachment) => void
): ExistingAttachment {
  const { mediaFileGlobalId, ...rest } = attrs;
  if (typeof mediaFileGlobalId === "string") {
    return new ExistingAttachmentFromGallery(
      { ...rest, mediaFileGlobalId },
      permalinkURL,
      onRemoveCallback
    );
  }
  return new ExistingAttachment(rest, permalinkURL, onRemoveCallback);
}

/**
 * This is a new attachment that is to be associated with a particular
 * container/sample/subsample/template/field, as a result of the user uploading
 * a file.
 */
export class NewlyUploadedAttachment implements Attachment {
  id: AttachmentId;
  globalId: ?GlobalId;
  name: string;
  size: Bytes;
  link: ?Url;
  file: ?File; // will never actually be null because the user just uploaded it
  imageLink: ?Url;
  chemicalString: string = "";
  removed: boolean;
  loadingImage: boolean = false;
  loadingString: boolean = false;
  onRemoveCallback: (Attachment) => void;
  permalinkURL: ?Url;
  owner: ?Person; // For the info button

  constructor(
    attrs: FromUpload,
    permalinkURL: ?Url,
    onRemoveCallback: (Attachment) => void
  ) {
    makeObservable(this, {
      name: observable,
      size: observable,
      link: observable,
      file: observable,
      imageLink: observable,
      chemicalString: observable,
      removed: observable,
      loadingImage: observable,
      loadingString: observable,
      permalinkURL: observable,
      cardTypeLabel: computed,
      deleted: computed,
      recordTypeLabel: computed,
      iconName: computed,
      isImageFile: computed,
      isChemicalFile: computed,
      hasId: computed,
      previewSupported: computed,
      chemistrySupported: computed,
      recordDetails: computed,
      fetchChemicalImage: action,
      fetchChemicalString: action,
      setLoadingImage: action,
      setLoadingString: action,
      setImageLink: action,
      setChemicalString: action,
    });
    this.id = attrs.id;
    this.name = attrs.name;
    this.owner = null;
    this.size = attrs.size;
    this.file = attrs.file;
    this.globalId = null;
    this.removed = false;
    this.onRemoveCallback = onRemoveCallback;
    this.permalinkURL = permalinkURL;
  }

  getFile(): Promise<File> {
    if (this.file) return Promise.resolve(this.file);
    throw new Error(
      "Impossible because the user already selected a file to upload"
    );
  }

  setLoadingImage(value: boolean): void {
    this.loadingImage = value;
  }

  setLoadingString(value: boolean): void {
    this.loadingString = value;
  }

  async setImageLink(): Promise<void> {
    const file = this.file;
    if (!file)
      throw new Error(
        "Impossible because the user already selected a file to upload"
      );
    let imageFile;
    if (this.isChemicalFile) imageFile = await this.fetchChemicalImage();
    runInAction(() => {
      this.imageLink = URL.createObjectURL(imageFile || file);
    });
  }

  async createAuthenticatedLink(): Promise<Url> {
    const file = await this.getFile();
    if (!file) throw new Error("File is not yet known.");
    return URL.createObjectURL(file);
  }

  revokeAuthenticatedLink(link: Url): void {
    if (this.imageLink) {
      this.imageLink = null;
    }
    return URL.revokeObjectURL(link);
  }

  setChemicalString(chemicalString: string): void {
    this.chemicalString = chemicalString;
  }

  async createChemicalPreview(): Promise<void> {
    await this.fetchChemicalString();
  }

  revokeChemicalPreview(): void {
    this.setChemicalString("");
  }

  remove() {
    this.removed = true;
    this.onRemoveCallback(this);
    getRootStore().trackingStore.trackEvent("RemovedAttachment");
  }

  async download() {
    const anchor = document.createElement("a");
    anchor.href = await this.createAuthenticatedLink();
    anchor.download = this.name;
    if (document.body) document.body.appendChild(anchor);
    anchor.click();
    getRootStore().trackingStore.trackEvent("DownloadAttachment");
    this.revokeAuthenticatedLink(anchor.download);
    if (document.body) document.body.removeChild(anchor);
  }

  fetchChemicalImage(): Promise<Blob> {
    if (!this.id) {
      throw new Error("Cannot get chemical image without file id");
    } else {
      const id = this.id;
      this.setLoadingImage(true);
      return ApiService.query<
        {| imageParams: { width: ?number, height: ?number } |},
        Blob
      >(
        `/files/${id}/file/image`,
        new URLSearchParams({
          imageParams: JSON.stringify({
            width: 800,
            height: null, // optional (h equals w if missing)
            // scale: 1.0, // optional
          }),
        }),
        true
      )
        .then((r) => {
          return r.data;
        })
        .finally(() => {
          this.setLoadingImage(false);
        });
    }
  }

  fetchChemicalString(): Promise<void> {
    const id = this.id;
    if (!id) {
      throw new Error("Cannot generate chemical preview without file id");
    } else {
      this.setLoadingString(true);
      const response = ApiService.get<
        void,
        {| data: { chemElements: string } |}
      >(`/files/${id}/chemFileDetails`);
      return response
        .then((r) => {
          if (r.status === 200) {
            const chemicalString = r.data.data.chemElements;
            this.setChemicalString(chemicalString);
          }
        })
        .catch((error) => {
          getRootStore().uiStore.addAlert(
            mkAlert({
              title: "Fetching chemical file failed.",
              message:
                error?.response?.data.message ??
                error.message ??
                "Unknown reason.",
              variant: "error",
            })
          );
          console.error(
            `Could not get a string representation of chemical file ${id}.`,
            error
          );
        })
        .finally(() => {
          this.setLoadingString(false);
        });
    }
  }

  get cardTypeLabel(): string {
    return "Attachment";
  }

  get deleted(): boolean {
    return this.removed;
  }

  get recordTypeLabel(): string {
    return this.cardTypeLabel;
  }

  get iconName(): string {
    return "attachment";
  }

  get isImageFile(): boolean {
    if (this.file) return /^image/.test(this.file.type);
    throw new Error(
      "Impossible because the user already selected a file to upload"
    );
  }

  get isChemicalFile(): boolean {
    const fileExt = justFilenameExtension(this.name);
    return chemExtensions.has(fileExt);
  }

  get hasId(): boolean {
    return Boolean(this.id);
  }

  get chemistrySupported(): boolean {
    return this.isChemicalFile && this.hasId;
  }

  get previewSupported(): boolean {
    return this.isImageFile;
  }

  get recordDetails(): RecordDetails {
    return {
      hideGlobalId: true,
      size: this.size,
    };
  }

  async save(parentGlobalId: GlobalId): Promise<void> {
    const toFormData = () => {
      const fd = new FormData();
      if (!this.file)
        throw new Error(
          "Impossible because the user already selected a file to upload"
        );
      fd.append("file", this.file);
      fd.append(
        "fileSettings",
        JSON.stringify({
          fileName: this.name,
          parentGlobalId,
        })
      );
      return fd;
    };

    if (!this.removed) {
      const formData = await toFormData();
      await ApiService.post<FormData, mixed>("files", formData);
      return;
    }
    return Promise.resolve();
  }
}

/**
 * Initialise a NewUploadedAttachment object from an uploaded File
 */
export const newAttachment = (
  file: File,
  permalinkURL: ?Url,
  onRemoveCallback: (Attachment) => void
): NewlyUploadedAttachment => {
  return new NewlyUploadedAttachment(
    {
      id: null,
      name: file.name,
      size: file.size,
      file,
    },
    permalinkURL,
    onRemoveCallback
  );
};

/**
 * This is a new attachment that is to be associated with a particular
 * container/sample/subsample/template/field, as a result of the user choosing
 * a Gallery file.
 */
export class NewGalleryAttachment implements Attachment {
  galleryId: string;
  name: string;
  size: Bytes;
  removed: boolean = false;
  onRemoveCallback: (Attachment) => void;
  permalinkURL: ?Url;
  downloadHref: null | (() => Promise<Url>);

  /*
   * Dummy values to satisfy Attachment interface
   */
  globalId: ?GlobalId = null;
  id: Id = null;
  imageLink: ?Url = null;
  owner: ?Person = null;
  loadingImage: boolean = false;
  loadingString: boolean = false;
  chemicalString: string = "";

  constructor(attrs: FromGallery, onRemoveCallback: (Attachment) => void) {
    makeObservable(this, {
      galleryId: observable,
      name: observable,
      size: observable,
      imageLink: observable,
      chemicalString: observable,
      removed: observable,
      loadingImage: observable,
      loadingString: observable,
      permalinkURL: observable,
      cardTypeLabel: computed,
      deleted: computed,
      recordTypeLabel: computed,
      iconName: computed,
      isChemicalFile: computed,
      previewSupported: computed,
      chemistrySupported: computed,
      recordDetails: computed,
    });
    this.name = attrs.name;
    this.size = attrs.size;
    this.galleryId = attrs.galleryId;
    this.removed = false;
    this.onRemoveCallback = onRemoveCallback;
    this.permalinkURL = `/globalId/${attrs.galleryId}`;
  }

  getFile(): Promise<File> {
    return Promise.reject(new Error("Not implemented"));
  }

  remove() {
    this.removed = true;
    this.onRemoveCallback(this);
    getRootStore().trackingStore.trackEvent("RemovedAttachment");
  }

  async download(): Promise<void> {
    const anchor = document.createElement("a");
    if (!this.downloadHref)
      throw new Error("There isn't a URL to download the file from");
    anchor.href = await this.downloadHref();
    anchor.download = this.name;
    if (document.body) document.body.appendChild(anchor);
    anchor.click();
    getRootStore().trackingStore.trackEvent("DownloadAttachment");
    if (document.body) document.body.removeChild(anchor);
    return Promise.resolve();
  }

  async createChemicalPreview(): Promise<void> {
    return Promise.reject(
      new Error("Gallery files do not support chemical preview")
    );
  }

  revokeChemicalPreview(): void {}

  setImageLink(): Promise<void> {
    return Promise.reject(
      new Error("Gallery files do not yet support preview image")
    );
  }

  revokeAuthenticatedLink() {}

  get isChemicalFile(): boolean {
    return false;
  }

  get chemistrySupported(): boolean {
    return false;
  }

  get previewSupported(): boolean {
    return false;
  }

  get cardTypeLabel(): string {
    return "Attachment";
  }

  get deleted(): boolean {
    return this.removed;
  }

  get recordTypeLabel(): string {
    return this.cardTypeLabel;
  }

  get iconName(): string {
    return "attachment";
  }

  get recordDetails(): RecordDetails {
    return {
      hideGlobalId: true,
      size: this.size,
      galleryFile: new LinkableGalleryFile({
        id: 0,
        globalId: this.galleryId,
        name: "foo",
      }),
    };
  }

  async save(parentGlobalId: GlobalId): Promise<void> {
    if (this.removed) return Promise.resolve();
    await ApiService.post<
      {| parentGlobalId: GlobalId, mediaFileGlobalId: string |},
      mixed
    >("attachments", {
      parentGlobalId,
      mediaFileGlobalId: this.galleryId,
    });
  }
}

/**
 * Initialise a NewGalleryAttachment object from a selected Gallery file.
 */
export const newGalleryAttachment = (
  file: GalleryFile,
  onRemoveCallback: (Attachment) => void
): NewGalleryAttachment => {
  if (!file.globalId)
    throw new Error("Cannot attach a file that does not have a Global Id");
  return new NewGalleryAttachment(
    {
      id: null,
      name: file.name,
      size: file.size,
      galleryId: file.globalId,
      downloadHref: file.downloadHref ?? null,
    },
    onRemoveCallback
  );
};
