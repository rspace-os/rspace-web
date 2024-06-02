/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import Omero, { getOrderBy, getOrder } from "../../omero/Omero";
import React from "react";
import * as axios from "axios";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import MockAdapter from "axios-mock-adapter";
import ProjectsList from "./json/projectsList.json";
import ScreensList from "./json/screenList.json";
import ProjectsAndScreensList from "./json/projectsAndScreensList.json";
import DataSetsForProject51 from "./json/datasetsForProject51.json";
import ThumbnailsForDS51 from "./json/imageThumbnailsForDS51.json";
import ThumbnailsForPA2661 from "./json/imageThumbnailForPlateAcquisition2661.json";
import ThumbnailsForPlate422 from "./json/imageThumbnailsForPlate422.json";
import ThumbnailForPlate422_179693 from "./json/imageThumbnailFor422_179693";
import Annotations from "./json/annotations.json";
import AnnotationsForImage179693 from "./json/annotationsForImage179693.json";
import PlatesForScreen3 from "./json/platesForScreen3.json";
import PlatesForScreen102 from "./json/platesForScreen102.json";
import AcquisitionsForPlate2551 from "./json/acquisitionsForPlate2551.json";
import AcquisitionsForPlate422 from "./json/acquisitionForPlate422.json";
import { Order } from "../Enums";
const mockAxios = new MockAdapter(axios);
const localStorageMock = {
  getItem: jest.fn(),
  setItem: jest.fn(),
};
const rsMock = {
  trackEvent: jest.fn(),
};
Object.defineProperty(window, "localStorage", { value: localStorageMock });
Object.defineProperty(window, "RS", { value: rsMock });
const getWrapper = (props) => {
  return render(<Omero {...props} />);
};
beforeEach(() => {
  window.HTMLElement.prototype.scrollIntoView = function () {};
  mockAxios
    .onGet("/apps/omero/projects/?dataType=Projects")
    .reply(200, ProjectsList.data);
  mockAxios
    .onGet("/apps/omero/projects/?dataType=Screens")
    .reply(200, ScreensList.data);
  mockAxios
    .onGet("/apps/omero/projects")
    .reply(200, ProjectsAndScreensList.data);
  mockAxios
    .onGet("/apps/omero/annotations/101?type=project")
    .reply(200, Annotations.data);
  mockAxios
    .onGet("/apps/omero/annotations/179693?type=image")
    .reply(200, AnnotationsForImage179693.data);
  mockAxios
    .onGet("/apps/omero/datasets/51")
    .reply(200, DataSetsForProject51.data);
  mockAxios
    .onGet("apps/omero/images/51/?fetchLarge=false")
    .reply(200, ThumbnailsForDS51.data);
  mockAxios.onGet("apps/omero/plates/3").reply(200, PlatesForScreen3.data);
  mockAxios.onGet("apps/omero/plates/102").reply(200, PlatesForScreen102.data);
  mockAxios
    .onGet("apps/omero/plateAcquisitions/2551")
    .reply(200, AcquisitionsForPlate2551.data);
  mockAxios
    .onGet("apps/omero/plateAcquisitions/422")
    .reply(200, AcquisitionsForPlate422.data);
  mockAxios
    .onGet("apps/omero/wells/2551/2661/?fetchLarge=false&wellIndex=0")
    .reply(200, ThumbnailsForPA2661.data);
  mockAxios
    .onGet("apps/omero/wells/422/422/?fetchLarge=false&wellIndex=0")
    .reply(200, ThumbnailsForPlate422.data);
  mockAxios
    .onGet("apps/omero/image/422/179693/?fetchLarge=false")
    .reply(200, ThumbnailForPlate422_179693);
});

describe("Has defaultOrderBy ", () => {
  it("when no value in localStorage then returns Order by path", () => {
    expect(getOrderBy()).toEqual("name");
  });
  it("returns Order By value in localStorage", () => {
    localStorageMock.getItem = jest
      .fn()
      .mockImplementationOnce(() => '"description"');
    expect(getOrderBy()).toEqual("description");
  });
});

