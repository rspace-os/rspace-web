# Apps

## Overview

The Apps page provides a centralized interface for managing third-party
integrations within RSpace. It serves as the primary mechanism for users to
discover, configure, and authenticate with external services that extend
RSpace's capabilities. The system handles diverse integration patterns from
simple OAuth flows to complex multi-instance configurations.

The Apps interface organizes integrations into four distinct categories based on
their availability and configuration status, providing users with clear pathways
to enable and configure the services they need:

**Enabled Integrations**: Currently active and configured
- Ready for immediate use throughout RSpace
- User can modify settings or disable
- Credentials and configurations are validated and stored

**Disabled Integrations**: Available but not currently active
- System administrator has made them available
- User can configure and enable them
- Setup instructions and authentication flows are accessible

**Unavailable Integrations**: Require system administrator setup
- Not yet configured at the system level
- Show potential capabilities to encourage adoption

**External Integrations**: Third-party developed integrations
- Built by partners or external developers
- May have different support models
- Clearly distinguished from official ResearchSpace integrations

## Core Components

- **`App.tsx`** - Main application shell with four categorized sections; places cards by each integration's mode
- **`CardListing.tsx`** - Grid layout component that renders integration cards filtered by status
- **`IntegrationCard.tsx`** - Reusable card component with theming and dialog management
- **`queries.ts`** - TanStack Query hooks: the integrations query, each card's slice of it, and the mutations that keep it current
- **`useIntegrationsEndpoint.ts`** - Central API communication hub with type-safe serialization/deserialization
- **`useDisconnect.ts`** - Shared hook that DELETEs `{basePath}/connect` for apps whose credentials live server-side; resolves `false` if the deletion failed, so callers do not report a disconnection that did not happen
- **Individual service hooks** (e.g., `useFigshare.tsx`) - Service-specific API wrappers

Each integration is implemented as a standalone component in the `/integrations` directory:

- **Simple Toggle** (e.g., `Chemistry.tsx`) - Basic enable/disable functionality
- **OAuth-based** (e.g., `Clustermarket.tsx`, `ProtocolsIO.tsx`) - Handle redirect flows and token management
- **API Key/Credential-based** (e.g., `NextCloud.tsx`, `OwnCloud.tsx`) - Form-based authentication
- **Complex Configuration** (e.g., `GitHub.tsx`, `Slack.tsx`, `Dataverse.tsx`) - Multi-instance management

## Integration Architecture

### Type-Safe State Management

The system uses a sophisticated type system to ensure integration state consistency:

```typescript
IntegrationState<CredentialType>
```

Where `CredentialType` varies by integration complexity:
- Simple: `{}`
- OAuth: `{ ACCESS_TOKEN: Optional<string> }`
- Multi-instance: `Array<Optional<ConfigurationObject>>`

All integration states pass through encoder/decoder functions that:
- **Decode**: Convert API JSON responses to type-safe TypeScript objects
- **Encode**: Convert TypeScript state back to API-compatible JSON
- **Validate**: Use `Optional` types to handle parsing failures gracefully

Integration states are server state, held by TanStack Query (`queries.ts`):
- **One query**: `allIntegrationsQueryOptions` fetches `/allIntegrations` once,
  under `integrationsQueryKeys.all`. Its `staleTime` is infinite, so cards that
  remount when they move between sections do not refetch.
- **Per-card selection**: each card calls `useIntegrationState("NAME")`, which
  selects its own integration from the query, and `App` selects only the modes.
  A change to one integration therefore re-renders only that integration's card.
- **Mutations**: `useUpdateIntegrationMutation` (`/update`),
  `useSaveAppOptionsMutation` (`/saveAppOptions`) and
  `useDeleteAppOptionsMutation` (`/deleteAppOptions`) write their result into
  the query; `useIntegrationModeUpdate` enables or disables an integration. A
  result is written only when no other change to the same integration has
  been written since its request began; otherwise it may be older than what
  the cache holds, so it is discarded and the integrations are refetched once
  the last overlapping change settles. A response can never overwrite newer
  data.
- **Changes made elsewhere**: `useRecordIntegrationChange` and
  `useTokenConnection` record what the server reports outside these endpoints,
  such as an OAuth window broadcasting that it has connected, through the same
  ordering.
- **Forms**: dialogs that edit fields use TanStack Form, validated by a Valibot
  schema. Each saved configuration has its own form, keyed by its `optionsId`,
  whose default values come from the query; a field the user has not touched
  follows the server, and a touched one keeps the user's edit until it is saved.
