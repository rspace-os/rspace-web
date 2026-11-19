import { render, screen } from "@testing-library/react";
import { I18nextProvider } from "react-i18next";
import { describe, expect, it } from "vitest";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import RevealedFieldsAnnouncer from "../RevealedFieldsAnnouncer";

async function renderAnnouncer(shown: Array<string>, hidden: Array<string>) {
  const i18n = await createTestI18n({ common: commonEn }, "common");
  const announcer = (shownFieldNames: Array<string>, hiddenFieldNames: Array<string>) => (
    <I18nextProvider i18n={i18n}>
      <RevealedFieldsAnnouncer shownFieldNames={shownFieldNames} hiddenFieldNames={hiddenFieldNames} />
    </I18nextProvider>
  );
  const { rerender } = render(announcer(shown, hidden));
  return (nextShown: Array<string>, nextHidden: Array<string>) => rerender(announcer(nextShown, nextHidden));
}

describe("RevealedFieldsAnnouncer", () => {
  it("names the fields a change shows and the fields it hides, in one update", async () => {
    const change = await renderAnnouncer(["Containment level"], ["GMO permit", "Export licence"]);

    change(["GMO permit", "Export licence"], ["Containment level"]);

    expect(screen.getByRole("status")).toHaveTextContent(
      "2 more fields are shown: GMO permit and Export licence. One field is hidden: Containment level.",
    );
  });

  it("says nothing about a field that arrives already hidden", async () => {
    const change = await renderAnnouncer(["Containment level"], []);

    change(["Containment level"], ["GMO permit"]);

    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });
});
