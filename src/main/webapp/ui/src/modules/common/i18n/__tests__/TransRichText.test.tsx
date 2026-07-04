import { ThemeProvider } from "@mui/material/styles";
import type React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@/__tests__/customQueries";
import materialTheme from "@/theme";

vi.unmock("react-i18next");

const { Trans } = await import("react-i18next");
const { wrapWithRealI18n } = await import("@/__tests__/helpers/realI18n");
const { default: TransRichTextComponent } = await import("@/modules/common/i18n/TransRichText");
const TransRichText = TransRichTextComponent as React.ComponentType<{ i18nKey: string }>;
const TestTrans = Trans as React.ComponentType<{
  i18nKey: string;
  components: {
    docsLink: React.ReactElement;
  };
}>;

const richTextResources = {
  common: {
    richTextTranslationUrlProbe:
      'Read the <strong>important note</strong> and <docsLink href="/docs/from-translation">open the translated docs</docsLink>.',
    richTextComponentUrlProbe:
      "Read the <strong>important note</strong> and <docsLink>open the component docs</docsLink>.",
    richTextDefaultMapProbe: 'Open the <a href="/docs">docs</a>.',
    richTextDefaultOrderedListProbe: "<ol><li>First item</li><li>Second item</li></ol>",
    richTextDefaultStrongProbe: "Read the <strong>important note</strong>.",
  },
};

function RichTextProbe(): React.ReactNode {
  return (
    <ThemeProvider theme={materialTheme}>
      <p>
        <TestTrans
          i18nKey="richTextTranslationUrlProbe"
          components={{
            docsLink: <a href="/docs/fallback">{"fallback docs text"}</a>,
          }}
        />
      </p>
      <p>
        <TestTrans
          i18nKey="richTextComponentUrlProbe"
          components={{
            docsLink: <a href="/docs/from-component">{"fallback docs text"}</a>,
          }}
        />
      </p>
    </ThemeProvider>
  );
}

describe("Trans rich text rendering", () => {
  it("renders basic rich text and supplied components without a compiler opt-out wrapper", async () => {
    render(await wrapWithRealI18n(<RichTextProbe />, { resources: richTextResources, defaultNS: "common" }));

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
  it("renders the <link> tag via the default (MUI) map with no provider wiring", async () => {
    render(
      await wrapWithRealI18n(
        <ThemeProvider theme={materialTheme}>
          <p>
            <TransRichText i18nKey="richTextDefaultMapProbe" />
          </p>
          <p>
            <TransRichText i18nKey="richTextDefaultStrongProbe" />
          </p>
          <TransRichText i18nKey="richTextDefaultOrderedListProbe" />
        </ThemeProvider>,
        { resources: richTextResources, defaultNS: "common" },
      ),
    );

    expect(screen.getByRole("link", { name: "docs" })).toHaveAttribute("href", "/docs");
    expect(screen.getByText("important note").tagName).toBe("STRONG");
    expect(screen.getByRole("list")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem").map(({ textContent }) => textContent)).toEqual([
      "First item",
      "Second item",
    ]);
  });
});
