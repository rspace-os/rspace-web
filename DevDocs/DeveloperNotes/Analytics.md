# Analytics

`ui/src/components/Analytics.tsx` owns PostHog initialization through the pinned
`posthog-js` dependency. Its shared setup promise fetches analytics properties
once across React islands and initializes the configured provider once. Failed
setup leaves analytics unavailable and allows a later mount to retry.

In PostHog mode, `scripts/segment.js` queues legacy `RS.trackEvent` calls until a
React Analytics island initializes the bundled SDK. The bundle replaces that
queue with a capture bridge and replays pending events. The legacy script still
fetches its own analytics properties; it does not load the PostHog CDN SDK.

`WEB-INF/decorators/default.jsp` includes `common/header.jsp`, which always loads
the AppBar entrypoint wrapped in Analytics. JSP pages outside this decorator that
load segment.js without any Analytics island, including the public structured
document and standalone TinyMCE integration dialogs, only queue PostHog events.
They do not initialize PostHog. Segment configuration keeps the legacy loader.