- **Local state**: only transient UI state stays in components, such as open
  dialogs and menus, a configuration being added, or requests still pending.

Card tests render a card inside a fresh `QueryClient` seeded with its
integration's state (see `__tests__/integrationsQueryClient.tsx`) and mock the
requests, rather than passing callbacks.

## Authentication Patterns

### OAuth Flow Integration

OAuth integrations follow a standardized pattern:

1. **Connection Button**: Triggers OAuth flow in new window
2. **External Authentication**: User authenticates on service's website
3. **Token Exchange**: Service redirects back with authorization code
4. **Token Storage**: Access token is stored and encrypted server-side

### API Key/Credential Management

Credential-based integrations provide:
- **Secure Forms**: Input fields for API keys, usernames, passwords
- **Validation**: Real-time credential verification where possible

### Multi-Instance Configuration

Complex integrations support multiple configurations:
- **Dynamic UI**: Add/remove configuration instances
- **Individual Authentication**: Each instance can have separate credentials

## Service Branding and Theming

Each integration card is themed according to the branding of the service it
represents, creating a cohesive and recognizable interface. The theming system
dynamically applies service-specific colours while maintaining accessibility
standards and design consistency, and is used not just on the apps page cards
but also by the dialogs and other UI elements across the rest of the product
where the integraton provides functionality.

### Brand Asset Structure

Service branding assets are organized in `/src/assets/branding/[service-name]/`:

```
/src/assets/branding/
├── github/
│   ├── index.ts                 # HSL colour definitions
│   └── logo.svg                 # Optimized service logo
├── slack/
│   ├── index.ts
│   └── logo.svg
├── figshare/
│   ├── index.ts
│   └── logo.svg
└── [Other services...]
```

### Colour System

Each service exports an HSL colour object that defines the primary brand colour:

```typescript
export const LOGO_COLOR = {
  hue: 298,        // Hue value (0-360)
  saturation: 56,  // Saturation percentage
  lightness: 19,   // Lightness percentage
};
```

The hue should be the dominant colour of the logo, with saturation and lightness
adjusted to ensure good contrast and visibility. The theming system automatically
generates complementary colours for backgrounds, borders, and text based on this
primary colour.

### Logo Guidelines

Logos follow design standards:
- **SVG format only** for scalability and performance
- **Consistent dimensions** matching existing assets. Currently, logos are exported from Illustrator on a 100x100px artboard. `Object > Expand` and `Type > Create Outlines` are used to ensure logo renders and scales properly.
- **Brand-coloured background** while ensuring the logo itself has good contrast with its background; usually, this means using a fully white version of the logo.

### Accessibility Compliance

The theming system automatically ensures accessibility:
- **High contrast mode support** with adjusted colour schemes
- **WCAG 2.1 AA compliance** for text contrast ratios
- **Automatic lightness reduction** for text colours
- **Responsive colour adaptation** based on user preferences

## Adding New Integrations

The system provides a comprehensive framework for adding new integrations. See
[`AddingANewIntegration.md`](./AddingANewIntegration.md) for detailed
implementation instructions covering:

1. **Type Definitions**: Declaring credential types and state shapes
2. **API Integration**: Implementing encoder/decoder functions
3. **UI Components**: Creating card and dialog interfaces
4. **Authentication Flows**: Handling OAuth, credentials, or complex configurations
5. **Testing**: Unit testing integration logic

## File Organization

```
/src/eln/apps/
├── index.tsx                    # Application entry point
├── App.tsx                      # Main application component
├── CardListing.tsx              # Cards, filtered by status
├── IntegrationCard.tsx          # Reusable card component
├── queries.ts                   # Integrations query and mutations
├── useIntegrationsEndpoint.ts   # Central API management
├── use[Service].tsx             # Individual service hooks
├── AddingANewIntegration.md     # Developer documentation
└── integrations/                # Individual integration components
    ├── ApiDirect.tsx
    ├── Clustermarket.tsx
    ├── GitHub.tsx
    └── [Other services...]
```

## Accessibility and User Experience

The Apps system prioritizes accessibility and user experience:

- **High Contrast Support**: All colour schemes adapt to system preferences
- **Keyboard Navigation**: Full keyboard accessibility throughout
- **Screen Reader Support**: Semantic HTML and ARIA labels
- **Responsive Design**: Optimized for desktop, tablet, and mobile devices
- **Clear Status Indicators**: Visual and textual state communication
- **Error Handling**: Graceful degradation with helpful error messages

All future changes should maintain these standards to ensure inclusivity.
