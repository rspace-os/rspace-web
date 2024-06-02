// @flow
import axios, { type AxiosPromise } from "axios";
import type { OmeroDataList, OmeroDataTypes, OmeroItem } from "./OmeroTypes";

const getOmeroDataList = (
  dataTypeChoice: string
): AxiosPromise<mixed, OmeroDataList> => {
  if (dataTypeChoice === "Projects And Screens") {
    return axios.get("/apps/omero/projects");
  } else {
    return axios.get("/apps/omero/projects/?dataType=" + dataTypeChoice);
  }
};

const getImagesList = (
  id: number,
  fetchLarge: boolean
): AxiosPromise<mixed, OmeroDataList> => {
  return axios.get(
    "/apps/omero/images/" + id + "/?fetchLarge=" + String(fetchLarge)
  );
};

const getDataSetsList = (id: number): AxiosPromise<mixed, OmeroDataList> =>
  axios.get("/apps/omero/datasets/" + id);

const getPlatesList = (id: number): AxiosPromise<mixed, OmeroDataList> =>
  axios.get("/apps/omero/plates/" + id);

const getPlateAcquisitionsList = (
  id: number
): AxiosPromise<mixed, OmeroDataList> =>
  axios.get("/apps/omero/plateAcquisitions/" + id);

const getWellsList = (
  id: number,
  parentid: number,
  fetchLarge: boolean,
  wellIndex: number
): AxiosPromise<mixed, OmeroDataList> => {
  return axios.get(
    "/apps/omero/wells/" +
      parentid +
      "/" +
      id +
      "/?fetchLarge=" +
      String(fetchLarge) +
      "&wellIndex=" +
      wellIndex
  );
};

const getAnnotationsList = (
  id: number,
  type: OmeroDataTypes
): AxiosPromise<mixed, Array<string>> =>
  axios.get("/apps/omero/annotations/" + id + "?type=" + type);

const getFullImage = (
  id: number,
  parentID: number,
  fetchLarge: boolean
): AxiosPromise<mixed, OmeroItem> =>
  axios.get(
    "/apps/omero/image/" +
      parentID +
      "/" +
      id +
      "/?fetchLarge=" +
      String(fetchLarge)
  );

export const getOmeroData = async (
  dataTypeChoice: string
): Promise<OmeroDataList> => {
  const projectsList = (await getOmeroDataList(dataTypeChoice)).data;
  return projectsList;
};

export const getImages = async (
  id: number,
  fetchLarge: boolean
): Promise<OmeroDataList> => {
  const imagesList = (await getImagesList(id, fetchLarge)).data;
  return imagesList;
};
export const getDatasets = async (id: number): Promise<OmeroDataList> => {
  const datasetsList = (await getDataSetsList(id)).data;
  return datasetsList;
};
export const getPlates = async (id: number): Promise<OmeroDataList> => {
  const platesList = (await getPlatesList(id)).data;
  return platesList;
};

export const getPlateAcquisitions = async (
  id: number
): Promise<OmeroDataList> => {
  const plateAcquisitionsList = (await getPlateAcquisitionsList(id)).data;
  return plateAcquisitionsList;
};

export const getAnnotations = async (
  id: number,
  type: OmeroDataTypes
): Promise<Array<string>> => {
  const annotationsList = (await getAnnotationsList(id, type)).data;
  return annotationsList;
};

export const getImage = async (
  id: number,
  parentID: number,
  fetchLarge: boolean
): Promise<OmeroItem> => {
  const fullImage = (await getFullImage(id, parentID, fetchLarge)).data;
  return fullImage;
};

export const getWells = async (
  plateAcquisitionID: number,
  plateID: number,
  fetchLarge: boolean,
  wellIndex: number
): Promise<OmeroDataList> => {
  const imagesList = (
    await getWellsList(plateAcquisitionID, plateID, fetchLarge, wellIndex)
  ).data;
  return imagesList;
};
