package com.researchspace.integrations.omero.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.json.JsonArray;
import javax.json.JsonObject;
import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.util.StringUtils;

@Getter
public class ImageRSpaceView implements OmeroRSpaceView {
  private static final String TYPE = "image";
  private final Long id;

  @SuppressFBWarnings("SS_SHOULD_BE_STATIC")
  private final int childCounts = 0;

  private final String name;
  private final String description;
  private List<String> annotations = new ArrayList<>();
  private String plateAcquisitionName;
  private List<String> displayImageData = new ArrayList<>();
  private String omeroConnectionKey;
  private String base64ThumbnailData;
  private final Long parentId;

  @SneakyThrows
  public ImageRSpaceView(
      JsonObject imageJson, long parentID, String base64ThumbnailData, List<String> annotations) {
    this(imageJson, parentID, base64ThumbnailData, annotations, "");
  }

  @SneakyThrows
  public ImageRSpaceView(
      JsonObject imageJson,
      long parentID,
      String base64ThumbnailData,
      List<String> annotations,
      String plateAcquisitionName) {
    this.name = nullSafeGetString(imageJson, "Name");
    this.description = nullSafeGetString(imageJson, "Description");
    this.id = Long.valueOf(imageJson.getInt("@id"));
    // image parent can be a dataset or it can be a WellSampleData
    this.parentId = parentID;
    this.base64ThumbnailData = base64ThumbnailData;
    this.annotations = annotations;
    this.plateAcquisitionName = plateAcquisitionName;
    if (!StringUtils.isEmpty(description)) {
      displayImageData.add("Description = " + description);
    }
    String accDateStr = nullSafeGetNumberAsString(imageJson, "AcquisitionDate");
    if (StringUtils.hasText(accDateStr)) {
      Date accDate = new Date(Long.parseLong(accDateStr));
      DateFormat df = new SimpleDateFormat("dd:MM:yy:HH:mm:ss");
      displayImageData.add("AcquisitionDate = " + df.format(accDate));
    }
    JsonObject pixels = nullSafeGetObject(imageJson, "Pixels");
    if (pixels != null) {
      displayImageData.add("Z-sections = " + nullSafeGetNumberAsString(pixels, "SizeZ"));
      displayImageData.add("Timepoints = " + nullSafeGetNumberAsString(pixels, "SizeT"));
      displayImageData.add("Number of Channels = " + nullSafeGetNumberAsString(pixels, "SizeC"));
      JsonObject pixelType = nullSafeGetObject(pixels, "Type");
      displayImageData.add(
          "Pixels Type = "
              + (pixelType != null ? nullSafeGetString(pixelType, "value") : "unknown"));
      displayImageData.add(
          "Dimensions(XY) = "
              + nullSafeGetNumberAsString(pixels, "SizeX")
              + " x "
              + nullSafeGetNumberAsString(pixels, "SizeY"));
      JsonObject physicalSizeX = nullSafeGetObject(pixels, "PhysicalSizeX");
      JsonObject physicalSizeY = nullSafeGetObject(pixels, "PhysicalSizeY");
      JsonObject physicalSizeZ = nullSafeGetObject(pixels, "PhysicalSizeZ");
      BigDecimal xFormatD = null;
      if (physicalSizeX != null) {
        xFormatD = new BigDecimal(nullSafeGetNumberAsString(physicalSizeX, "Value"));
        xFormatD = xFormatD.setScale(3, RoundingMode.HALF_UP);
      }
      BigDecimal yFormatD = null;
      if (physicalSizeY != null) {
        yFormatD = new BigDecimal(nullSafeGetNumberAsString(physicalSizeY, "Value"));
        yFormatD = yFormatD.setScale(3, RoundingMode.HALF_UP);
      }
      BigDecimal zFormatD = null;
      if (physicalSizeZ != null) {
        zFormatD = new BigDecimal(nullSafeGetNumberAsString(physicalSizeZ, "Value"));
        zFormatD = zFormatD.setScale(3, RoundingMode.HALF_UP);
      }
      String xFormat =
          physicalSizeX != null
              ? xFormatD + " " + nullSafeGetString(physicalSizeX, "Symbol")
              : " n/a X ";
      String yFormat =
          physicalSizeY != null
              ? yFormatD + " " + nullSafeGetString(physicalSizeY, "Symbol")
              : " n/a Y ";
      String zFormat =
          physicalSizeZ != null
              ? zFormatD + " " + nullSafeGetString(physicalSizeZ, "Symbol")
              : " n/a Z ";
      displayImageData.add("Pixel Size (XYZ) = " + xFormat + " x " + yFormat + " x " + zFormat);
      if (pixels.get("Channels")
          != null) { // channels are always NULL unless we fetch a single image.
        JsonArray channels = pixels.getJsonArray("Channels");
        StringBuilder channelStringBuilder = new StringBuilder();
        for (int chan = 0; chan < channels.size(); chan++) {
          JsonObject channel = channels.getJsonObject(chan);
          String chanColour = nullSafeGetNumberAsString(channel, "Color");
          JsonObject photoInterp = nullSafeGetObject(channel, "omero:photometricInterpretation");
          String photoInterpVal =
              photoInterp != null ? nullSafeGetString(photoInterp, "value") : " unknown ";
          String channelName = nullSafeGetString(channel, "Name");
          String data =
              "[name = "
                  + channelName
                  + " colour = "
                  + chanColour
                  + " photo interpretation = "
                  + photoInterpVal
                  + "] ";
          channelStringBuilder.append(data);
        }
        displayImageData.add("Channels = " + channelStringBuilder.toString());
      }
    }
  }

  /**
   * @param partialImage - image data from a WellSample returned by api call to "url:wells" - list
   *     wells for plate.
   * @param parentID
   * @param base64ThumbnailData
   */
  @SneakyThrows
  public ImageRSpaceView(
      JsonObject partialImage,
      Long parentID,
      String base64ThumbnailData,
      String plateAcquisitionName) {
    this.name = nullSafeGetString(partialImage, "Name");
    this.description = nullSafeGetString(partialImage, "Description");
    this.id = Long.valueOf(partialImage.getInt("@id"));
    // image parent can be a dataset or it can be a WellSampleData
    this.parentId = parentID;
    this.plateAcquisitionName = plateAcquisitionName;
    this.base64ThumbnailData = base64ThumbnailData;
  }

  @Override
  public void setOmeroConnectionKey(String omeroConnectionKey) {
    this.omeroConnectionKey = omeroConnectionKey;
  }

  @Override
  public List<OmeroRSpaceView> getChildren() {
    return Collections.EMPTY_LIST;
  }

  @Override
  public String getType() {
    return TYPE;
  }
}
