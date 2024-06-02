package com.researchspace.api.v1.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiInventoryDOIGeoLocation {

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiInventoryDOIGeoLocationPoint {
    private String pointLatitude;
    private String pointLongitude;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiInventoryDOIGeoLocationPolygonPoint {
    private ApiInventoryDOIGeoLocationPoint polygonPoint;

    public ApiInventoryDOIGeoLocationPolygonPoint(String pointLatitude, String pointLongitude) {
      polygonPoint = new ApiInventoryDOIGeoLocationPoint(pointLatitude, pointLongitude);
    }
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiInventoryDOIGeoLocationBox {
    private String westBoundLongitude;
    private String eastBoundLongitude;
    private String southBoundLatitude;
    private String northBoundLatitude;
  }

  private String geoLocationPlace;
  private ApiInventoryDOIGeoLocationPoint geoLocationPoint;
  private ApiInventoryDOIGeoLocationBox geoLocationBox;
  private List<ApiInventoryDOIGeoLocationPolygonPoint> geoLocationPolygon;
  private ApiInventoryDOIGeoLocationPoint geoLocationInPolygonPoint;

  public ApiInventoryDOIGeoLocation(String place) {
    this.geoLocationPlace = place;
  }
}