describe("Has defaultOrder ", () => {
  it("when no value in localStorage then returns  Order.asc", () => {
    expect(getOrder()).toEqual(Order.asc);
  });
  it("returns Order value in localStorage", () => {
    setUpLocalStorageWithOrder(Order.desc);
    expect(getOrder()).toEqual(Order.desc);
  });
});

const assertElemWithTestIDHasTextContent = (testid, textcontent) => {
  expect(screen.getByTestId(testid)).toBeInTheDocument();
  expect(screen.getByTestId(testid)).toHaveTextContent(textcontent);
};
const assertElemWithTestIDContainsHtml = (testid, html) => {
  expect(screen.getByTestId(testid)).toBeInTheDocument();
  expect(screen.getByTestId(testid)).toContainHTML(html);
};
const setUpProjectsAsData = async () => {
  localStorageMock.getItem = jest.fn().mockImplementation(() => '"Projects"');
  await setUpComponent();
};
const fetchDetailsFor = async (type, typeID) => {
  const fetchDetails = screen.getByTestId(type + "_fetch_details_" + typeID);
  fireEvent.click(fetchDetails);
  await waitFor(
    () =>
      screen.findByTestId(type + "_annotation_" + typeID + "_1", undefined, {
        timeout: 5500,
      }),
    {
      timeout: 5500,
    }
  );
};
const setUpProjectAndScreensInDescendingOrder = async () => {
  setUpLocalStorageWithOrder(Order.desc);
  await setUpComponent();
  await screen.findByText(
    "idr0018-neff-histopathology/experimentA",
    undefined,
    {
      timeout: 5500,
    }
  );
};
const setUpScreensAsData = async () => {
  localStorageMock.getItem = jest.fn().mockImplementation(() => '"Screens"');
  await setUpComponent();
};
const assertThatFirstRowOfDataIsProjectCalled = async (name, numDatasets) => {
  await screen.findByText(name, undefined, {
    timeout: 5500,
  });
  assertElemWithTestIDHasTextContent(
    "path0",
    name +
      "project fetch details see in omero show datasets [" +
      numDatasets +
      "]"
  );
};
const assertThatFirstRowOfDataIsScreenCalled = async (name, numDatasets) => {
  await screen.findByText(name, undefined, {
    timeout: 5500,
  });
  assertElemWithTestIDHasTextContent(
    "path0",
    name + "screen fetch details see in omero show plates [" + numDatasets + "]"
  );
};
const setUpLocalStorageWithOrder = (order) => {
  localStorageMock.getItem = jest.fn().mockImplementation((key) => {
    if (key === "omeroSearchOrder") {
      return '"' + order + '"';
    } else if (key == "omeroSearchOrderBy") {
      return '"name"';
    } else return null;
  });
};
const navigateFromScreenToPlate = async (
  screenName,
  screenID,
  plateID,
  handleSetup = true
) => {
  setUpLocalStorageWithOrder(Order.asc);
  if (handleSetup) {
    await setUpComponent();
  }

  await screen.findByText(screenName, undefined, {
    timeout: 5500,
  });
  const fetchPlates = screen.getByTestId(
    "screen_fetch_childrenLink_" + screenID
  );
  fireEvent.click(fetchPlates);
  await waitFor(
    () =>
      screen.findByTestId("plate_name_display_" + plateID, undefined, {
        timeout: 5500,
      }),
    {
      timeout: 5500,
    }
  );
};
const navigateFromScreenToPlateWIthoutSetup = async (
  screenName,
  screenID,
  plateID
) => {
  await navigateFromScreenToPlate(screenName, screenID, plateID, false);
};
const navigateFromProjectToDataset = async (
  projectName,
  projectID,
  datasetID
) => {
  setUpLocalStorageWithOrder(Order.asc);
  await setUpComponent();
  await screen.findByText(projectName, undefined, {
    timeout: 5500,
  });
  const fetchDatasets = screen.getByTestId(
    "project_fetch_childrenLink_" + projectID
  );
  fireEvent.click(fetchDatasets);
  await waitFor(
    () =>
      screen.findByTestId("dataset_name_display_" + datasetID, undefined, {
        timeout: 5500,
      }),
    {
      timeout: 5500,
    }
  );
};

