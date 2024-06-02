# Adding a new Integration

This document outlines the steps necessary to add a new integration to the new
apps page.

## Different types of Integrations

The integrations vary quite significantly in how complex their configuration
options are, and this affects what changes are necessary to the code in this
directory.

### No Authentication
These are the simplest integrations, where the user simply enables the
integration. In some cases this is where there is simply no authentication
necessary, or in other cases where the authentication is only performed when the
user goes to use the integration.

### OAuth
Many of the integrations that do require authentication use OAuth to connect
RSpace to the service. In this case, the UI typically includes just a single
button to trigger the OAuth flow which when clicked redirects the user to a page
on the service's own site for entering their credentials. RSpace is then handed
a code which can be used to request an access token.

### Simple Authentication e.g. API key
A few integrations use a different method of authentication wherein the user
must provide a few pieces of information, typically either an API key or a
username and password. This piece of data is then stored and used to make
requests on the user behalf when they use the integration. The UI will
therefore contain a number of text fields and a save button.

### Complex Configuration
A few of the integrations require a complex set of configurations. This is
typically where the user may connect to a number of instances of the service, with
the user able to provide as many or as few configurations as they wish. These
are more complex as they require implementing a more complex UI and call a
different set of APIs for saving and deleting those configurations.

## Steps

### 1. API calls

#### 1.1 Flow type

In each case, the first step is to declare the type of the credentials. The file
[./useIntegrationsEndpoint.js](useIntegrationsEndpoint.js) exports a custom
react hook that performs all of the network calls. As part of this, it
deserialises and serialises the JSON into a shape that can be type checked by
Flow. By converting the data into a new shape, we're able to leverage the
capabilities of the type checker, make impossible states impossible (such as an
integration being both enabled and unavailable), and isolate the UI code from
API changes that may be undertaken in the future.

Therefore, the first step is to describe the shape of the credential data by
appending to the `IntegrationStates` type. Each integration's state is described
by the `IntegrationState` (note: singular) type, which is parameterised by the
type of the credential data.

As such, the type of the state of an integration which has no authentication
requirement would be `IntegrationState<{||}>`: the type of the credentials is
the empty object.

The type of the state of OAuth-based authentication will be something like
```
IntegrationState<{|
  ACCESS_TOKEN: Optional<string>,
|}>
```
where `ACCESS_TOKEN` is the token that has been the result of the OAuth flow.
For integrations with credentials like API keys, the type will be essentially
the same, with `ACCESS_TOKEN` replaced with the other details.

For the most complex integrations with several configurations, in place of an
object the type of the credentials will be an array. As an example, this is the
type of the state of the GitHub integration:
```
IntegrationState<
  Array<
    Optional<{|
      GITHUB_ACCESS_TOKEN: Optional<string>,
      GITHUB_REPOSITORY_FULL_NAME: string,
      optionsId: OptionsId,
    |}>
  >
>
```

Note that each credential/configuration option is wrapped in the `Optional`
type. This is because the decoder could fail to find the respective property in
the JSON returned by the API or it could be the wrong type. So lets get into
that next.

#### 1.2 Decoder

Now that the type of the integrations state has been declared, the next step is
to map the JSON object returned by the `/allIntegrations` endpoint to this type.
We're effectively writing a simple parser, and because any parser can fail all
of the outputs use the `Optional` type. To implement a decoder for your new
integration, append to the `decodeIntegrationStates` function and in doing so
defining a function that acts as parser for this new integration's state.

For integrations without authentication, this is a straight copy-paste job: you
want something like this
```
$NAME: { mode: parseState(data.$Name, credentials: {} },
```

For OAuth-based authentication and simple other credentials this isn't too much
more complex. There are helper functions, `parseCredentialString` and
`parseCredentialBoolean` to aid with this process.
```
$NAME: {
  mode: parseState(data.$NAME),
  credentials: {
    ACCESS_TOKEN: parseCredentialString(data.$NAME.options, "ACCESS_TOKEN"),
  },
},
```

For more complex integrations with multiple configurations, this gets more
complex. In each case, the nested objects and arrays need to be checked that
they are present and of the right type. Where they are not `Optional.empty`
should be returned. There is also the `optionsId` value which is encoded in
the API responses as the key of the JSON object. Each function is slightly
different, so best to look at the existing examples.

In addition to call the new decode function in `decodeIntegrationStates`,
be sure to call it in `saveAppOptions` too which refreshes the UI after
editing the more complex configuration options.

#### 1.3 Encoder

The encoder is the inverse of the decoder. This is the function that takes the
state of the integration, with any changes the user has made, and turns it back
into the JSON payload. To implement an encoder for your new integration, append
to the `encodeIntegrationState` function.

For integrations without authentication, this is a simple process of mapping the
`mode` property of the `IntegraionState` object to the `available` and
`enabled`. There is no other data to map because, recall, the credential type is
the empty object.
```
  if (integration === "$NAME") {
    return {
      name: "$NAME",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      options: {},
    };
  }
```

