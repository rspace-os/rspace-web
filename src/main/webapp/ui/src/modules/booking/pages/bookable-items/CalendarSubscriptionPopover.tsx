import { faApple } from "@fortawesome/free-brands-svg-icons/faApple";
import { faGoogle } from "@fortawesome/free-brands-svg-icons/faGoogle";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarIcon, CalendarPlusIcon, CheckIcon, CopyIcon, LoaderCircleIcon, XIcon } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from "@/modules/common/ui/input-group";
import {
  Popover,
  PopoverClose,
  PopoverContent,
  PopoverDescription,
  PopoverHeader,
  PopoverTrigger,
} from "@/modules/common/ui/popover";
import {
  calendarApplicationUrls,
  calendarSubscriptionQueryKey,
  createOrReplaceCalendarSubscription,
  fetchCalendarSubscriptionStatus,
} from "./bookableItemCalendarSubscription";

export function CalendarSubscriptionPopover({ configurationId, token }: { configurationId: number; token: string }) {
  const { t } = useTranslation("booking");
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const [clipboardError, setClipboardError] = useState(false);
  const [focusGoogle, setFocusGoogle] = useState(false);
  const popupId = `calendar-subscription-${useId()}`;
  const headingId = `${popupId}-heading`;
  const descriptionId = `${popupId}-description`;
  const fieldId = `${popupId}-url`;
  const copyLabelId = `${fieldId}-label`;
  const autoGenerateOnOpenRef = useRef(false);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const googleActionRef = useRef<HTMLAnchorElement>(null);

  const queryKey = calendarSubscriptionQueryKey(configurationId);
  const status = useQuery({
    queryKey,
    queryFn: ({ signal }) => fetchCalendarSubscriptionStatus(configurationId, token, signal),
    enabled: open,
    retry: false,
  });

  const createMutation = useMutation({
    mutationFn: () => createOrReplaceCalendarSubscription(configurationId, token),
    retry: false,
    onMutate: () => {
      setCopied(false);
      setClipboardError(false);
    },
    onSuccess: (created) => {
      queryClient.setQueryData(queryKey, created);
      setFocusGoogle(true);
    },
  });

  const subscriptionUrl = status.data?.subscriptionUrl ?? null;

  useEffect(() => {
    if (!open || !autoGenerateOnOpenRef.current || status.isFetching || !status.isSuccess) return;
    autoGenerateOnOpenRef.current = false;
    if (status.data.subscriptionUrl === null) createMutation.mutate();
  }, [createMutation, open, status.data, status.isFetching, status.isSuccess]);

  useEffect(() => {
    if (open) requestAnimationFrame(() => headingRef.current?.focus());
  }, [open]);

  useEffect(() => {
    if (!focusGoogle || googleActionRef.current === null) return;
    googleActionRef.current.focus();
    setFocusGoogle(false);
  }, [focusGoogle, subscriptionUrl]);

  const close = () => {
    autoGenerateOnOpenRef.current = false;
    setOpen(false);
    setCopied(false);
    setClipboardError(false);
  };

  const copyLink = async () => {
    if (subscriptionUrl === null) return;
    setCopied(false);
    setClipboardError(false);
    try {
      await navigator.clipboard.writeText(subscriptionUrl);
      setCopied(true);
    } catch {
      setClipboardError(true);
    }
  };

  const calendarLinks = () => {
    if (subscriptionUrl === null) return null;
    const apps = calendarApplicationUrls(subscriptionUrl);
    return (
      <div className="space-y-5">
        <div className="space-y-2">
          <p className="text-sm font-medium">{t("bookableItemDetails.calendarSubscription.appPrompt")}</p>
          <div className="grid grid-cols-3 gap-2">
            <a className={buttonVariants({ variant: "outline", className: "min-w-0 px-2" })} href={apps.apple}>
              <FontAwesomeIcon icon={faApple} className="size-4" />
              {t("bookableItemDetails.calendarSubscription.apple")}
            </a>
            <a
              ref={googleActionRef}
              className={buttonVariants({ variant: "outline", className: "min-w-0 px-2" })}
              href={apps.google}
              target="_blank"
              rel="noreferrer"
            >
              <FontAwesomeIcon icon={faGoogle} className="size-4" />
              {t("bookableItemDetails.calendarSubscription.google")}
            </a>
            <a className={buttonVariants({ variant: "outline", className: "min-w-0 px-2" })} href={apps.other}>
              <CalendarIcon aria-hidden="true" />
              {t("bookableItemDetails.calendarSubscription.other")}
            </a>
          </div>
        </div>
        <div className="space-y-2">
          <label id={copyLabelId} htmlFor={fieldId} className="text-sm font-medium">
            {t("bookableItemDetails.calendarSubscription.copyPrompt")}
          </label>
          <InputGroup aria-labelledby={copyLabelId}>
            <InputGroupInput id={fieldId} readOnly value={subscriptionUrl} className="font-mono text-xs" />
            <InputGroupAddon align="inline-end">
              <InputGroupButton
                size="icon-xs"
                aria-label={t("bookableItemDetails.calendarSubscription.copy")}
                onClick={() => void copyLink()}
              >
                {copied ? <CheckIcon aria-hidden="true" /> : <CopyIcon aria-hidden="true" />}
              </InputGroupButton>
            </InputGroupAddon>
          </InputGroup>
          {copied ? (
            <p role="status" aria-live="polite">
              {t("bookableItemDetails.calendarSubscription.copied")}
            </p>
          ) : null}
          {clipboardError ? (
            <p role="alert" className="text-destructive">
              {t("bookableItemDetails.calendarSubscription.copyError")}
            </p>
          ) : null}
        </div>
      </div>
    );
  };

  const content = () => {
    if (subscriptionUrl !== null) return calendarLinks();
    if (status.isPending) {
      return (
        <p role="status" className="flex items-center gap-2 text-muted-foreground">
          <LoaderCircleIcon aria-hidden="true" className="animate-spin" />
          {t("bookableItemDetails.calendarSubscription.loading")}
        </p>
      );
    }
    if (status.isError || status.data === undefined) {
      return (
        <div className="space-y-3">
          <p role="alert">{t("bookableItemDetails.calendarSubscription.statusError")}</p>
          <Button type="button" variant="outline" onClick={() => void status.refetch()}>
            {t("bookableItemDetails.calendarSubscription.retry")}
          </Button>
        </div>
      );
    }
    if (createMutation.isError) {
      return (
        <div className="space-y-3">
          <p role="alert">{t("bookableItemDetails.calendarSubscription.generateError")}</p>
          <Button type="button" variant="outline" onClick={() => createMutation.mutate()}>
            {t("bookableItemDetails.calendarSubscription.retry")}
          </Button>
        </div>
      );
    }
    return (
      <p role="status" className="flex items-center gap-2 text-muted-foreground">
        <LoaderCircleIcon aria-hidden="true" className="animate-spin" />
        {t("bookableItemDetails.calendarSubscription.generating")}
      </p>
    );
  };

  return (
    <Popover
      open={open}
      onOpenChange={(nextOpen) => {
        if (nextOpen) {
          autoGenerateOnOpenRef.current = true;
          setOpen(true);
        } else close();
      }}
    >
      <PopoverTrigger
        render={<Button type="button" variant="outline" size="sm" className="shrink-0" />}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={popupId}
      >
        <CalendarPlusIcon aria-hidden="true" />
        {t("bookableItemDetails.calendarSubscription.trigger")}
      </PopoverTrigger>
      <PopoverContent
        id={popupId}
        role="dialog"
        aria-labelledby={headingId}
        aria-describedby={descriptionId}
        initialFocus={headingRef}
        align="end"
        collisionPadding={8}
        className="w-[min(24rem,calc(100vw-1rem))] max-w-[calc(100vw-1rem)] rounded-2xl"
      >
        <PopoverHeader className="relative pr-8">
          <h2 ref={headingRef} id={headingId} tabIndex={-1} className="text-base font-medium outline-none">
            {t("bookableItemDetails.calendarSubscription.title")}
          </h2>
          <PopoverDescription id={descriptionId}>
            {t("bookableItemDetails.calendarSubscription.description")}
          </PopoverDescription>
          <PopoverClose
            render={<Button type="button" variant="ghost" size="icon-sm" className="absolute top-0 right-0" />}
            aria-label={t("bookableItemDetails.calendarSubscription.close")}
          >
            <XIcon aria-hidden="true" />
          </PopoverClose>
        </PopoverHeader>
        {content()}
      </PopoverContent>
    </Popover>
  );
}
