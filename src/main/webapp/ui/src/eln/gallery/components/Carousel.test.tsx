import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { expectAccessible } from "@/__tests__/customQueries";
import "@/__tests__/__mocks__/matchMedia";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import axios from "@/common/axios";
import { SimpleCarousel } from "./Carousel.story";

const mockAxios = new MockAdapter(axios);

/*
 * Each preview in the carousel loads its content asynchronously after mount, so
 * those background state updates settle outside `act(...)` once the synchronous
 * assertions have run. These warnings are expected here and are silenced so
 * they don't drown out genuine failures.
 */
let restoreConsole: () => void;
beforeEach(() => {
  restoreConsole = silenceConsole(["error"], [/not wrapped in act/]);
});
afterEach(() => {
  restoreConsole();
});

beforeEach(() => {
  mockAxios.reset();
  /*
   * On mount the carousel's preview hooks fire bootstrap requests that are not
   * relevant to the behaviour under test. `/ln` and the deployment-property
   * lookup need specific (non-empty) shapes; the office-suite `supportedExts`
   * calls and anything else are absorbed by the `onAny` catch-all below, which
   * returns the same empty object those endpoints expect.
   */
  mockAxios.onGet("/ln").reply(200, { data: false });
  mockAxios.onGet(/\/deploymentproperties\/ajax\/property.*/).reply(200, false);
  mockAxios.onAny().reply(200, {});
});

describe("Carousel", () => {
  test("Should have no axe violations", async () => {
    const { baseElement } = render(<SimpleCarousel />);

    /*
     * The original Playwright spec filtered out the `landmark-one-main`,
     * `page-has-heading-one` and `region` violations, which are expected when
     * testing an isolated component rather than a full page. Under jsdom the
     * sa11y default ruleset does not flag those structural rules for an isolated
     * component, and colour-contrast rules cannot run, so a plain check passes.
     */
    await expectAccessible(baseElement);
  });

  test("Should show an indicator of progress through listing.", async () => {
    const user = userEvent.setup();
    render(<SimpleCarousel />);

    expect(screen.getByRole("status", { name: "gallery:carousel.currentFileIndex" })).toHaveTextContent("1 / 8");

    await user.click(screen.getByRole("button", { name: /carousel.next/i }));

    expect(screen.getByRole("status", { name: "gallery:carousel.currentFileIndex" })).toHaveTextContent("2 / 8");
  });

  test("Moving to a different file resets the zoom level", async () => {
    const user = userEvent.setup();
    render(<SimpleCarousel />);

    await user.click(screen.getByRole("button", { name: "common:actions.zoomIn" }));
    await user.click(screen.getByRole("button", { name: /carousel.next/i }));

    expect(screen.getByRole("button", { name: "common:actions.resetZoom" })).toBeDisabled();
  });
});
