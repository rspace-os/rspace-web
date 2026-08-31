import { useTranslation } from "react-i18next";

export default function NotFoundPage() {
  const { t } = useTranslation("common");
  return (
    <div className="flex h-screen items-center justify-center text-foreground">
      <p>{t("notFound.message")}</p>
    </div>
  );
}
