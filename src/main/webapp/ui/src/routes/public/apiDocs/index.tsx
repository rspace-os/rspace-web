import { createFileRoute } from "@tanstack/react-router";
import ApiDocsPage from "@/modules/api/components/ApiDocsPage";
import i18n from "@/modules/common/i18n";

export const Route = createFileRoute("/public/apiDocs/")({
  beforeLoad: () => ({ appBar: false as const }),
  head: () => ({ meta: [{ title: i18n.t("common:apiDocs.pageTitle") }] }),
  component: ApiDocsPage,
});
