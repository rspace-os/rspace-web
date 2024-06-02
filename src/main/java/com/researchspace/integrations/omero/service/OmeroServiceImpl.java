package com.researchspace.integrations.omero.service;

import com.researchspace.integrations.omero.client.OmeroClient;
import com.researchspace.integrations.omero.model.DataSetRSpaceView;
import com.researchspace.integrations.omero.model.ImageRSpaceView;
import com.researchspace.integrations.omero.model.OmeroRSpaceView;
import com.researchspace.integrations.omero.model.PlateAcquisitionRSpaceView;
import com.researchspace.integrations.omero.model.PlateRSpaceView;
import com.researchspace.integrations.omero.model.WellRSpaceView;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OmeroServiceImpl implements OmeroService {

  private OmeroClient omeroClient;

  public OmeroServiceImpl(OmeroClient omeroClient) {
    this.omeroClient = omeroClient;
  }

  @Override
  public List<? extends OmeroRSpaceView> getProjectsAndScreens(
      String credentials, String dataType) {
    return omeroClient.getProjectsAndScreens(credentials, dataType);
  }

  @Override
  public List<ImageRSpaceView> getImages(String credentials, long id, boolean fetchLarge) {
    return omeroClient.getImages(credentials, id, fetchLarge);
  }

  @Override
  public List<WellRSpaceView> getWells(
      String cred, long parentid, long id, boolean fetchLarge, int wellIndex) {
    return omeroClient.getWells(cred, parentid, id, fetchLarge, wellIndex);
  }

  @Override
  public List<DataSetRSpaceView> getDataSets(String cred, long projectid) {
    return omeroClient.getDataSets(cred, projectid);
  }

  @Override
  public List<PlateRSpaceView> getPlates(String cred, long screenid) {
    return omeroClient.getPlates(cred, screenid);
  }

  @Override
  public List<String> getAnnotations(String cred, long id, String type) {
    return omeroClient.getAnnotations(cred, id, type);
  }

  @Override
  public ImageRSpaceView getImage(String cred, long imageID, long dataSetID, boolean fetchLarge) {
    return omeroClient.getImage(cred, imageID, dataSetID, fetchLarge);
  }

  @Override
  public List<PlateAcquisitionRSpaceView> getPlateAcquisitions(String credentials, long plateID) {
    return omeroClient.getPlateAcquisitions(credentials, plateID);
  }
}
