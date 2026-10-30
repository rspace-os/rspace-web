/*
 * The rest of this suite's sibling test files run against i18next's cimode, which renders every
 * key's identifier rather than its English text - fine for most assertions, but unable to tell
 * apart two different keys' worth of ICU plural output once they're merged into one key (see
 * inventory.json's subsamplesTransferred). This file renders against a real English catalog
 * instead, specifically to prove that plural logic actually resolves to the right wording.
 */
import { ThemeProvider } from "@mui/material/styles";
import { screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import materialTheme from "@/theme";
import TransferOwnershipDialog from "../TransferOwnershipDialog";

function renderDialog(subSampleCount: number | null) {
  return renderWithRealI18n(
    <ThemeProvider theme={materialTheme}>
      <TransferOwnershipDialog
        open
        requestId={101}
        sampleName="Sample Fifty Five"
        requesterId={2}
        requesterFullName="Rita Requester"
        isNewlyCreatedSample={false}
        subSampleCount={subSampleCount}
        otherActiveRequests={null}
        isProcessingAction={false}
        onClose={() => {}}
        onSubmit={() => {}}
      />
    </ThemeProvider>,
    { resources: { inventory: inventoryEn, common: commonEn }, defaultNS: "inventory" },
  );
}

describe("TransferOwnershipDialog subsample-count bullet (real i18n plural rendering)", () => {
  test("says 'Both subsamples' for exactly two subsamples", async () => {
    await renderDialog(2);

    expect(screen.getByText("Both subsamples will be transferred to user Rita Requester")).toBeInTheDocument();
  });

  test("says 'All N subsamples' for more than two subsamples", async () => {
    await renderDialog(3);

    expect(screen.getByText("All 3 subsamples will be transferred to user Rita Requester")).toBeInTheDocument();
  });

  test("omits the bullet for one subsample, where there is nothing to disambiguate", async () => {
    await renderDialog(1);

    expect(screen.queryByText(/subsamples will be transferred/)).not.toBeInTheDocument();
  });
});
