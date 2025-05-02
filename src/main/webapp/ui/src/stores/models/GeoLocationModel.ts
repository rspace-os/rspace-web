/*
 * ====  A POINT ABOUT THE IMPORTS  ===========================================
 *
 *  This class is used, amongst other places, on the IdentifierPublicPage[1]
 *  where the user may not be authenticated. As such, this module, and any
 *  module that is imported, MUST NOT import anything from the global Inventory
 *  stores (i.e. from ../stores/*). If it does, then the page will be rendered
 *  as a blank screen and there will be an unhelpful error message on the
 *  browser's console saying that webpack export could not be initialised.
 *
 *  [1]: ../../components/PublicPage/IdentifierPublicPage.js
 *
 * ============================================================================
 */
import { observable, computed, action, makeObservable } from "mobx";
import {
  type GeoLocationAttrs,
  type GeoLocation,
  type GeoLocationBox,
  type GeoLocationPolygon,
  type PolygonPoint,
} from "../definitions/GeoLocation";

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

export class GeoLocationPolygonModel implements GeoLocationPolygon {
  readonly points: Array<{ polygonPoint: PolygonPoint }>;

  constructor(points: Array<{ polygonPoint: PolygonPoint }>) {
    makeObservable(this, {
      points: observable,
      length: computed,
      set: action,
      addAnotherPoint: action,
      removePoint: action,
      isValid: computed,
      empty: computed,
    });
    this.points = points;
  }

  get length(): number {
    return this.points.length;
  }

  get(i: number): ?{ polygonPoint: PolygonPoint } {
    return this.points[i];
  }

  set(i: number, key: keyof PolygonPoint, value: string): void {
    this.points[i].polygonPoint[key] = value;
    if (i === 0) this.points[this.length - 1].polygonPoint[key] = value;
  }

  mapPoints<T>(f: (point: PolygonPoint, index: number) => T): Array<T> {
    return this.points.map(({ polygonPoint }, i) => f(polygonPoint, i));
  }

  addAnotherPoint(i: number): void {
    this.points.splice(i + 1, 0, {
      polygonPoint: { pointLatitude: "", pointLongitude: "" },
    });
  }

  removePoint(i: number): void {
    this.points.splice(i, 1);
  }

  get isValid(): boolean {
    return this.points.every(({ polygonPoint }) => {
      if (polygonPoint.pointLatitude === "") return false;
      if (isNaN(parseFloat(polygonPoint.pointLatitude))) return false;
      if (polygonPoint.pointLongitude === "") return false;
      if (isNaN(parseFloat(polygonPoint.pointLongitude))) return false;
      return true;
    });
  }

  get empty(): boolean {
    return this.points.every(({ polygonPoint }) => {
      if (polygonPoint.pointLatitude !== "") return false;
      if (polygonPoint.pointLongitude !== "") return false;
      return true;
    });
  }

  toJson(): unknown {
    return this.points;
  }
}

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
     * Polygons are a series of points, where the first and last point are the
     * same; GeoLocationPolygonModel ensures that this invariant is maintained.
     * The validation is just to ensure we're not erasing data before doing so,
     * because mutations of the first or last points inside the
     * GeoLocationPolygonModel will over write the other.
     */
    const lastIndex = attrs.geoLocationPolygon.length - 1;
    if (
      attrs.geoLocationPolygon[0].polygonPoint.pointLatitude ===
        attrs.geoLocationPolygon[lastIndex].polygonPoint.pointLatitude &&
      attrs.geoLocationPolygon[0].polygonPoint.pointLongitude ===
        attrs.geoLocationPolygon[lastIndex].polygonPoint.pointLongitude
    ) {
      this.geoLocationPolygon = new GeoLocationPolygonModel(
        attrs.geoLocationPolygon.map(({ polygonPoint }) => ({
          polygonPoint: observable(polygonPoint),
        }))
      );
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
    return this.geoLocationPolygon.empty;
  }

  get polygonIncomplete(): boolean {
    return !this.geoLocationPolygon.isValid && !this.polygonEmpty;
  }

  get isValid(): boolean {
    return (
      (pointComplete(this.geoLocationPoint) ||
        this.placeComplete ||
        boxComplete(this.geoLocationBox) ||
        this.geoLocationPolygon.isValid) &&
      !this.pointIncomplete &&
      !this.boxIncomplete &&
      !this.polygonIncomplete
    );
  }

  toJson(): object {
    return {
      geoLocationBox: this.geoLocationBox,
      geoLocationPlace: this.geoLocationPlace,
      geoLocationPoint: this.geoLocationPoint,
      geoLocationPolygon: this.geoLocationPolygon.toJson(),
      geoLocationInPolygonPoint: this.geoLocationInPolygonPoint,
    };
  }
}
