import {
  describe,
  expect,
  beforeEach,
  it,
} from "vitest";
import RoRIntegration from "../../system-ror/RoRIntegration";
import React from "react";
import axios from "@/common/axios";
import {
  render,
  screen,
  fireEvent,
  act,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import MockAdapter from "axios-mock-adapter";
import v2ROR from "./json/v2Ror.json";
import invalidRoR from "./json/invalidRor.json";
import rorNotFound from "./json/rorNotFound.json";
import rorUpdateSucess from "./json/rorUpdateSuccess.json";
import "__mocks__/resizeObserver";

const mockAxios = new MockAdapter(axios);
const getWrapper = () => {
  return render(<RoRIntegration />);
};
const setupRoRMocks = (existingGlobalRoRID = "") => {
  mockAxios
    .onGet(
      "/system/ror/rorForID/https:__rspacror_forsl____rspacror_forsl__ror.org__rspacror_forsl__02mhbdp94"
    )
    .reply(200, v2ROR);
  mockAxios
    .onGet(
      "/system/ror/rorForID/https:__rspacror_forsl____rspacror_forsl__ror.org__rspacror_forsl__02mhbdp941"
    )
    .reply(200, invalidRoR);
  mockAxios
    .onGet(
      "/system/ror/rorForID/https:__rspacror_forsl____rspacror_forsl__ror.org__rspacror_forsl__02mhbdp92"
    )
    .reply(200, rorNotFound);
  mockAxios
    .onPost(
      "/system/ror/rorForID/https:__rspacror_forsl____rspacror_forsl__ror.org__rspacror_forsl__02mhbdp94"
    )
    .reply(200, rorUpdateSucess);
  mockAxios.onDelete("/system/ror/rorForID/").reply(200, rorUpdateSucess);
  mockAxios
    .onGet("/system/ror/existingGlobalRoRID")
    .reply(200, existingGlobalRoRID);
};

beforeEach(() => {
  mockAxios.resetHandlers();
  setupRoRMocks();
});

const setUpComponent = () => {
  act(() => {
    getWrapper();
  });
};

async function searchForRoRDetails(term = "https://ror.org/02mhbdp94") {
  const [rorInput] = screen.getAllByRole("textbox", {
    name: "Search Registry",
  });
  fireEvent.change(rorInput, {
    target: { value: term },
  });
  expect(rorInput).toHaveValue(term);
  const [searchButton] = await screen.findAllByRole("button", {
    name: /^search$/i,
  });
  fireEvent.click(searchButton);
}

async function assertRoRDetailsText() {
  await screen.findAllByText(/Universidad de Los Andes/);
  await screen.findAllByText(/Bogotá, Colombia/);
  await screen.findAllByText(/Status: active/);
  expect(
    screen.getAllByRole("link", { name: "https://ror.org/02mhbdp94" })[0]
  ).toHaveAttribute("href", "https://ror.org/02mhbdp94");
  expect(
    screen.getAllByRole("link", { name: "http://www.uniandes.edu.co/" })[0]
  ).toHaveAttribute("href", "http://www.uniandes.edu.co/");
}

describe("Renders page with ROR data ", () => {
  it("displays page with searchbar when RoR not linked", async () => {
    setUpComponent();

    await screen.findAllByText(
      "Research Organization Registry (ROR) Integration"
    );
  await screen.findAllByRole("textbox");
    expect(
      screen.queryByText(/A ROR ID is linked to this RSpace Instance./)
    ).not.toBeInTheDocument();
  });
  it("displays page with no searchbar  and with Unlink button when RoR is linked", async () => {
    mockAxios.resetHandlers();
    setupRoRMocks("https://ror.org/02mhbdp94");
    setUpComponent();
    await screen.findAllByText(
      "Research Organization Registry (ROR) Integration"
    );
    await screen.findByText(/A ROR ID is linked to this RSpace Instance./);
    expect(
      screen.getByRole("button", {
        name: /UnLink/,
      })
    ).toBeInTheDocument();
    await assertRoRDetailsText();
  });

  it("displays ROR v2 details on search", async () => {
    setUpComponent();
    await screen.findAllByText(
      "Research Organization Registry (ROR) Integration"
    );
    await screen.findAllByRole("textbox");
    await searchForRoRDetails();
    await screen.findByText(/ROR ID found. Click./);
    await assertRoRDetailsText();
  });

  it("displays error when invalid ROR entered", async () => {
    setUpComponent();
    await screen.findAllByText(
      "Research Organization Registry (ROR) Integration"
    );
    await screen.findAllByRole("textbox");
    await searchForRoRDetails("https://ror.org/02mhbdp941");
    await screen.findByText(/https:\/\/ror.org\/02mhbdp941 is not a valid ROR/);
  });

  it("displays error when ROR details not found", async () => {
    setUpComponent();
    await screen.findAllByText(
      "Research Organization Registry (ROR) Integration"
    );
    await screen.findAllByRole("textbox");
    await searchForRoRDetails("https://ror.org/02mhbdp92");
    await screen.findByText(
      /ROR ID 'https:\/\/ror.org\/02mhbdp92' does not exist/
    );
  });
});
