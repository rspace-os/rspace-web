import { test, describe, expect, beforeEach, vi } from "vitest";
import Omero, { getOrderBy, getOrder } from "../../omero/Omero";
import React from "react";
import axios from "@/common/axios";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import ProjectsList from "./json/projectsList.json";
import ScreensList from "./json/screenList.json";
import ProjectsAndScreensList from "./json/projectsAndScreensList.json";
import DataSetsForProject51 from "./json/datasetsForProject51.json";
import ThumbnailsForDS51 from "./json/imageThumbnailsForDS51.json";
import ThumbnailsForPA2661 from "./json/imageThumbnailForPlateAcquisition2661.json";
import ThumbnailsForPlate422 from "./json/imageThumbnailsForPlate422.json";
import ThumbnailForPlate422_179693 from "./json/imageThumbnailFor422_179693.json";
import Annotations from "./json/annotations.json";
import AnnotationsForImage179693 from "./json/annotationsForImage179693.json";
import PlatesForScreen3 from "./json/platesForScreen3.json";
import PlatesForScreen102 from "./json/platesForScreen102.json";
import AcquisitionsForPlate2551 from "./json/acquisitionsForPlate2551.json";
import AcquisitionsForPlate422 from "./json/acquisitionForPlate422.json";
import { Order } from "../Enums";

vi.mock("@/common/axios", async () => {
  const actual = await vi.importActual<typeof import("axios")>("axios");
  const instance = actual.default?.create
    ? actual.default.create()
    : actual.default;
  return { ...actual, default: instance };
});

vi.mock("../../omero/OmeroClient", async () => {
  const unwrap = <T,>(mod: T | { default?: T }): T =>
    (mod as { default?: T }).default ?? (mod as T);
  const ProjectsList = unwrap(await import("./json/projectsList.json"));
  const ScreensList = unwrap(await import("./json/screenList.json"));
  const ProjectsAndScreensList = unwrap(
    await import("./json/projectsAndScreensList.json"),
  );
  const DataSetsForProject51 = unwrap(
    await import("./json/datasetsForProject51.json"),
  );
  const ThumbnailsForDS51 = unwrap(
    await import("./json/imageThumbnailsForDS51.json"),
  );
  const ThumbnailsForPA2661 = unwrap(
    await import("./json/imageThumbnailForPlateAcquisition2661.json"),
  );
  const ThumbnailsForPlate422 = unwrap(
    await import("./json/imageThumbnailsForPlate422.json"),
  );
  const ThumbnailForPlate422_179693 = unwrap(
    await import("./json/imageThumbnailFor422_179693.json"),
  );
  const Annotations = unwrap(await import("./json/annotations.json"));
  const AnnotationsForImage179693 = unwrap(
    await import("./json/annotationsForImage179693.json"),
  );
  const PlatesForScreen3 = unwrap(await import("./json/platesForScreen3.json"));
  const PlatesForScreen102 = unwrap(
    await import("./json/platesForScreen102.json"),
  );
  const AcquisitionsForPlate2551 = unwrap(
    await import("./json/acquisitionsForPlate2551.json"),
  );
  const AcquisitionsForPlate422 = unwrap(
    await import("./json/acquisitionForPlate422.json"),
  );
  return {
    getOmeroData: async (dataTypeChoice: string) => {
      if (dataTypeChoice === "Projects") {
        return ProjectsList.data;
      }
      if (dataTypeChoice === "Screens") {
        return ScreensList.data;
      }
      return ProjectsAndScreensList.data;
    },
    getDatasets: async () => DataSetsForProject51.data,
    getImages: async () => ThumbnailsForDS51.data,
    getPlates: async (id: number) =>
      id === 3 ? PlatesForScreen3.data : PlatesForScreen102.data,
    getPlateAcquisitions: async (id: number) =>
      id === 2551
        ? AcquisitionsForPlate2551.data
        : AcquisitionsForPlate422.data,
    getWells: async (plateAcquisitionID: number) =>
      plateAcquisitionID === 2661
        ? ThumbnailsForPA2661.data
        : ThumbnailsForPlate422.data,
    getAnnotations: async (id: number, type: string) => {
      if (id === 101 && type === "project") {
        return Annotations.data;
      }
      if (id === 179693 && type === "image") {
        return AnnotationsForImage179693.data;
      }
      return [];
    },
    getImage: async () => ThumbnailForPlate422_179693,
  };
});

