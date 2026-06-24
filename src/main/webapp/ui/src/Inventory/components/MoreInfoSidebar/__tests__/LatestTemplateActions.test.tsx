import { afterEach, describe, expect, test, vi } from "vitest";

const mockRootStore = {
  uiStore: { confirm: vi.fn(() => Promise.resolve(true)), addAlert: vi.fn() },
  unitStore: { getUnit: () => ({ label: "ml" }) },
};
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => mockRootStore,
}));
vi.mock("../../../../common/InvApiService", () => ({
  default: { post: vi.fn(), get: vi.fn() },
}));

import { ThemeProvider } from "@mui/material/styles";
import { cleanup, render, screen } from "@/__tests__/customQueries";
import { makeMockTemplate } from "../../../../stores/models/__tests__/TemplateModel/mocking";
import materialTheme from "../../../../theme";
import LatestTemplateActions from "../LatestTemplateActions";

function renderFor(record: unknown) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <LatestTemplateActions record={record as never} />
    </ThemeProvider>,
  );
}

describe("LatestTemplateActions", () => {
  afterEach(cleanup);

  test("hides the Update Samples button when no samples need updating", () => {
    renderFor(makeMockTemplate({ samplesToUpdateCount: 0 }));
    expect(screen.queryByRole("button", { name: "moreInfo.updateSamples" })).not.toBeInTheDocument();
  });

  test("shows the Update Samples button when samples need updating", () => {
    renderFor(makeMockTemplate({ samplesToUpdateCount: 2 }));
    expect(screen.getByRole("button", { name: "moreInfo.updateSamples" })).toBeInTheDocument();
  });

  test("stays hidden for a historical version even when samples need updating", () => {
    renderFor(makeMockTemplate({ samplesToUpdateCount: 2, historicalVersion: true }));
    expect(screen.queryByRole("button", { name: "moreInfo.updateSamples" })).not.toBeInTheDocument();
  });
});
