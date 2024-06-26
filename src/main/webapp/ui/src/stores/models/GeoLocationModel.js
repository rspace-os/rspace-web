//@flow

import { observable, computed, makeObservable } from "mobx";
import {
  type GeoLocationAttrs,
  type GeoLocation,
  type GeoLocationBox,
  type GeoLocationPolygon,
  type PolygonPoint,
} from "../definitions/GeoLocation";

/**
 * GeoLocation validation.
 * "error" state and helperText to be displayed when:
 * some values for a GL element are given (incomplete), and that value empty.
 * incomplete status is not empty, and empty may be acceptable (for Place)
 */

const pointEmpty = (point: PolygonPoint): boolean => {
  return Object.values(point).every((v) => v === "");
};
export const pointComplete = (point: PolygonPoint): boolean => {
  return Object.values(point).every((v) => v !== "");
};
const pointIncomplete = (point: PolygonPoint): boolean => {
  return (
    Object.values(point).some((v) => v !== "") &&
    !Object.values(point).every((v) => v !== "")
  );
};

export const boxComplete = (box: GeoLocationBox): boolean => {
  return Object.values(box).every((v) => v !== "");
};
export const polygonComplete = (polygon: GeoLocationPolygon): boolean => {
  return polygon.every((el) => pointComplete(el.polygonPoint));
};

export default class GeoLocationModel implements GeoLocation {
  geoLocationBox: GeoLocationBox;
  geoLocationPlace: string;
  geoLocationPoint: PolygonPoint;
  geoLocationPolygon: GeoLocationPolygon;
  geoLocationInPolygonPoint: PolygonPoint;
  constructor(attrs: GeoLocationAttrs) {
    makeObservable(this, {
      geoLocationBox: observable,
      geoLocationPlace: observable,
      geoLocationPoint: observable,
      geoLocationPolygon: observable,
      geoLocationInPolygonPoint: observable,
      placeComplete: computed,
      pointIncomplete: computed,
      boxIncomplete: computed,
      polygonEmpty: computed,
      polygonIncomplete: computed,
      inPolygonPointIncomplete: computed,
      isValid: computed,
    });
    this.geoLocationPoint = attrs.geoLocationPoint;
    this.geoLocationPlace = attrs.geoLocationPlace;
    this.geoLocationBox = attrs.geoLocationBox;

    /*
     * Polygons are a series of points, where the first and last point are the same.
     * Rather than have all code which mutates these points synchronise the two ends,
     * we have them point to the same object in memory. The validation is just to
     * ensure we're not erasing data before doing so.
     */
    const lastIndex = attrs.geoLocationPolygon.length - 1;
    if (
      attrs.geoLocationPolygon[0].polygonPoint.pointLatitude ===
        attrs.geoLocationPolygon[lastIndex].polygonPoint.pointLatitude &&
      attrs.geoLocationPolygon[0].polygonPoint.pointLongitude ===
        attrs.geoLocationPolygon[lastIndex].polygonPoint.pointLongitude
    ) {
      this.geoLocationPolygon = attrs.geoLocationPolygon.map(
        ({ polygonPoint }) => ({ polygonPoint: observable(polygonPoint) })
      );
      this.geoLocationPolygon[lastIndex] = this.geoLocationPolygon[0];
    } else {
      throw new Error(
        "Polygon data is invalid: the first and last points are not the same."
      );
    }

    this.geoLocationInPolygonPoint = attrs.geoLocationInPolygonPoint;
  }

  get placeComplete(): boolean {
    return this.geoLocationPlace !== "";
  }

  get pointIncomplete(): boolean {
    return pointIncomplete(this.geoLocationPoint);
  }

  get inPolygonPointIncomplete(): boolean {
    return pointIncomplete(this.geoLocationInPolygonPoint);
  }

  get boxIncomplete(): boolean {
    return (
      Object.values(this.geoLocationBox).some((v) => v !== "") &&
      !Object.values(this.geoLocationBox).every((v) => v !== "")
    );
  }

  get polygonEmpty(): boolean {
    return this.geoLocationPolygon.every((el) => pointEmpty(el.polygonPoint));
  }

  get polygonIncomplete(): boolean {
    return !polygonComplete(this.geoLocationPolygon) && !this.polygonEmpty;
  }

  get isValid(): boolean {
    return (
      (pointComplete(this.geoLocationPoint) ||
        this.placeComplete ||
        boxComplete(this.geoLocationBox) ||
        polygonComplete(this.geoLocationPolygon)) &&
      !this.pointIncomplete &&
      !this.boxIncomplete &&
      !this.polygonIncomplete
    );
  }
}
