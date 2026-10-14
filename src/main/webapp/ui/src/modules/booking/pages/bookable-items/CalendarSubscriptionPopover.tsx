import { faApple } from "@fortawesome/free-brands-svg-icons/faApple";
import { faGoogle } from "@fortawesome/free-brands-svg-icons/faGoogle";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarIcon, CalendarPlusIcon, CheckIcon, CopyIcon, LoaderCircleIcon, XIcon } from "lucide-react";
import { useCallback, useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { ApiV2ProblemError } from "@/modules/booking/domain/booking";
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
import { Skeleton } from "@/modules/common/ui/skeleton";
import {
  calendarApplicationUrls,
  calendarSubscriptionQueryKey,
  createCalendarSubscription,
  fetchCalendarSubscriptionStatus,
  itemCalendarLinksQueryKey,
  revokeCalendarSubscription,
  rotateCalendarSubscription,
} from "./bookableItemCalendarSubscription";

export function CalendarSubscriptionPopover({
  configurationId,
  token,
  archived = false,
}: {
  configurationId: number;
  token: string;
  archived?: boolean;
}) {
  const { t } = useTranslation(["booking", "common"]);
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const openRef = useRef(open);
  openRef.current = open;
  const [clipboardError, setClipboardError] = useState(false);
  const [linkFeedback, setLinkFeedback] = useState<"replaced" | "disconnected" | "copied" | null>(null);
  const [focusGoogle, setFocusGoogle] = useState(false);
  // Both break calendars that use the current link, so each is confirmed first.
  const [confirming, setConfirming] = useState<"replace" | "disconnect" | null>(null);
  const popupId = `calendar-subscription-${useId()}`;
  const headingId = `${popupId}-heading`;
  const descriptionId = `${popupId}-description`;
  const fieldId = `${popupId}-url`;
  const copyLabelId = `${fieldId}-label`;
  const autoGenerateOnOpenRef = useRef(false);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const googleLinkRef = useRef<HTMLAnchorElement>(null);
  const focusCreateRef = useRef(false);
  const focusRetryRef = useRef(false);
  const focusOriginRef = useRef<Element | null>(null);
  const createButtonRef = useRef<HTMLButtonElement>(null);
  const retryButtonRef = useRef<HTMLButtonElement>(null);

  const queryKey = calendarSubscriptionQueryKey(configurationId);
  const status = useQuery({
    queryKey,
    queryFn: ({ signal }) => fetchCalendarSubscriptionStatus(configurationId, token, signal),
    enabled: open,
    retry: false,
  });

  const resetCopy = () => {
    setClipboardError(false);
  };
  const linksChanged = () => void queryClient.invalidateQueries({ queryKey: itemCalendarLinksQueryKey });

  const createMutation = useMutation({
    mutationFn: () => createCalendarSubscription(configurationId, token),
    retry: false,
    onMutate: () => {
      resetCopy();
      setLinkFeedback(null);
      // A fresh link supersedes any earlier replace conflict.
      rotateMutation.reset();
      focusCreateRef.current = false;
      focusRetryRef.current = false;
      focusOriginRef.current = document.activeElement;
    },
    onSuccess: async (created) => {
      await queryClient.cancelQueries({ queryKey, exact: true });
      queryClient.setQueryData(queryKey, created);
      linksChanged();
      if (openRef.current) setFocusGoogle(true);
    },
  });
  const rotateMutation = useMutation({
    mutationFn: (etag: string) => rotateCalendarSubscription(configurationId, token, etag),
    retry: false,
    onMutate: () => {
      resetCopy();
      setLinkFeedback(null);
      focusCreateRef.current = false;
      focusRetryRef.current = false;
      focusOriginRef.current = document.activeElement;
    },
    onSuccess: async (rotated) => {
      await queryClient.cancelQueries({ queryKey, exact: true });
      queryClient.setQueryData(queryKey, rotated);
      linksChanged();
      if (!openRef.current) return;
      setConfirming(null);
      setLinkFeedback("replaced");
      setFocusGoogle(true);
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.status === 409) {
        const refreshed = await status.refetch();
        if (!openRef.current) return;
        setConfirming(null);
        if (refreshed.isSuccess && refreshed.data.subscriptionUrl === null) {
          setLinkFeedback("disconnected");
          focusCreateRef.current = true;
        } else if (
          refreshed.isSuccess
            ? refreshed.data.subscriptionUrl !== null
            : status.data !== undefined && status.data.subscriptionUrl !== null
        ) {
          setFocusGoogle(true);
        } else {
          focusRetryRef.current = true;
        }
        return;
      }
      if (!openRef.current) return;
      setConfirming(null);
      setFocusGoogle(true);
    },
  });
  const revokeMutation = useMutation({
    mutationFn: () => revokeCalendarSubscription(configurationId, token),
    retry: false,
    onMutate: () => {
      resetCopy();
      setLinkFeedback(null);
      focusCreateRef.current = false;
      focusRetryRef.current = false;
      focusOriginRef.current = document.activeElement;
    },
    onSuccess: async () => {
      await queryClient.cancelQueries({ queryKey, exact: true });
      linksChanged();
      const refreshed = await status.refetch();
      if (!openRef.current) return;
      if (refreshed.isSuccess && refreshed.data.subscriptionUrl === null) {
        setConfirming(null);
        setLinkFeedback("disconnected");
        focusCreateRef.current = true;
      } else if (
        refreshed.isSuccess
          ? refreshed.data.subscriptionUrl !== null
          : status.data !== undefined && status.data.subscriptionUrl !== null
      ) {
        setConfirming(null);
        setFocusGoogle(true);
      } else {
        setConfirming(null);
        focusRetryRef.current = true;
      }
    },
    onError: () => {
      if (!openRef.current) return;
      setConfirming(null);
      setFocusGoogle(true);
    },
  });
  const changing = createMutation.isPending || rotateMutation.isPending || revokeMutation.isPending;

  const subscriptionUrl = status.data?.subscriptionUrl ?? null;

  useEffect(() => {
    if (!open || changing) return;
    const target =
      focusCreateRef.current && subscriptionUrl === null && status.isSuccess
        ? createButtonRef.current
        : focusRetryRef.current && (status.isError || createMutation.isError)
          ? retryButtonRef.current
          : null;
    if (!target) return;
    const active = document.activeElement;
    if (
      !focusOriginRef.current ||
      active === focusOriginRef.current ||
      active === headingRef.current ||
      active === document.body
    )
      target.focus();
    focusCreateRef.current = false;
    focusRetryRef.current = false;
    focusOriginRef.current = null;
  }, [changing, createMutation.isError, open, status.data, status.isError, status.isSuccess, subscriptionUrl]);

  const createSubscription = useCallback(() => {
    if (status.data?.subscriptionUrl === null) createMutation.mutate();
  }, [createMutation, status.data]);

  useEffect(() => {
    if (archived || !open || !autoGenerateOnOpenRef.current || status.isFetching || !status.isSuccess) return;
    autoGenerateOnOpenRef.current = false;
    createSubscription();
  }, [archived, createSubscription, open, status.isFetching, status.isSuccess]);

  const focusGoogleAction = useCallback(
    (element: HTMLAnchorElement | null) => {
      googleLinkRef.current = element;
      if (!open || !focusGoogle || !element) return;
      const active = document.activeElement;
      if (
        !focusOriginRef.current ||
        active === focusOriginRef.current ||
        active === headingRef.current ||
        active === document.body
      )
        element.focus();
      focusOriginRef.current = null;
      setFocusGoogle(false);
    },
    [open, focusGoogle],
  );

  const close = () => {
    autoGenerateOnOpenRef.current = false;
    openRef.current = false;
    setOpen(false);
    setFocusGoogle(false);
    focusCreateRef.current = false;
    focusRetryRef.current = false;
    focusOriginRef.current = null;
    setLinkFeedback(null);
    setConfirming(null);
    resetCopy();
    rotateMutation.reset();
    revokeMutation.reset();
  };

  const copyLink = async () => {
    if (subscriptionUrl === null) return;
    setClipboardError(false);
    setLinkFeedback(null);
    try {
      await navigator.clipboard.writeText(subscriptionUrl);
      setLinkFeedback("copied");
    } catch {
      setClipboardError(true);
      setLinkFeedback(null);
    }
  };

  const calendarLinks = () => {
    if (subscriptionUrl === null) return null;
    const apps = calendarApplicationUrls(subscriptionUrl);
    return (
      <div className="space-y-5">
        <div className="space-y-2">
          <p className="text-sm font-medium">{t("bookableItemDetails.calendarSubscription.appPrompt")}</p>
          <div className="flex flex-wrap gap-2">
            <a className={buttonVariants({ variant: "outline", className: "min-w-0 px-2" })} href={apps.apple}>
              <FontAwesomeIcon icon={faApple} className="size-4" />
              {t("bookableItemDetails.calendarSubscription.apple")}
            </a>
            <a
              ref={focusGoogleAction}
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
                {linkFeedback === "copied" ? <CheckIcon aria-hidden="true" /> : <CopyIcon aria-hidden="true" />}
              </InputGroupButton>
            </InputGroupAddon>
          </InputGroup>
        </div>
        {manageLink()}
      </div>
    );
  };

  const manageLink = () => {
    const rotateConflict = rotateMutation.error instanceof ApiV2ProblemError && rotateMutation.error.status === 409;
    return (
      <div className="space-y-2 border-t pt-4">
        {confirming ? (
          <div className="space-y-2">
            <p className="text-sm">
              {t(
                confirming === "replace"
                  ? "bookableItemDetails.calendarSubscription.replaceWarning"
                  : "bookableItemDetails.calendarSubscription.disconnectWarning",
              )}
            </p>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="destructive"
                size="sm"
                disabled={changing || status.data === undefined}
                aria-busy={rotateMutation.isPending || revokeMutation.isPending}
                onClick={() =>
                  confirming === "replace"
                    ? status.data && rotateMutation.mutate(status.data.etag)
                    : revokeMutation.mutate()
                }
              >
                {t(
                  confirming === "replace"
                    ? "bookableItemDetails.calendarSubscription.replaceConfirm"
                    : "bookableItemDetails.calendarSubscription.disconnectConfirm",
                )}
              </Button>
              <Button type="button" variant="outline" size="sm" onClick={() => setConfirming(null)}>
                {t("common:actions.cancel")}
              </Button>
            </div>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={changing}
              onClick={() => {
                rotateMutation.reset();
                setConfirming("replace");
              }}
            >
              {t("bookableItemDetails.calendarSubscription.replace")}
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              disabled={changing}
              onClick={() => {
                revokeMutation.reset();
                setConfirming("disconnect");
              }}
            >
              {t("bookableItemDetails.calendarSubscription.disconnect")}
            </Button>
          </div>
        )}
        {rotateConflict ? (
          <p role="alert" className="text-sm">
            {t("bookableItemDetails.calendarSubscription.replaceConflict")}
          </p>
        ) : rotateMutation.isError || revokeMutation.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t("bookableItemDetails.calendarSubscription.changeError")}
          </p>
        ) : null}
      </div>
    );
  };

  const content = () => {
    if (subscriptionUrl !== null) return calendarLinks();
    if (status.isPending) {
      return (
        <div aria-busy="true" className="space-y-3">
          <p role="status" className="sr-only">
            {t("bookableItemDetails.calendarSubscription.loading")}
          </p>
          <Skeleton aria-hidden="true" className="h-9 w-full" />
          <Skeleton aria-hidden="true" className="h-9 w-full" />
        </div>
      );
    }
    if (status.isError || status.data === undefined) {
      return (
        <div className="space-y-3">
          <p role="alert">{t("bookableItemDetails.calendarSubscription.statusError")}</p>
          <Button
            ref={retryButtonRef}
            type="button"
            variant="outline"
            disabled={status.isFetching}
            onClick={() => void status.refetch()}
          >
            {t("bookableItemDetails.calendarSubscription.retry")}
          </Button>
        </div>
      );
    }
    if (archived) {
      return (
        <p role="status" className="text-muted-foreground">
          {t("bookableItemDetails.calendarSubscription.archivedUnavailable")}
        </p>
      );
    }
    if (createMutation.isError) {
      return (
        <div className="space-y-3">
          <p role="alert">{t("bookableItemDetails.calendarSubscription.generateError")}</p>
          <Button ref={retryButtonRef} type="button" variant="outline" disabled={changing} onClick={createSubscription}>
            {t("bookableItemDetails.calendarSubscription.retry")}
          </Button>
        </div>
      );
    }
    // No link after a disconnect, or after a replace found it removed elsewhere: offer to add it again.
    if ((revokeMutation.isSuccess || rotateMutation.isError) && !createMutation.isPending) {
      return (
        <div className="space-y-3">
          <Button
            ref={createButtonRef}
            type="button"
            variant="outline"
            disabled={changing}
            onClick={createSubscription}
          >
            {t("bookableItemDetails.calendarSubscription.trigger")}
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
          autoGenerateOnOpenRef.current = !archived;
          openRef.current = true;
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
        initialFocus={() => googleLinkRef.current ?? headingRef.current}
        align="end"
        collisionPadding={8}
        className="w-[min(24rem,calc(100vw-1rem))] max-w-[calc(100vw-1rem)] rounded-sm"
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
        <p role="status" className="sr-only">
          {linkFeedback === "replaced"
            ? t("bookableItemDetails.calendarSubscription.replaced")
            : linkFeedback === "copied"
              ? t("bookableItemDetails.calendarSubscription.copied")
              : linkFeedback === "disconnected"
                ? t("bookableItemDetails.calendarSubscription.disconnected")
                : null}
        </p>
        {content()}
        <p role="alert" className={clipboardError ? "text-sm text-destructive" : "sr-only"}>
          {clipboardError ? t("bookableItemDetails.calendarSubscription.copyError") : null}
        </p>
      </PopoverContent>
    </Popover>
  );
}