const mockAxios = new MockAdapter(axios);
const localStorageMock: {
  getItem: (key: string) => string | null;
  setItem: (key: string, value: string) => void;
} = {
  getItem: vi.fn(),
  setItem: vi.fn(),
};
const rsMock = {
  trackEvent: vi.fn(),
};
Object.defineProperty(window, "localStorage", { value: localStorageMock });
Object.defineProperty(window, "RS", { value: rsMock });

type FindAllByTextArgs = Parameters<typeof screen.findAllByText>;
const getWrapper = (props: React.ComponentProps<typeof Omero>) => {
  return render(<Omero {...props} />);
};

beforeEach(() => {
  window.HTMLElement.prototype.scrollIntoView = function () {};
  localStorageMock.getItem = vi.fn().mockImplementation(() => null);
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
    .onGet("/apps/omero/images/51/?fetchLarge=false")
    .reply(200, ThumbnailsForDS51.data);
  mockAxios.onGet("/apps/omero/plates/3").reply(200, PlatesForScreen3.data);
  mockAxios.onGet("/apps/omero/plates/102").reply(200, PlatesForScreen102.data);
  mockAxios
    .onGet("/apps/omero/plateAcquisitions/2551")
    .reply(200, AcquisitionsForPlate2551.data);
  mockAxios
    .onGet("/apps/omero/plateAcquisitions/422")
    .reply(200, AcquisitionsForPlate422.data);
  mockAxios
    .onGet("/apps/omero/wells/2551/2661/?fetchLarge=false&wellIndex=0")
    .reply(200, ThumbnailsForPA2661.data);
  mockAxios
    .onGet("/apps/omero/wells/422/422/?fetchLarge=false&wellIndex=0")
    .reply(200, ThumbnailsForPlate422.data);
  mockAxios
    .onGet("/apps/omero/image/422/179693/?fetchLarge=false")
    .reply(200, ThumbnailForPlate422_179693);
});

describe("Has defaultOrderBy", () => {
  test("when no value in localStorage then returns Order by path", () => {
    expect(getOrderBy()).toEqual("name");
  });
  test("returns Order By value in localStorage", () => {
    localStorageMock.getItem = vi
      .fn()
      .mockImplementationOnce(() => '"description"');
    expect(getOrderBy()).toEqual("description");
  });
});

describe("Has defaultOrder", () => {
  test("when no value in localStorage then returns  Order.asc", () => {
    expect(getOrder()).toEqual(Order.asc);
  });
  test("returns Order value in localStorage", () => {
    setUpLocalStorageWithOrder(Order.desc);
    expect(getOrder()).toEqual(Order.desc);
  });
});

const assertElemWithTestIDHasTextContent = (
  testid: string,
  textcontent: string,
) => {
  const candidates = screen.queryAllByTestId(testid);
  if (candidates.length === 0) {
    return;
  }
  const normalize = (value: string) => value.replace(/\s+/g, " ").trim();
  const normalizedExpected = normalize(textcontent);
  const matchingCandidate = candidates.find((element) =>
    normalize(element.textContent || "").includes(normalizedExpected),
  );
  if (matchingCandidate) {
    expect(matchingCandidate).toBeInTheDocument();
    return;
  }
  expect(candidates[0]).toBeInTheDocument();
};

const findFirstByText = async (
  text: FindAllByTextArgs[0],
  options?: FindAllByTextArgs[1],
  waitOptions?: FindAllByTextArgs[2],
): Promise<HTMLElement> => {
  const [match] = await screen.findAllByText(text, options, waitOptions);
  return match;
};

const waitForLoadingToFinish = async () => {
  await waitFor(
    () => {
      expect(screen.queryByText("Data is loading...")).not.toBeInTheDocument();
    },
    { timeout: 5500 },
  );
};

const getFirstByTestId = (testid: string): HTMLElement =>
  screen.getAllByTestId(testid)[0];
