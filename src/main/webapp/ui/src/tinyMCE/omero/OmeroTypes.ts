import React from "react";

type OmeroItemBase = {
  path: Array<React.ReactElement<"dt">>;
  firstDescription?: string;
  imageData?: Array<React.ReactElement>;
  dataDescriptionArray?: Array<string>;
  displayType?: React.ReactElement<"img">;
  fetchLarge: boolean;
  fetched?: boolean;
  fetchNew?: boolean;
  addedChildren: Array<OmeroItem>;
  currentclicked?: boolean;
  imageGridDetails: Array<Array<Array<React.ReactElement<"img">>>>; //an array of image grids, each image grid corresponds to a 'field' in a plate acquisitoon
  hiddenImageGridDetails?: Array<Array<Array<React.ReactElement<"img">>>>;
  gridBeingShown: number;
  annotations: Array<React.ReactElement>;
  base64ThumbnailData?: string;
  childCounts: number;
  children: Array<OmeroItem>;
  description: string;
  descriptionElems?: Array<React.ReactElement>;
  deselectedByHiding?: boolean;
  displayImageData: Array<string>;
  gridShown?: boolean;
  hide?: boolean;
  hideAsIndirectDescendant?: boolean;
  id: number;
  name: string;
  paths: Array<string>;
  omeroConnectionKey: string;
  parentId: number;
  plateAcquisitionName?: string;
  samplesUrls?: Array<string>;
  selected?: boolean;
  showingChildren?: boolean;
  imageSrc?: { style: { cssText: string } };
  gridOfImages: Array<OmeroItem>;
  parentName?: string;
  parentType?: string;
  fake: boolean;
};

export type OmeroItem =
  | PlateAcquisition
  | Project
  | Screen
  | Dataset
  | Plate
  | Image
  | WellSample
  | Well;

export type PlateAcquisition = OmeroItemBase & {
  rows: number;
  columns: number;
  type: "plateAcquisition";
};

export type Project = OmeroItemBase & {
  type: "project";
};

export type Screen = OmeroItemBase & {
  type: "screen";
};

export type Dataset = OmeroItemBase & {
  type: "dataset";
};

export type Plate = OmeroItemBase & {
  type: "plate";
};

export type Image = OmeroItemBase & {
  type: "image";
};

export type Well = OmeroItemBase & {
  row: number;
  column: number;
  type: "well";
};

export type WellSample = OmeroItemBase & {
  type: "well sample";
};

export type OmeroDataList = Array<OmeroItem>;

export type OmeroDataTypes =
  | "project"
  | "screen"
  | "dataset"
  | "plate"
  | "plateAcquisition"
  | "well"
  | "well sample"
  | "image";

export type OmeroArgs = {
  omero_web_url: string;
};

export const $PropertyExists = <T extends NonNullable<unknown>>(
  val: T
): NonNullable<T> => val;