const hideChildren = async (type, typeID, childType, childID, numchildren) => {
  const fetchChildrenID = type + "_fetch_childrenLink_" + typeID;
  const hideChildrenLink = screen.getByTestId(fetchChildrenID);
  const targetChildID = childType + "_name_display_" + childID;
  expect(screen.getByTestId(targetChildID)).toBeInTheDocument();
  assertElemWithTestIDHasTextContent(
    fetchChildrenID,
    "hide children [" + numchildren + "]"
  );
  fireEvent.click(hideChildrenLink);
  assertElemWithTestIDHasTextContent(
    fetchChildrenID,
    "show " + childType + "s [" + numchildren + "]"
  );
  expect(screen.queryByTestId(targetChildID)).not.toBeInTheDocument();
};

const hideImageGrid = async (type, typeID, imageID) => {
  const targetImageID = "image_img_" + imageID;
  expect(screen.getByTestId(targetImageID)).toBeInTheDocument();
  const hideImageGridLinkID = type + "_hide_grid_" + typeID;
  const showImageGridLinkID = type + "_show_grid_" + typeID;
  expect(screen.queryByTestId(showImageGridLinkID)).not.toBeInTheDocument();
  const hideImageGridLink = screen.getByTestId(hideImageGridLinkID);
  fireEvent.click(hideImageGridLink);
  expect(screen.queryByTestId(targetImageID)).not.toBeInTheDocument();
  expect(screen.getByTestId(showImageGridLinkID)).toBeInTheDocument();
};
const clickChildLinkAndShowPlateAcquisition = async (
  childLInkID,
  plateAcquisitionID
) => {
  const fetchPlatesAcquisitions = screen.getByTestId(
    "plate_fetch_childrenLink_" + childLInkID
  );
  fireEvent.click(fetchPlatesAcquisitions);
  await waitFor(
    () =>
      screen.findByTestId(
        "plateAcquisition_name_display_" + plateAcquisitionID,
        undefined,
        {
          timeout: 5500,
        }
      ),
    {
      timeout: 5500,
    }
  );
};
//for plates that have 1 or 0 plate acquisitions and so can directly open grid of images and plate acquistion at same time
const clickChildLinkAndShowPlateAcquisitionWithImages = async (
  childLInkID,
  plateAcquisitionID,
  imageID
) => {
  await waitFor(
    () =>
      screen.findByTestId(
        "plate_fetch_childrenLink_" + childLInkID,
        undefined,
        {
          timeout: 15500,
        }
      ),
    {
      timeout: 5500,
    }
  );
  const fetchGridOfImages = screen.getByTestId(
    "plate_fetch_childrenLink_" + childLInkID
  );
  fireEvent.click(fetchGridOfImages);
  await waitFor(
    () =>
      screen.findByTestId("image_img_" + imageID, undefined, {
        timeout: 5500,
      }),
    {
      timeout: 5500,
    }
  );
  //check that a plate acquisition is also displayed
  expect(
    screen.getByTestId("plateAcquisition_name_display_" + plateAcquisitionID)
  ).toBeInTheDocument();
};

const clickOnImageWithIDInGridAndAwaitInsertionAsChildOfFullImageData = async (
  imageID
) => {
  const imageInGrid = screen.getByTestId("image_img_" + imageID);
  fireEvent.click(imageInGrid);
  await waitFor(
    () =>
      screen.findByTestId("image_name_display_" + imageID, undefined, {
        timeout: 5500,
      }),
    {
      timeout: 5500,
    }
  );
  await waitFor(
    () =>
      screen.findByTestId("image_annotation_" + imageID + "_0", undefined, {
        timeout: 5500,
      }),
    {
      timeout: 5500,
    }
  );
};
const clickDatasetImageGridLinkAndCheckForImageWithID = async (
  datasetID,
  targetImageID
) => {
  const fetchImages = screen.getByTestId("dataset_show_grid_" + datasetID);
  fireEvent.click(fetchImages);
  await waitFor(
    () =>
      screen.findByTestId("image_img_" + targetImageID, undefined, {
        timeout: 5500,
      }),
    {
      timeout: 5500,
    }
  );
};