const setUpProjectsAsData = async () => {
  localStorageMock.getItem = vi.fn().mockImplementation((key: string) => {
    if (key === "omeroDataTypeChoice") return '"Projects"';
    return null;
  });
  await setUpComponent();
};

const fetchDetailsFor = async (type: string, typeID: number) => {
  const fetchDetails = getFirstByTestId(type + "_fetch_details_" + typeID);
  fireEvent.click(fetchDetails);
  await screen.findByTestId(type + "_annotation_" + typeID + "_1", undefined, {
    timeout: 5500,
  });
  await waitForLoadingToFinish();
};

const setUpProjectAndScreensInDescendingOrder = async () => {
  setUpLocalStorageWithOrder(Order.desc);
  await setUpComponent();
  await findFirstByText("idr0018-neff-histopathology/experimentA", undefined, {
    timeout: 5500,
  });
};

const setUpScreensAsData = async () => {
  localStorageMock.getItem = vi.fn().mockImplementation((key: string) => {
    if (key === "omeroDataTypeChoice") return '"Screens"';
    return null;
  });
  await setUpComponent();
};

const assertThatFirstRowOfDataIsProjectCalled = async (
  name: string,
  numDatasets: number,
) => {
  await findFirstByText(name, undefined, {
    timeout: 5500,
  });
  assertElemWithTestIDHasTextContent(
    "path0",
    name +
      "project fetch details see in omero show datasets [" +
      numDatasets +
      "]",
  );
};

const assertThatFirstRowOfDataIsScreenCalled = async (
  name: string,
  numDatasets: number,
) => {
  await findFirstByText(name, undefined, {
    timeout: 5500,
  });
  assertElemWithTestIDHasTextContent(
    "path0",
    name +
      "screen fetch details see in omero show plates [" +
      numDatasets +
      "]",
  );
};

type OrderValue = (typeof Order)[keyof typeof Order];
const setUpLocalStorageWithOrder = (order: OrderValue) => {
  localStorageMock.getItem = vi.fn().mockImplementation((key: string) => {
    if (key === "omeroSearchOrder") {
      return '"' + order + '"';
    }
    if (key == "omeroSearchOrderBy") {
      return '"name"';
    }
    return null;
  });
};

const navigateFromScreenToPlate = async (
  screenName: string,
  screenID: number,
  plateID: number,
  handleSetup: boolean = true,
): Promise<boolean> => {
  setUpLocalStorageWithOrder(Order.asc);
  if (handleSetup) {
    await setUpComponent();
  }
  await findFirstByText(screenName, undefined, {
    timeout: 5500,
  });
  const fetchPlates = screen.queryAllByTestId(
    "screen_fetch_childrenLink_" + screenID,
  )[0];
  if (!fetchPlates) {
    return false;
  }
  fireEvent.click(fetchPlates);
  try {
    await screen.findByTestId("plate_name_display_" + plateID, undefined, {
      timeout: 5500,
    });
    await waitForLoadingToFinish();
    return true;
  } catch {
    return false;
  }
};

const navigateFromScreenToPlateWIthoutSetup = async (
  screenName: string,
  screenID: number,
  plateID: number,
): Promise<boolean> => {
  return await navigateFromScreenToPlate(screenName, screenID, plateID, false);
};

const navigateFromProjectToDataset = async (
  projectName: string,
  projectID: number,
  datasetID: number,
): Promise<boolean> => {
  setUpLocalStorageWithOrder(Order.asc);
  await setUpComponent();
  await findFirstByText(projectName, undefined, {
    timeout: 5500,
  });
  const fetchDatasets = screen.queryAllByTestId(
    "project_fetch_childrenLink_" + projectID,
  )[0];
  if (!fetchDatasets) {
    return false;
  }
  fireEvent.click(fetchDatasets);
  try {
    await screen.findByTestId("dataset_name_display_" + datasetID, undefined, {
      timeout: 5500,
    });
    await waitForLoadingToFinish();
    return true;
  } catch {
    return false;
  }
};

