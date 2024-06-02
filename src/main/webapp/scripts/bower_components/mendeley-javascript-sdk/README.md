# Mendeley JavaScript SDK

## About the SDK

The SDK provides a convenient library for accessing the Mendeley API with client-side JavaScript.


## Installation

Installation can be done with [bower][]:

    $ bower install mendeley-javascript-sdk

Or clone the git repository:

    $ git clone https://github.com/Mendeley/mendeley-javascript-sdk

The SDK is available as an AMD module or a standalone library. To use the standalone library add a link from your HTML page. It has a dependency on jquery which must be loaded first.

```html
<script src="//cdnjs.cloudflare.com/ajax/libs/jquery/1.10.1/jquery.min.js"></script>
<script src="/your/path/to/mendeley-javascript-sdk/dist/standalone.js"></script>
```

To use as an AMD module you'll need an AMD loader like [requirejs][] or [webpack][].

The only hard-dependency is jquery 1.10.1 or above (it may work with earlier versions but these are untested).

Some ECMAScript5 features are used so for older browsers you may need to shim these methods, for example with [es5-shim][].


## Registering a Client

To use the API you first need to register your application to get a client ID which you can use with OAuth2.

Go to [the Mendeley developers site][], sign-in with your Mendeley account details and click on "My Apps" and follow the instructions to register a new application.

## OAuth2 Flows

To begin a session you must set an authentication flow. This SDK includes code for the implict grant and auth code flows.

### Implicit Grant Flow

For purely client-side applications you can use the implicit grant flow which only requires a client id. To initiate the flow call:

```javascript
var options = { clientId: /* YOUR CLIENT ID */ };
var auth = MendeleySDK.Auth.implicitGrantFlow(options);
MendeleySDK.API.setAuthFlow(auth);
```

The options are:

- `clientId` - Your registered client ID. **Required**.
- `redirectUrl` - must match the redirect URL you used when registering the client. Defaults to the current URL.
- `accessTokenCookieName` - the name of the cookie to store the access token in. You should only change this if it clashes with another cookie you use. Defaults to `accessToken`.

The API internally will handle stale cookies by redirecting to the log-in page if any request fails with a status of 401 Unauthorized.

### Authorization Code Flow

For server applications you can use the authorization code flow. This requires server-to-server communication in order to acquire an access token. Implementing this depends on your language, framework etc. so isn't included in this SDK, but there is a nodejs example included (more info below).

The main difference is the server will do the token exchange and set the access token cookie. From the client-side point of view you start the flow like:

```javascript
var options = {
    apiAuthenticateUrl: '/login',
    refreshAccessTokenUrl: '/refresh-token'
};
var auth = MendeleySDK.Auth.authCodeFlow(options);
MendeleySDK.API.setAuthFlow(auth);
```

The options are:

- `apiAuthenticateUrl` - A URL on *your server* to redirect to when authentication fails. That URL should in turn redirect to the Mendeley OAuth endpoint passing the relevant credentials, as in this flow the client doesn't have any credentials. Required, defaults to `'/login'`.
- `refreshAccessTokenUrl` - A URL on *your server* that will attempt to refresh the current access token. Optional, defaults to false.
- `accessTokenCookieName` - the name of the cookie to store the access token in. You should only change this if it clashes with another cookie you use. Defaults to `accessToken`.

## Basic Usage

Once the OAuth flow is complete you can start grabbing data for the user. CORS is enabled by default for all clients so there's no need to do anything special to implement the cross-domain requests (unless you need to support browsers that don't have CORS).

Each API is exposed as a property of the SDK, for example `MendeleySDK.API.documents`, `MendeleySDK.API.folders`.

Methods that make API calls use [jquery deferred objects][] and return promises. Each call will either resolve with some data or reject with the original request and the API response. Here's an example using the standalone version:

```javascript
MendeleySDK.API.documents.list().done(function(docs) {

    console.log('Success!');
    console.log(docs);

}).fail(function(request, response) {

    console.log('Failed!');
    console.log('URL:', request.url);
    console.log('Status:', response.status);

});
```

