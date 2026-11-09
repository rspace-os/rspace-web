import { Loader2Icon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { cn } from "@/modules/common/utils/cn";

function Spinner({ className, ...props }: React.ComponentProps<"svg">) {
  const { t } = useTranslation("common");

  return (
    <Loader2Icon
      data-slot="spinner"
      role="status"
      aria-label={t("loading")}
      className={cn("size-4 animate-spin", className)}
      {...props}
    />
  );
}

export { Spinner };

function ViewportLoadingSpinner() {
  return (
    <div className="fixed inset-0 grid place-items-center">
      <Spinner className="size-8" />
    </div>
  );
}

function ContentLoadingSpinner() {
  return (
    <div className="grid min-h-48 flex-1 place-items-center self-stretch">
      <Spinner className="size-8" />
    </div>
  );
}

export { ContentLoadingSpinner, ViewportLoadingSpinner };
