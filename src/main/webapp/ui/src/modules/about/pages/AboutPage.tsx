import AboutPanel from "@/modules/about/AboutPanel";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { baseUiRichTextComponents } from "@/modules/common/i18n/TransRichText";

export default function AboutPage() {
  return (
    <main className="min-h-screen">
      <div className="container mx-auto flex min-h-screen items-center justify-center px-4">
        <I18nRoot namespaces={["about"]} componentMap={baseUiRichTextComponents}>
          <AboutPanel />
        </I18nRoot>
      </div>
    </main>
  );
}
