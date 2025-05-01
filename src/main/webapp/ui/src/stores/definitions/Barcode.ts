import React from "react";
import { type Id } from "./BaseRecord";
import { type Username } from "./Person";
import { type IsoTimestamp, type _LINK, type URL } from "../../util/types";

/*
 * The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
 * "SHOULD", "SHOULD NOT", "RECOMMENDED",  "MAY", and "OPTIONAL" in this
 * document are to be interpreted as described in RFC 2119.
 */

/**
 * We generate some barcodes for labs that don't have an existing barcoding
 * system. These barcodes are not stored in the database but instead are
 * generated on-the-fly.
 */
export type GeneratedBarcodeAttrs = {
  data: string;
};

/*
 * This is the shape of the data outputted by the server to model a barcode
 * that has been persisted in the database.
 */
type FromServer = {
  id: Id;
  created: IsoTimestamp;
  createdBy: Username;
  data: string;
  description: string;
  _links: Array<_LINK>;
};

/*
 * This is the data that must be specified when a new BarcodeRecord is created.
 */
type NewlyCreated = {
  data: string;
  newBarcodeRequest: true;
  description: string;
};

/*
 * This is the data that must be specified when a BarcodeRecord is to be
 * deleted.
 */
type Deleted = {
  id: Id;
  data: string;
  description: string;
  deleteBarcodeRequest: true;
  imageUrl: URL | null;
};

export type PersistedBarcodeAttrs = FromServer | NewlyCreated | Deleted;
export type BarcodeAttrs = GeneratedBarcodeAttrs | PersistedBarcodeAttrs;

export interface BarcodeRecord {
  data: string;
  description: string;

  imageUrl: URL | null;
  /*
   * fetchImage MUST reject if imageUrl is null.
   */
  fetchImage(): Promise<File>;

  /*
   * The data to be sent to the API to persist the barcode. This method MUST
   * throw if generated is true.
   */
  readonly paramsForBackend: object;

  /*
   * If isDeletable is true then `deletedCopy` MUST return a copy of this
   * BarcodeRecord, but with the metadata indicating that it has been deleted,
   * such that sending the output of `paramsForBackend` on the new object to the
   * server will result in the barcode being deleted. `isDeleted` MUST also be set
   * to true on the new object. If `isDeletable` is false then `deletedCopy` MUST
   * throw an error. Null MUST be returned if the barcode record can be
   * discarded without communicating with the server.
   */
  deletedCopy(): string | null;
  readonly isDeleted: boolean;
  readonly isDeletable: boolean;

  setDescription(description: string): void;
  readonly descriptionIsEditable: boolean;
  readonly renderedDescription: React.ReactNode;
}
