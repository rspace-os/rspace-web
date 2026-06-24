import { ThemeProvider } from "@mui/material/styles";
import { createInstance, type i18n as I18nInstance } from "i18next";
import type React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@/__tests__/customQueries";
import materialTheme from "@/theme";

vi.unmock("react-i18next");

const { I18nextProvider, Trans, initReactI18next } = await import("react-i18next");
const TestTrans = Trans as React.ComponentType<{
  i18nKey: string;
  components: {
    docsLink: React.ReactElement;
  };
}>;

async function createTestI18n(): Promise<I18nInstance> {
  const i18n = createInstance();
  await i18n.use(initReactI18next).init({
    lng: "en-US",
    fallbackLng: "en-US",
    resources: {
      "en-US": {
        common: {
          richTextCompilerProbe: "Read the <strong>important note</strong> and <docsLink>open the docs</docsLink>.",
        },
      },
    },
    defaultNS: "common",
    interpolation: { escapeValue: false },
    react: { useSuspense: false },
  });
  return i18n;
}

function RichTextProbe({ i18n }: { i18n: I18nInstance }): React.ReactNode {
  return (
    <ThemeProvider theme={materialTheme}>
      <I18nextProvider i18n={i18n}>
        <p>
          <TestTrans
            i18nKey="richTextCompilerProbe"
            components={{
              docsLink: <a href="/docs/rich-text">open the docs</a>,
            }}
          />
        </p>
      </I18nextProvider>
    </ThemeProvider>
  );
}

describe("Trans rich text rendering", () => {
  it("renders basic rich text and supplied components without a compiler opt-out wrapper", async () => {
    render(<RichTextProbe i18n={await createTestI18n()} />);

    expect(screen.getByText("important note").tagName).toBe("STRONG");
    expect(screen.getByRole("link", { name: "open the docs" })).toHaveAttribute("href", "/docs/rich-text");
    expect(screen.getByText(/Read the/)).toHaveTextContent("Read the important note and open the docs.");
  });
});
