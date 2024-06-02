package com.researchspace.integrations.omero.service;

import com.researchspace.integrations.omero.model.DataSetRSpaceView;
import com.researchspace.integrations.omero.model.ImageRSpaceView;
import com.researchspace.integrations.omero.model.OmeroRSpaceView;
import com.researchspace.integrations.omero.model.PlateAcquisitionRSpaceView;
import com.researchspace.integrations.omero.model.PlateRSpaceView;
import com.researchspace.integrations.omero.model.WellRSpaceView;
import java.util.List;

public interface OmeroService {
  List<? extends OmeroRSpaceView> getProjectsAndScreens(String credentials, String dataType);

  List<ImageRSpaceView> getImages(String credentials, long id, boolean fetchLarge);

  List<WellRSpaceView> getWells(
      String cred, long parentid, long id, boolean fetchLarge, int wellIndex);

  List<DataSetRSpaceView> getDataSets(String cred, long projectid);

  List<PlateRSpaceView> getPlates(String cred, long screenid);

  List<String> getAnnotations(String cred, long id, String type);

  ImageRSpaceView getImage(String cred, long imageID, long dataSetID, boolean fetchLarge);

  List<PlateAcquisitionRSpaceView> getPlateAcquisitions(String credentials, long plateID);
}