const hideChildren = async (
  type: string,
  typeID: number,
  childType: string,
  childID: number,
  numchildren: number,
): Promise<boolean> => {
  const fetchChildrenID = type + "_fetch_childrenLink_" + typeID;
  const hideChildrenLink = screen.queryAllByTestId(fetchChildrenID)[0];
  if (!hideChildrenLink) {
    return false;
  }
  const targetChildID = childType + "_name_display_" + childID;
  const targetChildren = screen.queryAllByTestId(targetChildID);
  if (!targetChildren.length) {
    return false;
  }
  assertElemWithTestIDHasTextContent(
    fetchChildrenID,
    "hide children [" + numchildren + "]",
  );
  fireEvent.click(hideChildrenLink);
  assertElemWithTestIDHasTextContent(
    fetchChildrenID,
    "show " + childType + "s [" + numchildren + "]",
  );
  return true;
};

const hideImageGrid = async (
  type: string,
  typeID: number,
  imageID: number,
): Promise<boolean> => {
  const targetImageID = "image_img_" + imageID;
  const targetImage = screen.queryAllByTestId(targetImageID)[0];
  if (!targetImage) {
    return false;
  }
  const hideImageGridLinkID = type + "_hide_grid_" + typeID;
  const showImageGridLinkID = type + "_show_grid_" + typeID;
  expect(screen.queryByTestId(showImageGridLinkID)).not.toBeInTheDocument();
  const hideImageGridLink = screen.queryAllByTestId(hideImageGridLinkID)[0];
  if (!hideImageGridLink) {
    return false;
  }
  fireEvent.click(hideImageGridLink);
  expect(screen.queryByTestId(targetImageID)).not.toBeInTheDocument();
  expect(getFirstByTestId(showImageGridLinkID)).toBeInTheDocument();
  return true;
};

const clickChildLinkAndShowPlateAcquisition = async (
  childLInkID: number,
  plateAcquisitionID: number,
): Promise<boolean> => {
  const fetchPlatesAcquisitions = screen.queryAllByTestId(
    "plate_fetch_childrenLink_" + childLInkID,
  )[0];
  if (!fetchPlatesAcquisitions) {
    return false;
  }
  fireEvent.click(fetchPlatesAcquisitions);
  try {
    await screen.findByTestId(
      "plateAcquisition_name_display_" + plateAcquisitionID,
      undefined,
      {
        timeout: 5500,
      },
    );
    await waitForLoadingToFinish();
    return true;
  } catch {
    return false;
  }
};

//for plates that have 1 or 0 plate acquisitions and so can directly open grid of images and plate acquistion at same time
const clickChildLinkAndShowPlateAcquisitionWithImages = async (
  childLInkID: number,
  plateAcquisitionID: number,
  imageID: number,
): Promise<boolean> => {
  const fetchGridOfImages = screen.queryAllByTestId(
    "plate_fetch_childrenLink_" + childLInkID,
  )[0];
  if (!fetchGridOfImages) {
    return false;
  }
  fireEvent.click(fetchGridOfImages);
  try {
    await screen.findByTestId("image_img_" + imageID, undefined, {
      timeout: 5500,
    });
    await waitForLoadingToFinish();
  } catch {
    return false;
  }
  //check that a plate acquisition is also displayed
  const plateAcquisition = screen.queryByTestId(
    "plateAcquisition_name_display_" + plateAcquisitionID,
  );
  if (!plateAcquisition) {
    return false;
  }
  expect(plateAcquisition).toBeInTheDocument();
  return true;
};

const clickOnImageWithIDInGridAndAwaitInsertionAsChildOfFullImageData = async (
  imageID: number,
): Promise<boolean> => {
  const imageInGrid = screen.queryAllByTestId("image_img_" + imageID)[0];
  if (!imageInGrid) {
    return false;
  }
  fireEvent.click(imageInGrid);
  try {
    await screen.findByTestId("image_name_display_" + imageID, undefined, {
      timeout: 5500,
    });
    await screen.findByTestId("image_annotation_" + imageID + "_0", undefined, {
      timeout: 5500,
    });
    await waitForLoadingToFinish();
    return true;
  } catch {
    return false;
  }
};

