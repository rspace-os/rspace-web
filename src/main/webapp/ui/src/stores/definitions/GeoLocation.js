//@flow

/*
 * These values are stringly-encoded numerical values. Longitudes are decimal values from -180 to
 * 180 inclusive. Latitudes are decimal values from -90 to 90 inclusive. Values can be empty string
 * at any time. For more information on the specifics of how the data model is defined, check out
 * this document https://support.datacite.org/docs/datacite-metadata-schema-v44-recommended-and-optional-properties#18-geolocation
 */

export type GeoLocationBox = {
  eastBoundLongitude: string,
  northBoundLatitude: string,
  southBoundLatitude: string,
  westBoundLongitude: string,
};
export type PolygonPoint = {
  pointLatitude: string,
  pointLongitude: string,
};
export type SimplePoint = {
  latitude: string,
  longitude: string,
};

export interface GeoLocationPolygon {
  +length: number;
  get(i: number): ?{| polygonPoint: PolygonPoint |};
  set(i: number, key: $Keys<PolygonPoint>, value: string): void;
  mapPoints<T>(f: (PolygonPoint, number) => T): Array<T>;
  addAnotherPoint(i: number): void;
  removePoint(i: number): void;
  +isValid: boolean;
  +empty: boolean;
  toJson(): mixed;
}

/*
 * This is the shape of the data output by the server to model a geoLocation that has been persisted
 * in the database, as part of an IGSN identifier or independently.
 */
export type GeoLocationAttrs = {|
  geoLocationBox: GeoLocationBox,
  geoLocationPlace: string,
  geoLocationPoint: PolygonPoint,
  geoLocationPolygon: Array<{| polygonPoint: PolygonPoint |}>,
  geoLocationInPolygonPoint: PolygonPoint,
|};

export const newGeoLocation: GeoLocationAttrs = {
  geoLocationBox: {
    eastBoundLongitude: "",
    northBoundLatitude: "",
    southBoundLatitude: "",
    westBoundLongitude: "",
  },
  geoLocationPlace: "",
  geoLocationPoint: {
    pointLatitude: "",
    pointLongitude: "",
  },
  geoLocationPolygon: [
    {
      polygonPoint: {
        pointLatitude: "",
        pointLongitude: "",
      },
    },
    {
      polygonPoint: {
        pointLatitude: "",
        pointLongitude: "",
      },
    },
    {
      polygonPoint: {
        pointLatitude: "",
        pointLongitude: "",
      },
    },
    {
      polygonPoint: {
        pointLatitude: "",
        pointLongitude: "",
      },
    },
  ],
  geoLocationInPolygonPoint: {
    pointLatitude: "",
    pointLongitude: "",
  },
};

/**
 * GeoLocations are used to encode the spatial region or the named place where data was gathered or
 * about which data is focussed. There are four elements -- point, box, place, and polygon -- of
 * which at least one must be defined.
 */
export interface GeoLocation {
  /*
   * A single point, with longtiude and latitude.
   */
  geoLocationPoint: PolygonPoint;

  /*
   * A rectangular region. This region is bounded by two longitudinal lines and two latitudinal
   * lines.
   */
  geoLocationBox: GeoLocationBox;

  /*
   * A named location using free-form text, for example an address.
   */
  geoLocationPlace: string;

  /*
   * An arbitrary region enclosed by a series of points. Given that a polygon alone simply divides
   * the plane into two regions, `geoLocationInPolygonPoint` disambiguates which of the two
   * regions is being described by defining an arbitrary point within the noteworthy region. If
   * `geoLocationInPolygonPoint` is undefined, which is to say that either of its two values is
   * the empty string, then the region being described by the polygon is assumed to the be smaller
   * of the two regions on the plane.
   */
  geoLocationPolygon: GeoLocationPolygon;
  geoLocationInPolygonPoint: PolygonPoint;

  /*
   * A GeoLocation is valid if at least one element is complete, and all three not left incomplete.
   */
  +isValid: boolean;

  /*
   * Computed values used in the rendering of the UI that facilitates the defining of polygons.
   */
  +polygonEmpty: boolean;
  +inPolygonPointIncomplete: boolean;

  toJson(): { ... };
}
