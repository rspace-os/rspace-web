package com.researchspace.integrations.omero.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.integrations.omero.client.OmeroClient;
import com.researchspace.integrations.omero.model.DataSetRSpaceView;
import com.researchspace.integrations.omero.model.ImageRSpaceView;
import com.researchspace.integrations.omero.model.OmeroRSpaceView;
import com.researchspace.integrations.omero.model.PlateAcquisitionRSpaceView;
import com.researchspace.integrations.omero.model.PlateRSpaceView;
import com.researchspace.integrations.omero.model.WellRSpaceView;
import java.util.List;
import org.junit.jupiter.api.Test;

class OmeroServiceTest {

  private final OmeroClient client = mock(OmeroClient.class);
  private final OmeroService service = new OmeroServiceImpl(client);

  @Test
  void delegatesToClient() {
    List<? extends OmeroRSpaceView> projects = List.of();
    List<ImageRSpaceView> images = List.of();
    List<DataSetRSpaceView> datasets = List.of();
    List<PlateRSpaceView> plates = List.of();
    List<PlateAcquisitionRSpaceView> acquisitions = List.of();
    List<WellRSpaceView> wells = List.of();
    List<String> annotations = List.of();
    ImageRSpaceView image = mock(ImageRSpaceView.class);

    doReturn(projects).when(client).getProjectsAndScreens("credentials", "all");
    when(client.getImages("credentials", 1L, true)).thenReturn(images);
    when(client.getDataSets("credentials", 2L)).thenReturn(datasets);
    when(client.getPlates("credentials", 3L)).thenReturn(plates);
    when(client.getPlateAcquisitions("credentials", 4L)).thenReturn(acquisitions);
    when(client.getWells("credentials", 5L, 6L, false, 7)).thenReturn(wells);
    when(client.getAnnotations("credentials", 8L, "screen")).thenReturn(annotations);
    when(client.getImage("credentials", 9L, 10L, false)).thenReturn(image);

    assertSame(projects, service.getProjectsAndScreens("credentials", "all"));
    assertSame(images, service.getImages("credentials", 1L, true));
    assertSame(datasets, service.getDataSets("credentials", 2L));
    assertSame(plates, service.getPlates("credentials", 3L));
    assertSame(acquisitions, service.getPlateAcquisitions("credentials", 4L));
    assertSame(wells, service.getWells("credentials", 5L, 6L, false, 7));
    assertSame(annotations, service.getAnnotations("credentials", 8L, "screen"));
    assertSame(image, service.getImage("credentials", 9L, 10L, false));
  }
}