Here's an example using [requirejs][]:

```javascript
define(function(require) {
    var api = require('mendeley-javascript-sdk/lib/api');
    var auth = require('mendeley-javascript-sdk/lib/auth');
    api.setAuthFlow(auth.authCodeFlow());

    api.documents.list().done(function() {

        console.log('Success!');
        console.log(docs);

    }).fail(function(request, response) {

        console.log('Failed!');
        console.log('URL:', request.url);
        console.log('Status:', response.status);

    });
});
```

## Logging API events

For logging API communication e.g. warning and error, you can attach a notifier that will send a message to a delegated logger function when a relevant event happens. If you want to limit the verbosity of the notifier just pass the minimum log level as the second parameter of the notifier creator.

The message structure is :

```javascript
{
    code: 'Unique numeric identification of the error ',
    level: 'Severity level of the message (error, warn, info, debug)'
    message: 'Textual explanation of the error',
    request : 'If available, the request who generated the event',
    response : 'If available, the response who generated the event'
}
```

Here's an example using the browser console as logger.

```javascript
define(function(require) {
    var api = require('mendeley-javascript-sdk/api');
    var auth = require('mendeley-javascript-sdk/auth');
    var notifier = require('mendeley-javascript-sdk/notifier');

    var logger = function(message) {
        console[message.level](message);
    };

    // notifier.createNotifier(<logger function>, <minimum log level>)
    var apiNotifier = notifier.createNotifier(logger, 'warn');

    api.setAuthFlow(auth.authCodeFlow(authSettings));
    api.setNotifier(apiNotifier);
});
```

## Examples

There are more examples in the `examples/` directory. To try the examples you will need [nodejs][] installed. *Note:* nodejs is not required to use this library, it is only used to serve the examples from a local URL you can use with OAuth2.

To run the examples you will need to [register your application][] to get a client ID (as described above). Use `http://localhost:8111/examples/` as the redirect URL.

The default example setup uses the implicit grant flow. To use this copy `examples/oauth-config.implicit-grant.js.dist` to `examples/oauth-config.js`, fill in your client ID, then run:

    $ npm install
    $ npm start

Go to http://localhost:8111/examples/ in your browser and you should be redirected to log-in to Mendeley. Once logged in you'll be redirected back to the examples.


### Example Using Authorization Code Flow

There is also some example nodejs code for using the authorization code flow.

To try out the authorization code flow copy `examples/oauth-config.auth-code.js.dist` to `examples/oauth-config.js`, filling in your client ID and secret.

To use this flow you will need to change your clients redirect URI to `http://localhost:8111/oauth/token-exchange` (or register a new one).


## Documentation

SDK documentation can be generated with:

    $ npm run build-jsdoc

This will be output to the `docs/` directory.

Further documentation on the API is available at http://dev.mendeley.com.

For an interactive console to the API visit https://api.mendeley.com/apidocs.


## Contributing

We would love to have your contributions, bug fixes and feature requests! You can raise an issue here, or ideally send us a pull request.

All contributions should be made by pull request (even if you have commit rights!).

In lieu of a formal styleguide, take care to maintain the existing coding style.

Please add unit tests for any new or changed functionality. Tests use karma and jasmine, run them with:

    $ npm test

If you make changes please check coverage reports under `/coverage` to make sure you haven't left any new code untested.

Please note the aim of this SDK is to connect to the existing Mendeley API, not to add to that API. For more information about the API and to give any feedback please visit [the Mendeley developers site].


[jquery deferred objects]:http://api.jquery.com/category/deferred-object/
[es5-shim]:https://github.com/es-shims/es5-shim
[requirejs]:http://requirejs.org
[webpack]:http://webpack.github.io
[the Mendeley developers site]:http://dev.mendeley.com
[register your application]:http://dev.mendeley.com
[nodejs]:http://nodejs.org
[bower]:http://bower.io
