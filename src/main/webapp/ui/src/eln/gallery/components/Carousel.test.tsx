import { beforeEach, describe, expect, test } from "vitest";
import { expectAccessible } from "@/__tests__/customQueries";
import "@/__tests__/__mocks__/matchMedia";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { SimpleCarousel } from "./Carousel.story";

const mockAxios = new MockAdapter(axios);

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

async function waitForPreviewImages() {
  await waitFor(() => {
    expect(screen.getAllByRole("img", { name: "gallery:carousel.previewAlt", hidden: true })).toHaveLength(8);
  });
}

describe("Carousel", () => {
  test("Should have no axe violations", async () => {
    const { baseElement } = render(<SimpleCarousel />);
    await waitForPreviewImages();

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
    await waitForPreviewImages();

    expect(screen.getByRole("status", { name: "gallery:carousel.currentFileIndex" })).toHaveTextContent("1 / 8");

    await user.click(screen.getByRole("button", { name: "gallery:carousel.next" }));

    expect(screen.getByRole("status", { name: "gallery:carousel.currentFileIndex" })).toHaveTextContent("2 / 8");
  });

  test("Moving to a different file resets the zoom level", async () => {
    const user = userEvent.setup();
    render(<SimpleCarousel />);
    await waitForPreviewImages();

    await user.click(screen.getByRole("button", { name: "common:actions.zoomIn" }));
    await user.click(screen.getByRole("button", { name: "gallery:carousel.next" }));

    expect(screen.getByRole("button", { name: "common:actions.resetZoom" })).toBeDisabled();
  });
});