For OAuth and simple authenticated integrations, the `options` object must also
be populated with the credential data. Assuming there is just one piece of data
this just a case of reversing the decoding:
```
if (integration === "$NAME") {
    return {
      name: "$NAME",
      available: data.mode !== "UNAVAILABLE",
      enabled: data.mode === "ENABLED",
      // $FlowExpectedError[prop-missing]
      // $FlowExpectedError[incompatible-type]
      // $FlowExpectedError[incompatible-use]
      options: data.credentials.ACCESS_TOKEN.map((token) => ({
        ACCESS_TOKEN: token,
      })).orElse({}),
    };
  }
```

The Flow suppression are unfortunate, but do not result in any weakening of the
type safety. Despite the conditional logic selecting each integration in turn,
Flow remains unconvinced that `data.credentials` has the refined type. However,
because the `options` object will simply be serialised into a JSON string and
passed to the API it shouldn't be possible for any bug to be present that would
have been caught by Flow.

Once more, the more complex integrations are, well, more complex. Each
configuration needs to be encoded into the `options` object using
`Object.fromEntries` and `mapOptional`, with the `optionsId` value be used as
the key of the array of objects. Again, best to just work from the existing
examples.

### 2. UI

With all that glue code done, the remainder is the UI. Each integration is
rendered by the new apps page as a card. When the integration has been made
available by the sysadmin, tapping this card will open a dialog allowing the
user to enable/disable the integration and provide any
configuration/authentication details.

#### 2.1 Card component

Each integration's card and dialog is implemented as a react component in the
[./integrations](integrations) directory, each of which calls `IntegrationCard`
component. Simply implement a new component that behaves much the same, passing
props like name, explanatoryText, and color. An SVG icon needs to be obtained,
and modified to match the same shape and dimensions of the other icons. The
passed colour should then match the icon where the icon has a coloured
background. If the logo has a white background then choose the most common
colour.

Once defined, add the new component to the `./CardListing` component where it
will then appear in the UI in the enabled, disabled, or unavailable listing
depending upon its current state.

#### 2.2 Dialog content

One of `IntegrationCard` props is `setupSection`. This is a react component
that is, well, the content of the dialog beneath the subheading "Setup". Here,
instructions on how to setup the integration and where to find it in the rest of
the product should be provided as well as any forms for configuring and authenticating.
Standard HTML `<ol>` and `<li>` tags have been styled to match the rest of
the dialog, but for other text use `<Typograph variant="body2">` from MUI.

For OAuth-based integrations, this is when a "Connect" button needs to be added
that triggers the opening of a new window for the user to authenticate. For
examples, take a look at [Clustermarket](integrations/Clustermarket.js) or
[protocols.io](integrations/ProtocolsIO.js). Those files provide an explanation
of how the authentication flow works and respective changes that need to be made
to the pages that RSpace renders after the user has authenticated.

For the more complex configurations, this `setupSection` prop is where they
need to provide all of the UI to enter all of these options. Each is unique, but
they typically abstract out this code into a separate component, partially for
code clarity and partially so that the Optional wrapper around each
configuration option can be handled separately with `OptionalArray.all`. For
examples of how these dialogs are implemented, take a look at
[GitHub](integrations/GitHub.js), [Slack](integrations/Slack.js), or
[Dataverse](integrations/Dataverse.js). One thing to be aware of about these
dialogs is that they bring in the `useIntegrationsEndpoint` custom hook, as
opposed to the other integration dialogs which just use the passed in `update`
prop to make API calls. This is because whilst `update` needs to be passed down
so that the card moves to the other listings when enabled/disabled, these
components call `/saveAppOptions` and `/deleteAppOptions` directly and update
the state themselves.

#### A note on mobx

When [./App.js](App.js) calls `useIntegrationsEndpoint`, it wraps the resulting
data in both a `FetchingData.Fetched` wrapper and a `mobx` observable one. The
former isn't significant when adding new integrations -- only something to pay
attention to when making changes to the whole page -- but the use of mobx is
important. It is this that means that when the components mutate the current
state of the integrations, react re-renders with the updated state. For example,
when a configuration option is deleted, the object in the current state is
discarded by calling `splice` inside of a call to `runInAction`. This then
triggers a minimal re-render to remove it from the UI. Whenever state is being
mutated by an integration's component, be sure to check that `runInAction` is
being called correctly otherwise the UI will be stale until some other operation
updates things. Similarly, be sure to check that components that need to update
when such state is mutated are wrappped in `observer` from `mobx-react-lite`.

### 3. Unit test

Finally, you may wish to add a unit test in
[./intergations/\_\_tests\_\_](integrations/__tests__) to cover the new component
and the other logic. Not all components are thoroughly tested, but the more
complex the UI inside of the dialog and the more complex the data model, the more
worthwhile testing this code is likely to be.
