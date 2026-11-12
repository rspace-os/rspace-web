# Analytics

`ui/src/components/Analytics.tsx` owns setup for both PostHog and Segment.
Its shared setup promise fetches `/session/ajax/analyticsProperties` once across
React islands and initializes the configured provider once per page. Failed
setup leaves analytics unavailable and allows a later mount to retry.
PostHog uses the pinned, bundled `posthog-js` dependency. Segment loads its
analytics.js script and configures Intercom, identifies the user, and records
the page as before.

Legacy code calls `RS.trackEvent(name, properties)` from `scripts/global.js`.
It forwards events to `window.analytics.track` when available. Otherwise it
creates a queue and stores events until React Analytics initializes. PostHog
setup replaces the queue with a capture bridge and replays pending events.
Segment setup saves the queued events, removes the legacy object stub so the
Segment snippet can install its array queue, then replays events through
Segment's `track`. Existing Segment snippets and loaded SDKs retain their
normal behavior.

`global.js` also sets `window.currentUser` to an empty string if it is undefined.
The global ready handler checks `currentUser` before initializing. This shim
preserves initialization on the public document and plugin dialog pages, which
do not declare the `currentUser` variable supplied by `default.jsp`.

`WEB-INF/decorators/default.jsp` includes `common/header.jsp`, which loads the
AppBar entrypoint wrapped in Analytics. The public structured document page
loads `exportModal`, whose entrypoint wraps both its initial closed export UI and its
opened dialog in Analytics. It also
loads `PreviewInfo`, which wraps stoichiometry previews in Analytics when those
previews render. The Omero, Clustermarket, and Galaxy TinyMCE tracking calls live
in each `plugin.min.js` and run in the editor page, where Analytics initializes,
rather than in the dialog iframe.

HelpDocs fetches `/session/ajax/livechatProperties` independently to configure
help and live chat.
