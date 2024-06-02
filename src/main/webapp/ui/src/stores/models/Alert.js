// @flow strict

import { type LinkableRecord } from "../definitions/LinkableRecord";
import { type Node } from "react";
import { take } from "../../util/iterators";

export type AlertVariant = "success" | "warning" | "error" | "notice";

/*
 * Global variable is necessary because we have to be sure that all alerts get
 * a unique id. Initially we tried using the time of creation in milliseconds
 * but there are times when two alerts get created in the same millisecond.
 * Being a primitive value and not being exported, not other code can depend
 * on this value so this isn't a huge concern, although do watch out when
 * writing jest tests.
 */
let id = 0;

type AlertDetails = {|
  title: string,
  variant: AlertVariant,
  record?: LinkableRecord,
  help?: string,
|};

type AlertParams = {|
  title?: ?string,
  message: string,
  variant?: AlertVariant,
  duration?: number,
  isInfinite?: boolean,
  actionLabel?: string,
  onActionClick?: () => void,
  details?: Array<AlertDetails>,
  retryFunction?: () => Promise<void>,
  allowClosing?: boolean,
  icon?: Node,
|};

export default class Alert {
  title: ?string;
  message: string;
  id: number;
  variant: AlertVariant;
  duration: number;
  isInfinite: boolean;
  isOpen: boolean;
  actionLabel: ?string;
  onActionClick: () => void;
  details: Array<AlertDetails>;
  detailsCount: number;
  retryFunction: ?() => Promise<void>;
  allowClosing: boolean;
  icon: ?Node;

  constructor({
    title,
    message,
    variant,
    duration,
    isInfinite,
    actionLabel,
    onActionClick,
    details,
    retryFunction,
    allowClosing,
    icon,
  }: AlertParams) {
    this.title = title;
    this.message = message;
    this.variant = variant ?? "notice";

    /*
     * Generally speaking, error messages should not auto dismiss. This is so
     * that it is not possible for a user to not notice them if they step away
     * from their computer whilst an action is being processed. Error messages
     * also typically include links to different parts of the UI and retry
     * buttons so keeping them around aids with resolving the issue. When
     * isInfinite is set, it overrides the value of the duration.
     */
    this.isInfinite = isInfinite ?? this.variant === "error";
    this.duration = duration ?? 4000;

    this.actionLabel = actionLabel ?? null;
    this.onActionClick = onActionClick ?? (() => {});
    this.id = id++;
    this.isOpen = true;
    this.details = [...take(details ?? [], 25)];
    this.detailsCount = details?.length ?? 0;
    this.retryFunction = retryFunction;
    this.allowClosing = allowClosing ?? true;
    this.icon = icon;
  }
}