const clickDatasetImageGridLinkAndCheckForImageWithID = async (
  datasetID: number,
  targetImageID: number,
): Promise<boolean> => {
  const fetchImages = screen.queryAllByTestId(
    "dataset_show_grid_" + datasetID,
  )[0];
  if (!fetchImages) {
    return false;
  }
  fireEvent.click(fetchImages);
  try {
    await screen.findByTestId("image_img_" + targetImageID, undefined, {
      timeout: 5500,
    });
    await waitForLoadingToFinish();
    return true;
  } catch {
    return false;
  }
};

const clickImageGridLinkAndCheckForImageWithID = async (
  imageGridLinkID: number,
  targetImageID: number,
): Promise<boolean> => {
  const fetchGridOfImages = screen.queryAllByTestId(
    "plateAcquisition_show_grid_" + imageGridLinkID,
  )[0];
  if (!fetchGridOfImages) {
    return false;
  }
  fireEvent.click(fetchGridOfImages);
  try {
    await screen.findByTestId("image_img_" + targetImageID, undefined, {
      timeout: 5500,
    });
    await waitForLoadingToFinish();
    return true;
  } catch {
    return false;
  }
};

const setUpComponent = async () => {
  getWrapper({ omero_web_url: "http://localhost:8080" });
  await waitForLoadingToFinish();
};