const clickImageGridLinkAndCheckForImageWithID = async (
  imageGridLinkID,
  targetImageID
) => {
  const fetchGridOfImages = screen.getByTestId(
    "plateAcquisition_show_grid_" + imageGridLinkID
  );
  fireEvent.click(fetchGridOfImages);
  await waitFor(
    () =>
      screen.findByTestId("image_img_" + targetImageID, undefined, {
        timeout: 5500,
      }),
    {
      timeout: 5500,
    }
  );
};
const setUpComponent = async () => {
  await act(() => {
    getWrapper({ omero_web_url: "http://localhost:8080" });
  });
};
describe("Renders page with results data ", () => {
  it("displays two results table headers", async () => {
    await setUpComponent();
    await screen.findByText("Path", undefined, {
      timeout: 5500,
    });
    await screen.findByText("Description", undefined, {
      timeout: 5500,
    });
  }, 9999);
  it("displays data choice radio", async () => {
    await setUpComponent();
    await screen.findByText("Projects And Screens", undefined, {
      timeout: 5500,
    });
    await screen.findByText("Projects", undefined, {
      timeout: 5500,
    });
    await screen.findByText("Screens", undefined, {
      timeout: 5500,
    });
  }, 9999);
  it("displays Projects And Screens By Default ordered by name", async () => {
    await setUpComponent();
    await screen.findByText(
      "idr0018-neff-histopathology/experimentA",
      undefined,
      {
        timeout: 5500,
      }
    );
    assertElemWithTestIDHasTextContent(
      "path0",
      "idr0001-graml-sysgro/screenAscreen fetch details see in omero show plates [192]"
    );
    assertElemWithTestIDHasTextContent(
      "path24",
      "idr0018-neff-histopathology/experimentAproject fetch details see in omero show datasets [248]"
    );
  }, 9999);
  it("displays Projects Only", async () => {
    await setUpProjectsAsData();
    await screen.findByText(
      "idr0018-neff-histopathology/experimentA",
      undefined,
      {
        timeout: 5500,
      }
    );
    expect(
      screen.queryByText("idr0094-ellinger-sarscov2/screenB")
    ).not.toBeInTheDocument();
  }, 9999);

  it("displays Screens Only", async () => {
    await setUpScreensAsData();
    await screen.findByText("idr0094-ellinger-sarscov2/screenB", undefined, {
      timeout: 5500,
    });
    expect(
      screen.queryByText("idr0018-neff-histopathology/experimentA")
    ).not.toBeInTheDocument();
  }, 9999);

  it("screens can be sorted by name ", async () => {
    await setUpScreensAsData();
    await assertThatFirstRowOfDataIsScreenCalled(
      "idr0001-graml-sysgro/screenA",
      192
    );
    await screen.getByText("Path").click();
    await assertThatFirstRowOfDataIsScreenCalled(
      "idr0145-ho-replicationstress/screenB",
      7
    );
  }, 9999);

  it("screens can be sorted by description ", async () => {
    await setUpScreensAsData();
    await assertThatFirstRowOfDataIsScreenCalled(
      "idr0001-graml-sysgro/screenA",
      192
    );
    await screen.getByText("Description").click();
    await assertThatFirstRowOfDataIsScreenCalled(
      "idr0006-fong-nuclearbodies/screenA",
      169
    );
  }, 9999);
  it("screens description is formatted", async () => {
    await setUpScreensAsData();
    await assertThatFirstRowOfDataIsScreenCalled(
      "idr0001-graml-sysgro/screenA",
      192
    );
    assertElemWithTestIDContainsHtml(
      "description0",
      '<td class="MuiTableCell-root MuiTableCell-body MuiTableCell-sizeMedium css-1d99l7t-MuiTableCell-root-tableCell" data-testid="description0" id="description_tablecell_screen3" width="75%"> <dl><dt id="screen_first_description_3" class="css-b9v4sk-firstDescription"> Publication Title: "A genomic Multiprocess survey of machineries that control and link cell shape, microtubule organization, and cell-cycle progression."</dt><dt id="screen_rest_description_3" class="css-72f9hd-restOfDescription"> Screen Description - \n' +
        "Primary screen of fission yeast knock out mutants looking for genes controlling cell shape, microtubules, and cell-cycle progression. 262 genes controlling specific aspects of those processes are identifed, validated, and functionally annotated.</dt></dl></td>"
    );
  }, 9999);
  it("projects can be sorted by name ", async () => {
    await setUpProjectsAsData();
    await assertThatFirstRowOfDataIsProjectCalled(
      "idr0018-neff-histopathology/experimentA",
      248
    );
    await screen.getByText("Path").click();
    await assertThatFirstRowOfDataIsProjectCalled(
      "idr0148-schumacher-kidneytem/experimentA",
      10
    );
  }, 9999);
  it("projects description is formatted ", async () => {
    await setUpProjectsAsData();
    await assertThatFirstRowOfDataIsProjectCalled(
      "idr0018-neff-histopathology/experimentA",
      248
    );
    assertElemWithTestIDContainsHtml(
      "description1",
      '<td class="MuiTableCell-root MuiTableCell-body MuiTableCell-sizeMedium css-1d99l7t-MuiTableCell-root-tableCell" data-testid="description1" id="description_tablecell_project51" width="75%"> <dl><dt id="project_first_description_51" class="css-b9v4sk-firstDescription"> Publication Title: "Subdiffraction imaging of centrosomes reveals higher-order organizational features of pericentriolar material."</dt><dt id="project_rest_description_51" class="css-72f9hd-restOfDescription"> Experiment Description - \n' +
        "Images relating to Figure 1e in Lawo et al 2012. These are the 3D-SIM reconstructed, maximum intensity projected and aligned images of centriole or PCM proteins of cycling HeLa cells in interphase.</dt></dl></td>"
    );
  }, 9999);
  it("projects can be sorted by description ", async () => {
    await setUpProjectsAsData();
    await assertThatFirstRowOfDataIsProjectCalled(
      "idr0018-neff-histopathology/experimentA",
      248
    );
    await screen.getByText("Description").click();
    await assertThatFirstRowOfDataIsProjectCalled(
      "idr0117-croce-marimba/experimentA",
      9
    );
  }, 9999);

  it("items can have details added ", async () => {
    await setUpProjectAndScreensInDescendingOrder();
    assertElemWithTestIDHasTextContent(
      "description24",
      "Experiment Description - Histopathology raw images and annotated tiff files of tissues from mice with 10 different single gene knockouts."
    );
    await fetchDetailsFor("project", 101);
    assertElemWithTestIDHasTextContent(
      "path24",
      "idr0018-neff-histopathology/experimentAproject details fetched see in omero show datasets [248]"
    );
    assertElemWithTestIDHasTextContent(
      "description24",
      'Experiment Description - Histopathology raw images and annotated tiff files of tissues from mice with 10 different single gene knockouts.Sample Type = cellOrganism = Homo sapiensStudy Type = protein localizationImaging Method = structured illumination microscopy (SIM) Publication Title: "= Subdiffraction imaging of centrosomes reveals higher-order organizational features of pericentriolar material." Publication Authors = Lawo S, Hasegan M, Gupta GD, Pelletier LPubMed ID = 23086237 https://www.ncbi.nlm.nih.gov/pubmed/23086237Publication DOI = 10.1038/ncb2591 https://doi.org/10.1038/ncb2591Release Date = 2016-05-26License = CC BY 4.0 https://creativecommons.org/licenses/by/4.0/Copyright = Lawo et alAnnotation File = idr0021-experimentA-annotation.csv https://github.com/IDR/idr0021-lawo-pericentriolarmaterial/blob/HEAD/experimentA/idr0021-experimentA-annotation.csvFile = "bulk_annotations"'
    );
  }, 9999);

  it("projects can show datasets", async () => {
    await navigateFromProjectToDataset(
      "idr0021-lawo-pericentriolarmaterial/experimentA",
      51,
      51
    );
    assertElemWithTestIDHasTextContent(
      "path28",
      "CDK5RAP2-Cdataset fetch detailsshow image grid [33] see in omero -> parent_project"
    );
  }, 9999);
  it("projects can hide datasets", async () => {
    await navigateFromProjectToDataset(
      "idr0021-lawo-pericentriolarmaterial/experimentA",
      51,
      51
    );
    await hideChildren("project", 51, "dataset", 51, 10);
  }, 9999);
  it("datasets have links to projects ", async () => {
    await navigateFromProjectToDataset(
      "idr0021-lawo-pericentriolarmaterial/experimentA",
      51,
      51
    );
    //check has link to parent project and that the linked ID exists in the doc
    assertElemWithTestIDContainsHtml(
      "dataset_link_parent_51",
      '<a href="#project_name_display_51"> -> parent_project</a>'
    );
    expect(screen.getByTestId("project_name_display_51")).toBeInTheDocument();
  }, 9999);

  it("when projects are reordered datasets are reordered AFTER their parent projects ", async () => {
    await navigateFromProjectToDataset(
      "idr0021-lawo-pericentriolarmaterial/experimentA",
      51,
      51
    );
    assertElemWithTestIDHasTextContent(
      "path27",
      "idr0021-lawo-pericentriolarmaterial/experimentAproject fetch details see in omero hide children [10]"
    );
    assertElemWithTestIDHasTextContent(
      "path28",
      "CDK5RAP2-Cdataset fetch detailsshow image grid [33] see in omero -> parent_project"
    );
    assertElemWithTestIDHasTextContent(
      "path37",
      "TUBG1-Ndataset fetch detailsshow image grid [80] see in omero -> parent_project"
    );
    await screen.getByText("Path").click();
    assertElemWithTestIDHasTextContent(
      "path11",
      "idr0021-lawo-pericentriolarmaterial/experimentAproject fetch details see in omero hide children [10]"
    );
    // the datasets come AFTER their parent AND they are now ordered in the opposite way to before - ie by name DESC
    assertElemWithTestIDHasTextContent(
      "path12",
      "TUBG1-Ndataset fetch detailsshow image grid [80] see in omero -> parent_project"
    );
    assertElemWithTestIDHasTextContent(
      "path21",
      "CDK5RAP2-Cdataset fetch detailsshow image grid [33] see in omero -> parent_project"
    );
  }, 9999);

  it("datasets can show grids of images ", async () => {
    await navigateFromProjectToDataset(
      "idr0021-lawo-pericentriolarmaterial/experimentA",
      51,
      51
    );
    await clickDatasetImageGridLinkAndCheckForImageWithID(51, 1884837);
  }, 9999);

  it("datasets can hide grids of images ", async () => {
    await navigateFromProjectToDataset(
      "idr0021-lawo-pericentriolarmaterial/experimentA",
      51,
      51
    );
    await clickDatasetImageGridLinkAndCheckForImageWithID(51, 1884837);
    await hideImageGrid("dataset", 51, 1884837);
  }, 9999);

  it("screens can show plates, plates can show plate acquisitions", async () => {
    await navigateFromScreenToPlate("idr0001-graml-sysgro/screenA", 3, 2551);
    assertElemWithTestIDHasTextContent(
      "path1",
      "JL_120731_S6Aplate fetch details see in omero show plateAcquisitions [6] -> parent_screen"
    );
    await clickChildLinkAndShowPlateAcquisition(2551, 2661);
    assertElemWithTestIDHasTextContent(
      "path2",
      "Meas_01(2012-07-31_10-41-12)plateAcquisition fetch detailsshow grid of wells for field 1 see in omero -> parent_plate"
    );
  }, 9999);

  it("screens can hide plates", async () => {
    await navigateFromScreenToPlate("idr0001-graml-sysgro/screenA", 3, 2551);
    await hideChildren("screen", 3, "plate", 2551, 192);
  }, 9999);

  it("plates can hide plateacquisitions", async () => {
    await navigateFromScreenToPlate("idr0001-graml-sysgro/screenA", 3, 2551);
    await clickChildLinkAndShowPlateAcquisition(2551, 2661);
    await hideChildren("plate", 2551, "plateAcquisition", 2661, 6);
  }, 9999);

  it("when screen hides plates it also hides plates children", async () => {
    await navigateFromScreenToPlate("idr0001-graml-sysgro/screenA", 3, 2551);
    await clickChildLinkAndShowPlateAcquisition(2551, 2661);
    await hideChildren("screen", 3, "plate", 2551, 192);
    const plateAqcquisitionID = "plateAcquisition_name_display_2661";
    expect(screen.queryByTestId(plateAqcquisitionID)).not.toBeInTheDocument();
  }, 9999);

  it("when screen shows plates it also shows plates children", async () => {
    await navigateFromScreenToPlate("idr0001-graml-sysgro/screenA", 3, 2551);
    await clickChildLinkAndShowPlateAcquisition(2551, 2661);
    await hideChildren("screen", 3, "plate", 2551, 192);
    await navigateFromScreenToPlateWIthoutSetup(
      "idr0001-graml-sysgro/screenA",
      3,
      2551
    );
    const plateAqcquisitionID = "plateAcquisition_name_display_2661";
    expect(screen.getByTestId(plateAqcquisitionID)).toBeInTheDocument();
  }, 9999);

  it("plate acquisitions can show grids of images ", async () => {
    await navigateFromScreenToPlate("idr0001-graml-sysgro/screenA", 3, 2551);
    await clickChildLinkAndShowPlateAcquisition(2551, 2661);
    await clickImageGridLinkAndCheckForImageWithID(2661, 1230029);
  }, 9999);

  it("plate acquisitions can hide grids of images ", async () => {
    await navigateFromScreenToPlate("idr0001-graml-sysgro/screenA", 3, 2551);
    await clickChildLinkAndShowPlateAcquisition(2551, 2661);
    await clickImageGridLinkAndCheckForImageWithID(2661, 1230029);
    await hideImageGrid("plateAcquisition", 2661, 1230029);
  }, 9999);

  it("plates with only one (or none) plate acquisition can directly show grids of images beside the plate acquisition", async () => {
    await navigateFromScreenToPlate(
      "idr0002-heriche-condensation/screenA",
      102,
      422
    );
    await clickChildLinkAndShowPlateAcquisitionWithImages(422, 422, 179693);
  }, 9999);

  it("On click of an image in the grid of images it is inserted into the document as a new child and has annotations with image data", async () => {
    await navigateFromScreenToPlate(
      "idr0002-heriche-condensation/screenA",
      102,
      422
    );
    await clickChildLinkAndShowPlateAcquisitionWithImages(422, 422, 179693);
    await clickOnImageWithIDInGridAndAwaitInsertionAsChildOfFullImageData(
      179693
    );
    assertElemWithTestIDHasTextContent(
      "path3",
      "Run 422plateAcquisition fetch detailshide image grid see in omero hide children [1] -> parent_plate"
    );
    assertElemWithTestIDHasTextContent(
      "path4",
      "plate1_1_013 [Well 1, Field 1 (Spot 1)] details fetched large thumbnailsee in omero -> parent_plateAcquisition"
    );
    assertElemWithTestIDHasTextContent(
      "description4",
      "Z-sections = 1Timepoints = 329Number of Channels = 2Pixels Type = uint16Dimensions(XY) = 1344 x 1024Pixel Size (XYZ) = 0.323 µm x 0.323 µm x n/a Z Channels = [name = Cy3 colour = -16776961 photo interpretation = Monochrome] [name = eGFP colour = 16711935 photo interpretation = Monochrome] Cell Line = HeLaGene Identifier = ENSG00000117399Gene Identifier URL = http://www.ensembl.org/id/ENSG00000117399Gene Symbol = CDC20Analysis Gene Annotation Build = GRCh37, Ensembl release 61, Feb 2011Organism = Homo sapienssiRNA Identifier = s2748siRNA Pool Identifier = Sense Sequence = CGAAAUGACUAUUACCUGATTAntisense Sequence = UCAGGUAAUAGUCAUUUCGGAReagent Design Gene Annotation Build = GRCh37, Ensembl release 61, Feb 2011Control Type = positive controlControl Comments = early mitotic phenotypeQuality Control = failChannels = H2B- mCherry/Cy3:chromatin;eGFP:nuclear lamina and report on nuclear envelope breakdown"
    );
  }, 9999);
});
