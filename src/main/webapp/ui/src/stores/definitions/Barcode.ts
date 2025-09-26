import React from "react";
import { type Id } from "./BaseRecord";
import { type Username } from "./Person";
import { type IsoTimestamp, type _LINK, type URL } from "../../util/types";

/**
 * @module Barcode
 * @description Barcodes are often used in labs to aid with the rapid
 * identification of samples and containers. RSpace supports associating
 * barcodes with Inventory records, printing barcode labels, and searching for
 * items in the systm by scanning its barcode. This module defines the types
 * used in the frontend system to model barcodes.
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
export type FromServer = {
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
export type NewlyCreated = {
  data: string;
  newBarcodeRequest: true;
  description: string;
};

/*
 * This is the data that must be specified when a BarcodeRecord is to be
 * deleted.
 */
export type Deleted = {
  id: Id;
  data: string;
  description: string;
  deleteBarcodeRequest: true;
  imageUrl: URL | null;
};

export type PersistedBarcodeAttrs = FromServer | NewlyCreated | Deleted;
export type BarcodeAttrs = GeneratedBarcodeAttrs | PersistedBarcodeAttrs;

/**
 * This is the base definition of a barcode, which may be either a
 * barcode that has been persisted in the database, or one that has been
 * generated on-the-fly. Barcodes may be any one of many different types,
 * including traditional 1D barcodes like Code 128, or 2D barcodes like QR
 * codes.
 */
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
  deletedCopy(): BarcodeRecord | null;
  readonly isDeleted: boolean;
  readonly isDeletable: boolean;

  setDescription(description: string): void;
  readonly descriptionIsEditable: boolean;
  readonly renderedDescription: React.ReactNode;
}
