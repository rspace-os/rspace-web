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

function renderDialog(
  subSampleCount: number | null,
  isNewlyCreatedSample = false,
  sampleName = "Sample Fifty Five",
  requesterFullName = "Rita Requester",
) {
  return renderWithRealI18n(
    <ThemeProvider theme={materialTheme}>
      <TransferOwnershipDialog
        open
        requestId={101}
        sampleName={sampleName}
        requesterId={2}
        requesterFullName={requesterFullName}
        isNewlyCreatedSample={isNewlyCreatedSample}
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
  test("says 'All N subsamples' for more than two subsamples", async () => {
    await renderDialog(3);

    expect(screen.getByText("All 3 subsamples will be transferred to Rita Requester")).toBeInTheDocument();
  });

  test("omits the bullet for one subsample, where there is nothing to disambiguate", async () => {
    await renderDialog(1);

    expect(screen.queryByText(/subsamples will be transferred/)).not.toBeInTheDocument();
  });
});

describe("TransferOwnershipDialog new-sample hint (real i18n rich-text rendering)", () => {
  test("renders the newly created sample's name in bold", async () => {
    await renderDialog(null, true);

    const name = screen.getByText("Sample Fifty Five");
    expect(name.tagName).toBe("STRONG");
  });

  // Sample names are free text the current user typed into the Operations Wizard - a name
  // containing angle brackets must never be parsed as markup: not mangled/dropped (correctness),
  // and not capable of producing a real <a>/<strong>/etc of its own (injection). Exercises both
  // the HTML-escaping (`<pH 7>`-style names) and the react-i18next tag-parsing (named/indexed
  // component syntax) risks in one string, matching the proof-of-concept from the reported issue.
  test("shows an angle-bracketed sample name as literal bold text, not parsed markup", async () => {
    const dangerousName = 'Evil <externalLink href="https://evil.example">click me</externalLink> & <1>x</1>';
    await renderDialog(null, true, dangerousName);

    const name = screen.getByText(dangerousName);
    expect(name.tagName).toBe("STRONG");
    expect(screen.queryByRole("link", { name: "click me" })).not.toBeInTheDocument();
    expect(screen.queryByText("click me")).not.toBeInTheDocument();
  });
});

describe("TransferOwnershipDialog ownership warning (real i18n rich-text rendering)", () => {
  test("shows the requester's name in the warning sentence", async () => {
    await renderDialog(null, false);

    expect(
      screen.getByText(
        (_content, element) =>
          Boolean(element?.classList.contains("MuiAlert-message")) &&
          element?.textContent ===
            "You will no longer own this sample. Only Rita Requester or their PI can transfer it back.",
      ),
    ).toBeInTheDocument();
  });

  // requesterFullName is free text (an RSpace account's display name is user-editable) - the same
  // injection risk as the new-sample hint above applies here too.
  test("shows an angle-bracketed requester name as literal text, not parsed markup", async () => {
    const dangerousName = 'Evil <externalLink href="https://evil.example">click me</externalLink> & <1>x</1>';
    await renderDialog(null, false, "Sample Fifty Five", dangerousName);

    expect(screen.getByText(dangerousName)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "click me" })).not.toBeInTheDocument();
    expect(screen.queryByText("click me")).not.toBeInTheDocument();
  });
});
