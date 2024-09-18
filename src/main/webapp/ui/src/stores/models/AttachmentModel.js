//@flow

import {
  observable,
  action,
  computed,
  makeObservable,
  runInAction,
} from "mobx";
import { type URL as Url } from "../../util/types";
import { type _LINK } from "../../common/ApiServiceBase";
import { mkAlert } from "../contexts/Alert";
import ApiService from "../../common/InvApiService";
import { type RecordDetails } from "../../stores/definitions/Record";
import { type GlobalId } from "../../stores/definitions/BaseRecord";
import getRootStore from "../stores/RootStore";
import { justFilenameExtension } from "../../util/files";
import { type Person } from "../definitions/Person";
import { type Attachment } from "../definitions/Attachment";

type AttachmentId = ?number;
type Bytes = number;

type CommonAttrs = {|
  id: AttachmentId,
  name: string,
  size: Bytes,
|};

type FromServer = {|
  ...CommonAttrs,
  globalId: GlobalId,
  contentMimeType: string,
  _links: Array<_LINK>,
|};

type FromUpload = {|
  ...CommonAttrs,
  file: File,
|};

export type AttachmentAttrs = FromServer | FromUpload;

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
 * container/sample/subsample/template, having previously been uploaded.
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
  contentMimeType: ?string;
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
      if (!this.contentMimeType) throw new Error("ContentMimeType is missing");
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
    if (typeof this.contentMimeType === "string") {
      return /^image/.test(this.contentMimeType);
    }
    if (this.file) {
      return /^image/.test(this.file.type);
    }
    /*
     * This can't happen because if attachment is loaded from the server
     * then it will have a contentMimeType whereas if it is a new
     * attachment then it will have a file. Included to satisfy flow.
     */
    throw new Error("Should not happen.");
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
      size: this.size,
    };
  }
}

/**
 * This is a new attachment that is to be associated with a particular
 * container/sample/subsample/template, as a result of the user uploading a
 * file.
 */
export class NewlyUploadedAttachment implements Attachment {
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
  contentMimeType: ?string;
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

  async getFile(): Promise<File> {
    if (this.file) return this.file;
    if (!this.link) throw new Error("No file specified");
    const { data } = await ApiService.query<{||}, Blob>(
      this.link,
      new URLSearchParams(),
      true
    );
    return runInAction(() => {
      if (!this.contentMimeType) throw new Error("ContentMimeType is missing");
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
    if (typeof this.contentMimeType === "string") {
      return /^image/.test(this.contentMimeType);
    }
    if (this.file) {
      return /^image/.test(this.file.type);
    }
    /*
     * This can't happen because if attachment is loaded from the server
     * then it will have a contentMimeType whereas if it is a new
     * attachment then it will have a file. Included to satisfy flow.
     */
    throw new Error("Should not happen.");
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
      size: this.size,
    };
  }
}

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
