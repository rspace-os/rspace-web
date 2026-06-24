import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import axios from "@/common/axios";
import RoRIntegration from "../../system-ror/RoRIntegration";
import invalidRoR from "./json/invalidRor.json";
import rorNotFound from "./json/rorNotFound.json";
import rorUpdateSucess from "./json/rorUpdateSuccess.json";
import v2ROR from "./json/v2Ror.json";

import "@/__tests__/__mocks__/resizeObserver";

type HTMLElementWithRorRoot = HTMLElement & {
  rorRoot?: { unmount: () => void };
};

const mockAxios = new MockAdapter(axios);
const getWrapper = () => {
  return render(<RoRIntegration />);
};
const setupRoRMocks = (existingGlobalRoRID = "") => {
  mockAxios
    .onGet("/system/ror/rorForID/https:__rspacror_forsl____rspacror_forsl__ror.org__rspacror_forsl__02mhbdp94")
    .reply(200, v2ROR);
  mockAxios
    .onGet("/system/ror/rorForID/https:__rspacror_forsl____rspacror_forsl__ror.org__rspacror_forsl__02mhbdp941")
    .reply(200, invalidRoR);
  mockAxios
    .onGet("/system/ror/rorForID/https:__rspacror_forsl____rspacror_forsl__ror.org__rspacror_forsl__02mhbdp92")
    .reply(200, rorNotFound);
  mockAxios
    .onPost("/system/ror/rorForID/https:__rspacror_forsl____rspacror_forsl__ror.org__rspacror_forsl__02mhbdp94")
    .reply(200, rorUpdateSucess);
  mockAxios.onDelete("/system/ror/rorForID/").reply(200, rorUpdateSucess);
  mockAxios.onGet("/system/ror/existingGlobalRoRID").reply(200, existingGlobalRoRID);
};
beforeEach(() => {
  mockAxios.resetHandlers();
  setupRoRMocks();
});

afterEach(() => {
  const existingMainArea: HTMLElementWithRorRoot | null = document.getElementById("mainArea");
  existingMainArea?.rorRoot?.unmount();
  document.body.innerHTML = "";
});
const setUpComponent = () => {
  act(() => {
    getWrapper();
  });
};
async function searchForRoRDetails(term = "https://ror.org/02mhbdp94") {
  const rorInput = screen.getByRole("textbox", {
    name: "ror.searchTooltip",
  });
  fireEvent.change(rorInput, {
    target: { value: term },
  });
  expect(rorInput).toHaveValue(term);
  const searchButton = await screen.findByLabelText("actions.search");
  fireEvent.click(searchButton);
}
async function assertRoRDetailsText() {
  await screen.findByText(/Universidad de Los Andes/);
  await screen.findByText(/Bogotá, Colombia/);
  await screen.findByText(/ror.statusLabel active/);
  expect(screen.getAllByRole("link", { name: "https://ror.org/02mhbdp94" })[0]).toHaveAttribute(
    "href",
    "https://ror.org/02mhbdp94",
  );
  expect(screen.getAllByRole("link", { name: "http://www.uniandes.edu.co/" })[0]).toHaveAttribute(
    "href",
    "http://www.uniandes.edu.co/",
  );
}

describe("Renders page with ROR data", () => {
  test("loads the RoR view into mainArea from the config link", async () => {
    document.body.innerHTML = '<a href="#" id="rorRegistryLink">RoR</a><div id="mainArea">stale content</div>';

    act(() => {
      window.dispatchEvent(new Event("load"));
      fireEvent.click(screen.getByRole("link", { name: "RoR" }));
    });

    await waitFor(() => expect(screen.queryByText("stale content")).not.toBeInTheDocument());
    await screen.findByText("ror.heading");
  });

  test("displays page with searchbar when RoR not linked", async () => {
    setUpComponent();

    await screen.findByText("ror.heading");
    await screen.findByRole("textbox");
    expect(screen.queryByText(/A ROR ID is linked to this RSpace Instance./)).not.toBeInTheDocument();
  });
  test("displays page with no searchbar, and with Unlink button when RoR is linked", async () => {
    mockAxios.resetHandlers();
    setupRoRMocks("https://ror.org/02mhbdp94");
    setUpComponent();
    await screen.findByText("ror.heading");
    await screen.findByText(/ror.unlinkHelpPrefix/);
    expect(
      screen.getByRole("button", {
        name: /ror.unlinkButton/,
      }),
    ).toBeInTheDocument();
    await assertRoRDetailsText();
  });

  test("displays ROR v2 details on search", async () => {
    setUpComponent();
    await screen.findByText("ror.heading");
    await screen.findByRole("textbox");
    await searchForRoRDetails();
    await screen.findByText(/ror.linkHelpPrefix/);
    await assertRoRDetailsText();
  });

  test("displays error when invalid ROR entered", async () => {
    setUpComponent();
    await screen.findByText("ror.heading");
    await screen.findByRole("textbox");
    await searchForRoRDetails("https://ror.org/02mhbdp941");
    await screen.findByText(/https:\/\/ror.org\/02mhbdp941 is not a valid ROR/);
  });

  test("displays error when ROR details not found", async () => {
    setUpComponent();
    await screen.findByText("ror.heading");
    await screen.findByRole("textbox");
    await searchForRoRDetails("https://ror.org/02mhbdp92");
    await screen.findByText(/ROR ID 'https:\/\/ror.org\/02mhbdp92' does not exist/);
  });
});
