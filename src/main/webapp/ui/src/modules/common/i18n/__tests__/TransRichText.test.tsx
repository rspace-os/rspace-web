import Link from "@mui/material/Link";
import { ThemeProvider } from "@mui/material/styles";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { describe, expect, it, vi } from "vitest";

import materialTheme from "@/theme";

vi.unmock("react-i18next");

const { Trans } = await import("react-i18next");
const { renderWithRealI18n } = await import("@/__tests__/helpers/realI18n");
const { default: TransRichTextComponent } = await import("@/modules/common/i18n/TransRichText");
const TransRichText = TransRichTextComponent as React.ComponentType<{
  i18nKey: string;
  values?: Record<string, string>;
}>;
const TestTrans = Trans as React.ComponentType<{
  i18nKey: string;
  components: {
    docsLink: React.ReactElement;
  };
}>;

const richTextResources = {
  common: {
    help: {
      centralArticle: "central-article#translated-section",
    },
    richTextTranslationUrlProbe:
      'Read the <strong>important note</strong> and <docsLink href="/docs/from-translation">open the translated docs</docsLink>.',
    richTextComponentUrlProbe:
      "Read the <strong>important note</strong> and <docsLink>open the component docs</docsLink>.",
    richTextDefaultInlineProbe:
      "<strong>Line one</strong><br/><strong>Line two</strong> with <cite>Darwin</cite>, <code>const x = 1</code>, and <kbd>Ctrl K</kbd>.",
    richTextDefaultOrderedListProbe: "<ol><li>First item</li><li>Second item</li></ol>",
    richTextDefaultStrongProbe: "Read the <strong>important note</strong>.",
    richTextDefaultUnorderedListProbe: "<ul><li>Bullet item</li></ul>",
    richTextExternalLinkProbe: 'Visit <externalLink href="https://example.com">the example site</externalLink>.',
    richTextHelpDocsDocLinkProbe: 'See <helpDocs docLink="centralArticle">the central help article</helpDocs>.',
    richTextInterpolatedHelpDocsDocLinkProbe:
      'See <helpDocs docLink="{docLink}">the dynamic central help article</helpDocs>.',
    richTextInternalLinkProbe: 'Go to the <internalLink to="/apps">Apps page</internalLink>.',
  },
};

function RouteProbe(): React.ReactNode {
  const location = useLocation();
  return (
    <>
      <span role="status" aria-label="current path">
        {location.pathname}
      </span>
      <p>
        <TransRichText i18nKey="richTextInternalLinkProbe" />
      </p>
    </>
  );
}

function RichTextProbe(): React.ReactNode {
  return (
    <ThemeProvider theme={materialTheme}>
      <p>
        <TestTrans
          i18nKey="richTextTranslationUrlProbe"
          components={{
            docsLink: <Link href="/docs/fallback">{"fallback docs text"}</Link>,
          }}
        />
      </p>
      <p>
        <TestTrans
          i18nKey="richTextComponentUrlProbe"
          components={{
            docsLink: <Link href="/docs/from-component">{"fallback docs text"}</Link>,
          }}
        />
      </p>
    </ThemeProvider>
  );
}

describe("Trans rich text rendering", () => {
  it("renders basic rich text and supplied components without a compiler opt-out wrapper", async () => {
    await renderWithRealI18n(<RichTextProbe />, { resources: richTextResources, defaultNS: "common" });

    expect(screen.getAllByText("important note").map(({ tagName }) => tagName)).toEqual(["STRONG", "STRONG"]);
    expect(screen.getByRole("link", { name: "open the translated docs" })).toHaveAttribute(
      "href",
      "/docs/from-translation",
    );
    expect(screen.getByRole("link", { name: "open the component docs" })).toHaveAttribute(
      "href",
      "/docs/from-component",
    );
    expect(
      screen.getByText(
        (_content, element) => element?.textContent === "Read the important note and open the translated docs.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        (_content, element) => element?.textContent === "Read the important note and open the component docs.",
      ),
    ).toBeInTheDocument();
  });
});

describe("TransRichText default vocabulary", () => {
  it("renders rich inline tags via the default map with no provider wiring", async () => {
    await renderWithRealI18n(
      <ThemeProvider theme={materialTheme}>
        <p>
          <TransRichText i18nKey="richTextDefaultStrongProbe" />
        </p>
        <p>
          <TransRichText i18nKey="richTextDefaultInlineProbe" />
        </p>
        <TransRichText i18nKey="richTextDefaultOrderedListProbe" />
        <TransRichText i18nKey="richTextDefaultUnorderedListProbe" />
      </ThemeProvider>,
      { resources: richTextResources, defaultNS: "common" },
    );

    expect(screen.getByText("important note").tagName).toBe("STRONG");
    expect(screen.getByText("Line one").nextSibling).toBeInstanceOf(HTMLBRElement);
    expect(screen.getByText("Darwin").tagName).toBe("CITE");
    expect(screen.getByText("const x = 1").tagName).toBe("CODE");
    expect(screen.getByText("Ctrl K").tagName).toBe("KBD");
    expect(screen.getAllByRole("list").map(({ tagName }) => tagName)).toEqual(["OL", "UL"]);
    expect(screen.getAllByRole("listitem").map(({ textContent }) => textContent)).toEqual([
      "First item",
      "Second item",
      "Bullet item",
    ]);
  });

  it("renders external and helpdocs links via the default MUI map", async () => {
    await renderWithRealI18n(
      <ThemeProvider theme={materialTheme}>
        <p>
          <TransRichText i18nKey="richTextExternalLinkProbe" />
        </p>
        <p>
          <TransRichText i18nKey="richTextHelpDocsDocLinkProbe" />
        </p>
        <p>
          <TransRichText i18nKey="richTextInterpolatedHelpDocsDocLinkProbe" values={{ docLink: "centralArticle" }} />
        </p>
      </ThemeProvider>,
      { resources: richTextResources, defaultNS: "common" },
    );

    const external = screen.getByRole("link", { name: "the example site" });
    expect(external).toHaveAttribute("href", "https://example.com");
    expect(external).toHaveAttribute("target", "_blank");
    expect(external).toHaveAttribute("rel", "noreferrer");

    const centralHelpDocs = screen.getByRole("link", { name: "the central help article" });
    expect(centralHelpDocs).toHaveAttribute(
      "href",
      "https://researchspace.helpdocs.io/article/central-article#translated-section",
    );
    expect(centralHelpDocs).toHaveAttribute("target", "_blank");
    expect(centralHelpDocs).toHaveAttribute("rel", "noreferrer");

    const interpolatedCentralHelpDocs = screen.getByRole("link", { name: "the dynamic central help article" });
    expect(interpolatedCentralHelpDocs).toHaveAttribute(
      "href",
      "https://researchspace.helpdocs.io/article/central-article#translated-section",
    );
    expect(interpolatedCentralHelpDocs).toHaveAttribute("target", "_blank");
    expect(interpolatedCentralHelpDocs).toHaveAttribute("rel", "noreferrer");
  });

  it("renders internal links through react-router when a router is present", async () => {
    const user = userEvent.setup();

    await renderWithRealI18n(
      <ThemeProvider theme={materialTheme}>
        <MemoryRouter initialEntries={["/"]}>
          <Routes>
            <Route path="*" element={<RouteProbe />} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>,
      { resources: richTextResources, defaultNS: "common" },
    );

    expect(screen.getByRole("status", { name: "current path" })).toHaveTextContent("/");

    await user.click(screen.getByRole("link", { name: "Apps page" }));

    expect(screen.getByRole("status", { name: "current path" })).toHaveTextContent("/apps");
  });
});
