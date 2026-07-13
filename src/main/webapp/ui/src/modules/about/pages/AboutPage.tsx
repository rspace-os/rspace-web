import { type AnyRoute, createRoute } from "@tanstack/react-router";
import AboutPanel from "@/modules/about/AboutPanel";
import i18n from "@/modules/common/i18n";
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

export function createAboutRoute<TParentRoute extends AnyRoute>(rootRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => rootRoute,
    path: "/about",
    beforeLoad: () => ({ appBar: { currentPage: "aboutRSpace", authenticated: false } }),
    head: () => ({
      // `common` loads eagerly at i18next init, so this synchronous lookup is safe
      // outside the component tree; the lazy `about` namespace would return the raw key here.
      meta: [{ title: i18n.t("common:appBar.aboutRSpace") }],
    }),
    component: AboutPage,
  });
}
