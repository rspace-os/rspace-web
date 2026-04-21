// Take from https://github.com/GuptaSiddhant/react-broadcast-channel - MIT License

import * as React from "react";

export type BroadcastChannelData =
  | string
  | number
  | boolean
  | Record<string, unknown>
  | undefined
  | null;

/**
 * React hook to create and manage a Broadcast Channel across multiple browser windows.
 *
 * @param channelName Static name of channel used across the browser windows.
 * @param handleMessage Callback to handle the event generated when `message` is received.
 * @param handleMessageError [optional] Callback to handle the event generated when `error` is received.
 * @returns A function to send/post message on the channel.
 * @example
 * ```tsx
 * import {useBroadcastChannel} from 'react-broadcast-channel';
 *
 * function App () {
 *   const postUserIdMessage = useBroadcastChannel('userId', (e) => alert(e.data));
 *   return (<button onClick={() => postUserIdMessage('ABC123')}>Send UserId</button>);
 * }
 * ```
 * ---
 * Works in browser that support Broadcast Channel API natively. See [MDN](https://developer.mozilla.org/en-US/docs/Web/API/Broadcast_Channel_API#browser_compatibility).
 * To support other browsers, install and use [broadcastchannel-polyfill](https://www.npmjs.com/package/broadcastchannel-polyfill).
 */
export function useBroadcastChannel<T extends BroadcastChannelData = string>(
  channelName: string,
  handleMessage?: (event: MessageEvent) => void,
  handleMessageError?: (event: MessageEvent) => void,
): (data: T) => void {
  const [channel] = React.useState<BroadcastChannel | null>(
    typeof window !== "undefined" && "BroadcastChannel" in window
      ? new BroadcastChannel(channelName)
      : null,
  );

  useChannelEventListener(channel, "message", handleMessage);
  useChannelEventListener(channel, "messageerror", handleMessageError);

  return React.useCallback((data: T) => channel?.postMessage(data), [channel]);
}

/**
 * React hook to manage state across browser windows. Has the similar signature as `React.useState`.
 *
 * @param channelName Static name of channel used across the browser windows.
 * @param initialState Initial state.
 * @returns Tuple of state and setter for the state.
 * @example
 * ```tsx
 * import {useBroadcastState} from 'react-broadcast-channel';
 *
 * function App () {
 *   const [count, setCount] = useBroadcastState('count', 0);
 *   return (
 *     <div>
 *       <button onClick={() => setCount(prev => prev - 1)}>Decrement</button>
 *       <span>{count}</span>
 *       <button onClick={() => setCount(prev => prev + 1)}>Increment</button>
 *     </div>
 *   );
 * }
 * ```
 * ---
 * Works in browser that support Broadcast Channel API natively. See [MDN](https://developer.mozilla.org/en-US/docs/Web/API/Broadcast_Channel_API#browser_compatibility).
 * To support other browsers, install and use [broadcastchannel-polyfill](https://www.npmjs.com/package/broadcastchannel-polyfill).
 */
export function useBroadcastState<T extends BroadcastChannelData = string>(
  channelName: string,
  initialState: T,
): [T, React.Dispatch<React.SetStateAction<T>>, boolean] {
  const [isPending, startTransition] = React.useTransition();
  const [state, setState] = React.useState<T>(initialState);
  const broadcast = useBroadcastChannel<T>(channelName, (ev) =>
    setState(ev.data),
  );

  const updateState: React.Dispatch<React.SetStateAction<T>> =
    React.useCallback(
      (input) => {
        setState((prev) => {
          const newState = typeof input === "function" ? input(prev) : input;
          startTransition(() => broadcast(newState));
          return newState;
        });
      },
      [broadcast],
    );

  return [state, updateState, isPending];
}

// Helpers

/** Hook to subscribe/unsubscribe from channel events. */
function useChannelEventListener<K extends keyof BroadcastChannelEventMap>(
  channel: BroadcastChannel | null,
  event: K,
  handler?: (e: BroadcastChannelEventMap[K]) => void,
) {
  const callbackRef = React.useRef(handler);
  if (callbackRef.current !== handler) {
    callbackRef.current = handler;
  }

  React.useEffect(() => {
    const callback = callbackRef.current;
    if (!channel || !callback) {
      return;
    }

    channel.addEventListener(event, callback);
    return () => channel.removeEventListener(event, callback);
  }, [channel, event]);
}
