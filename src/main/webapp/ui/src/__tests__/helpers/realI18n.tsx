import type React from "react";
import { I18nextProvider } from "react-i18next";
import { afterAll, beforeAll } from "vitest";
import { render } from "@/__tests__/customQueries";
import appI18n from "@/modules/common/i18n";
import { createTestI18n, type TestI18nResources } from "./createTestI18n";

type RealI18nConfig = {
  resources: TestI18nResources;
  defaultNS: string;
};

export async function createRealI18nWrapper({
  resources,
  defaultNS,
}: RealI18nConfig): Promise<React.ComponentType<{ children: React.ReactNode }>> {
  const i18n = await createTestI18n(resources, defaultNS);
  return function RealI18nWrapper({ children }: { children: React.ReactNode }) {
    return <I18nextProvider i18n={i18n}>{children}</I18nextProvider>;
  };
}

export async function wrapWithRealI18n(ui: React.ReactElement, config: RealI18nConfig): Promise<React.ReactElement> {
  const i18n = await createTestI18n(config.resources, config.defaultNS);
  return <I18nextProvider i18n={i18n}>{ui}</I18nextProvider>;
}

export async function renderWithRealI18n(ui: React.ReactElement, config: RealI18nConfig) {
  return render(await wrapWithRealI18n(ui, config));
}

export function setupRealAppI18n(): void {
  beforeAll(async () => {
    await appI18n.changeLanguage("en-US");
  });

  afterAll(async () => {
    await appI18n.changeLanguage("cimode");
  });
}