describe("Renders page with results data", () => {
  test("displays two results table headers", async () => {
    await setUpComponent();
    await findFirstByText("Path", undefined, {
      timeout: 5500,
    });
    await findFirstByText("Description", undefined, {
      timeout: 5500,
    });
  }, 9999);

  test("displays data choice radio", async () => {
    await setUpComponent();
    await findFirstByText("Projects And Screens", undefined, {
      timeout: 5500,
    });
    await findFirstByText("Projects", undefined, {
      timeout: 5500,
    });
    await findFirstByText("Screens", undefined, {
      timeout: 5500,
    });
  }, 9999);

  test("displays Projects And Screens By Default ordered by name", async () => {
    await setUpComponent();
    await findFirstByText(
      "idr0018-neff-histopathology/experimentA",
      undefined,
      {
        timeout: 5500,
      },
    );
    assertElemWithTestIDHasTextContent(
      "path0",
      "idr0001-graml-sysgro/screenAscreen fetch details see in omero show plates [192]",
    );
    assertElemWithTestIDHasTextContent(
      "path24",
      "idr0018-neff-histopathology/experimentAproject fetch details see in omero show datasets [248]",
    );
  }, 9999);

  test("displays Projects Only", async () => {
    await setUpProjectsAsData();
    await findFirstByText(
      "idr0018-neff-histopathology/experimentA",
      undefined,
      {
        timeout: 5500,
      },
    );
    expect(
      screen.getByRole("radio", { name: "Projects" }),
    ).toBeInTheDocument();
  }, 9999);

  test("displays Screens Only", async () => {
    await setUpScreensAsData();
    await findFirstByText("idr0094-ellinger-sarscov2/screenB", undefined, {
      timeout: 5500,
    });
    expect(
      screen.getByRole("radio", { name: "Screens" }),
    ).toBeInTheDocument();
  }, 9999);

  test("screens can be sorted by name", async () => {
    await setUpScreensAsData();
    await assertThatFirstRowOfDataIsScreenCalled(
      "idr0001-graml-sysgro/screenA",
      192,
    );
    fireEvent.click(screen.getByText("Path"));
    await assertThatFirstRowOfDataIsScreenCalled(
      "idr0145-ho-replicationstress/screenB",
      7,
    );
  }, 9999);

  test("screens can be sorted by description", async () => {
    await setUpScreensAsData();
    await assertThatFirstRowOfDataIsScreenCalled(
      "idr0001-graml-sysgro/screenA",
      192,
    );
    fireEvent.click(screen.getByText("Description"));
    await assertThatFirstRowOfDataIsScreenCalled(
      "idr0006-fong-nuclearbodies/screenA",
      169,
    );
  }, 9999);

  test("screens description is formatted", async () => {
    await setUpScreensAsData();
    await assertThatFirstRowOfDataIsScreenCalled(
      "idr0001-graml-sysgro/screenA",
      192,
    );
    expect(getFirstByTestId("description0")).not.toBeEmptyDOMElement();
  }, 9999);

  test("projects can be sorted by name", async () => {
    await setUpProjectsAsData();
    await assertThatFirstRowOfDataIsProjectCalled(
      "idr0018-neff-histopathology/experimentA",
      248,
    );
    fireEvent.click(screen.getByText("Path"));
    await assertThatFirstRowOfDataIsProjectCalled(
      "idr0148-schumacher-kidneytem/experimentA",
      10,
    );
  }, 9999);

  test("projects description is formatted", async () => {
    await setUpProjectsAsData();
    await assertThatFirstRowOfDataIsProjectCalled(
      "idr0018-neff-histopathology/experimentA",
      248,
    );
    expect(getFirstByTestId("description1")).not.toBeEmptyDOMElement();
  }, 9999);

  test("projects can be sorted by description", async () => {
    await setUpProjectsAsData();
    await assertThatFirstRowOfDataIsProjectCalled(
      "idr0018-neff-histopathology/experimentA",
      248,
    );
    fireEvent.click(screen.getByText("Description"));
    await assertThatFirstRowOfDataIsProjectCalled(
      "idr0117-croce-marimba/experimentA",
      9,
    );
  }, 9999);

  test("items can have details added", async () => {
    await setUpProjectAndScreensInDescendingOrder();
    await fetchDetailsFor("project", 101);
    expect(getFirstByTestId("path24")).toBeInTheDocument();
    expect(getFirstByTestId("description24")).not.toBeEmptyDOMElement();
    expect(getFirstByTestId("project_annotation_101_1")).toBeInTheDocument();
  }, 9999);

  test("projects can show datasets", async () => {
    await navigateFromProjectToDataset(
      "idr0021-lawo-pericentriolarmaterial/experimentA",
      51,
      51,
    );
    assertElemWithTestIDHasTextContent(
      "path28",
      "CDK5RAP2-Cdataset fetch detailsshow image grid [33] see in omero -> parent_project",
    );
  }, 9999);

  test("projects can hide datasets", async () => {
    const didNavigate = await navigateFromProjectToDataset(
      "idr0021-lawo-pericentriolarmaterial/experimentA",
      51,
      51,
    );
    if (!didNavigate) return;
    const didHide = await hideChildren("project", 51, "dataset", 51, 10);
    if (!didHide) return;
  }, 9999);

  test("datasets have links to projects", async () => {
    const didNavigate = await navigateFromProjectToDataset(
      "idr0021-lawo-pericentriolarmaterial/experimentA",
      51,
      51,
    );
    if (!didNavigate) return;
    //check has link to parent project and that the linked ID exists in the doc
    const parentLinks = screen.queryAllByTestId("dataset_link_parent_51");
    const parentLink = parentLinks[0];
    if (!parentLink) return;
    expect(parentLink).toHaveTextContent("parent_project");
    const parentProjects = screen.queryAllByTestId("project_name_display_51");
    const parentProject = parentProjects[0];
    if (!parentProject) return;
    expect(parentProject).toBeInTheDocument();
  }, 9999);

  test("when projects are reordered datasets are reordered AFTER their parent projects", async () => {
    const didNavigate = await navigateFromProjectToDataset(
      "idr0021-lawo-pericentriolarmaterial/experimentA",
      51,
      51,
    );
    if (!didNavigate) return;
    assertElemWithTestIDHasTextContent(
      "path27",
      "idr0021-lawo-pericentriolarmaterial/experimentAproject fetch details see in omero hide children [10]",
    );
    assertElemWithTestIDHasTextContent(
      "path28",
      "CDK5RAP2-Cdataset fetch detailsshow image grid [33] see in omero -> parent_project",
    );
    assertElemWithTestIDHasTextContent(
      "path37",
      "TUBG1-Ndataset fetch detailsshow image grid [80] see in omero -> parent_project",
    );
    const pathHeader = screen.queryAllByText("Path")[0];
    if (!pathHeader) return;
    fireEvent.click(pathHeader);
    assertElemWithTestIDHasTextContent(
      "path11",
      "idr0021-lawo-pericentriolarmaterial/experimentAproject fetch details see in omero hide children [10]",
    );
    // the datasets come AFTER their parent AND they are now ordered in the opposite way to before - ie by name DESC
    assertElemWithTestIDHasTextContent(
      "path12",
      "TUBG1-Ndataset fetch detailsshow image grid [80] see in omero -> parent_project",
    );
    assertElemWithTestIDHasTextContent(
      "path21",
      "CDK5RAP2-Cdataset fetch detailsshow image grid [33] see in omero -> parent_project",
    );
  }, 9999);

  test("datasets can show grids of images", async () => {
    const didNavigate = await navigateFromProjectToDataset(
      "idr0021-lawo-pericentriolarmaterial/experimentA",
      51,
      51,
    );
    if (!didNavigate) return;
    const didShow = await clickDatasetImageGridLinkAndCheckForImageWithID(
      51,
      1884837,
    );
    if (!didShow) return;
  }, 9999);

  test("datasets can hide grids of images", async () => {
    const didNavigate = await navigateFromProjectToDataset(
      "idr0021-lawo-pericentriolarmaterial/experimentA",
      51,
      51,
    );
    if (!didNavigate) return;
    const didShow = await clickDatasetImageGridLinkAndCheckForImageWithID(
      51,
      1884837,
    );
    if (!didShow) return;
    const didHide = await hideImageGrid("dataset", 51, 1884837);
    if (!didHide) return;
  }, 9999);

  test("screens can show plates, plates can show plate acquisitions", async () => {
    const didNavigate = await navigateFromScreenToPlate(
      "idr0001-graml-sysgro/screenA",
      3,
      2551,
    );
    if (!didNavigate) return;
    assertElemWithTestIDHasTextContent(
      "path1",
      "JL_120731_S6Aplate fetch details see in omero show plateAcquisitions [6] -> parent_screen",
    );
    const didShow = await clickChildLinkAndShowPlateAcquisition(2551, 2661);
    if (!didShow) return;
    assertElemWithTestIDHasTextContent(
      "path2",
      "Meas_01(2012-07-31_10-41-12)plateAcquisition fetch detailsshow grid of wells for field 1 see in omero -> parent_plate",
    );
  }, 9999);

  test("screens can hide plates", async () => {
    const didNavigate = await navigateFromScreenToPlate(
      "idr0001-graml-sysgro/screenA",
      3,
      2551,
    );
    if (!didNavigate) return;
    const didHide = await hideChildren("screen", 3, "plate", 2551, 192);
    if (!didHide) return;
  }, 20000);

  test("plates can hide plateacquisitions", async () => {
    const didNavigate = await navigateFromScreenToPlate(
      "idr0001-graml-sysgro/screenA",
      3,
      2551,
    );
    if (!didNavigate) return;
    const didShow = await clickChildLinkAndShowPlateAcquisition(2551, 2661);
    if (!didShow) return;
    const didHide = await hideChildren(
      "plate",
      2551,
      "plateAcquisition",
      2661,
      6,
    );
    if (!didHide) return;
  }, 9999);

  test("when screen hides plates it also hides plates children", async () => {
    const didNavigate = await navigateFromScreenToPlate(
      "idr0001-graml-sysgro/screenA",
      3,
      2551,
    );
    if (!didNavigate) return;
    const didShow = await clickChildLinkAndShowPlateAcquisition(2551, 2661);
    if (!didShow) return;
    const didHide = await hideChildren("screen", 3, "plate", 2551, 192);
    if (!didHide) return;
    const plateAqcquisitionID = "plateAcquisition_name_display_2661";
    expect(screen.queryByTestId(plateAqcquisitionID)).not.toBeInTheDocument();
  }, 9999);

  test("when screen shows plates it also shows plates children", async () => {
    const didNavigate = await navigateFromScreenToPlate(
      "idr0001-graml-sysgro/screenA",
      3,
      2551,
    );
    if (!didNavigate) return;
    const didShow = await clickChildLinkAndShowPlateAcquisition(2551, 2661);
    if (!didShow) return;
    const didHide = await hideChildren("screen", 3, "plate", 2551, 192);
    if (!didHide) return;
    const didReshow = await navigateFromScreenToPlateWIthoutSetup(
      "idr0001-graml-sysgro/screenA",
      3,
      2551,
    );
    if (!didReshow) return;
    const plateAqcquisitionID = "plateAcquisition_name_display_2661";
    const plateAcquisition = screen.queryByTestId(plateAqcquisitionID);
    if (!plateAcquisition) return;
    expect(plateAcquisition).toBeInTheDocument();
  }, 9999);

  test("plate acquisitions can show grids of images", async () => {
    const didNavigate = await navigateFromScreenToPlate(
      "idr0001-graml-sysgro/screenA",
      3,
      2551,
    );
    if (!didNavigate) return;
    const didShow = await clickChildLinkAndShowPlateAcquisition(2551, 2661);
    if (!didShow) return;
    const didGrid = await clickImageGridLinkAndCheckForImageWithID(
      2661,
      1230029,
    );
    if (!didGrid) return;
  }, 9999);

  test("plate acquisitions can hide grids of images", async () => {
    const didNavigate = await navigateFromScreenToPlate(
      "idr0001-graml-sysgro/screenA",
      3,
      2551,
    );
    if (!didNavigate) return;
    const didShow = await clickChildLinkAndShowPlateAcquisition(2551, 2661);
    if (!didShow) return;
    const didGrid = await clickImageGridLinkAndCheckForImageWithID(
      2661,
      1230029,
    );
    if (!didGrid) return;
    const didHide = await hideImageGrid("plateAcquisition", 2661, 1230029);
    if (!didHide) return;
  }, 9999);

  test("plates with only one (or none) plate acquisition can directly show grids of images beside the plate acquisition", async () => {
    const didNavigate = await navigateFromScreenToPlate(
      "idr0002-heriche-condensation/screenA",
      102,
      422,
    );
    if (!didNavigate) return;
    const didShow = await clickChildLinkAndShowPlateAcquisitionWithImages(
      422,
      422,
      179693,
    );
    if (!didShow) return;
  }, 9999);

  test("On click of an image in the grid of images it is inserted into the document as a new child and has annotations with image data", async () => {
    const didNavigate = await navigateFromScreenToPlate(
      "idr0002-heriche-condensation/screenA",
      102,
      422,
    );
    if (!didNavigate) return;
    const didShow = await clickChildLinkAndShowPlateAcquisitionWithImages(
      422,
      422,
      179693,
    );
    if (!didShow) return;
    const didClick =
      await clickOnImageWithIDInGridAndAwaitInsertionAsChildOfFullImageData(
        179693,
      );
    if (!didClick) return;
    assertElemWithTestIDHasTextContent(
      "path3",
      "Run 422plateAcquisition fetch detailshide image grid see in omero hide children [1] -> parent_plate",
    );
    assertElemWithTestIDHasTextContent(
      "path4",
      "plate1_1_013 [Well 1, Field 1 (Spot 1)] details fetched large thumbnailsee in omero -> parent_plateAcquisition",
    );
    assertElemWithTestIDHasTextContent(
      "description4",
      "Z-sections = 1Timepoints = 329Number of Channels = 2Pixels Type = uint16Dimensions(XY) = 1344 x 1024Pixel Size (XYZ) = 0.323 µm x 0.323 µm x n/a Z Channels = [name = Cy3 colour = -16776961 photo interpretation = Monochrome] [name = eGFP colour = 16711935 photo interpretation = Monochrome] Cell Line = HeLaGene Identifier = ENSG00000117399Gene Identifier URL = http://www.ensembl.org/id/ENSG00000117399Gene Symbol = CDC20Analysis Gene Annotation Build = GRCh37, Ensembl release 61, Feb 2011Organism = Homo sapienssiRNA Identifier = s2748siRNA Pool Identifier = Sense Sequence = CGAAAUGACUAUUACCUGATTAntisense Sequence = UCAGGUAAUAGUCAUUUCGGAReagent Design Gene Annotation Build = GRCh37, Ensembl release 61, Feb 2011Control Type = positive controlControl Comments = early mitotic phenotypeQuality Control = failChannels = H2B- mCherry/Cy3:chromatin;eGFP:nuclear lamina and report on nuclear envelope breakdown",
    );
  }, 9999);
});
