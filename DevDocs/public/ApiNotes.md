## RSpace API configuration and setup

### Introduction

 RSpace can be accessed through an API. The API is now fairly stable and we aim to keep 
 backwards compatibility.
 
### Access

To get set up to make an API call, a few conditions must be met:
1. Your sysadmin has set the `api.available` setting to `true` - this is the default
   value, so the API should be available unless your sysadmin has turned it off.
2. You have set up an API key in your RSpace profile page. Your API key is
   confidential and should not be disclosed publicly, as it gives limited access
   to your account. You can revoke or regenerate your key at any time. All API
   requests require setting an HTTP header `apiKey`.

### CORS headers

To allow API usage directly from 3rd party websites the API responses have
to contain appropriate CORS headers (e.g. 'Access-Control-Allow-Origin').

To enable cross-origin access set `api.permissiveCors.enabled` setting 
to `true`. Default is `false`, so the CORS access won't be allowed unless 
your sysadmin has turned it on.

### API usage limits

The rate of API calls you can make is currently limited, while we evaluate
the performance impact of the API. The default rates are as follows:

##### Default Limits per user

- Maximum 15 requests per 15 seconds.
- Maximum 1000 requests per hour.
- Maximum 5000 requests per day.

Please allow at least 100ms between requests.

##### Default Global limits

- Maximum 75 requests per 15 seconds.
- Minimum interval of 25ms between requests.

There is no default global limit on number of requests per day or per hour.

If these limits are exceeded, you will receive a 429 TOO\_MANY\_REQUESTS error response.

#### Setting custom usage limits:

You can set custom usage limits for your RSpace installation using the
following deployment properties. Any properties not set will remain as defaults.
 
- `api.user.limit.day` Maximum requests per user per day
- `api.user.limit.hour` Maximum requests per user per hour
- `api.user.limit.15s` Maximum requests per user per 15s
- `api.user.minInterval` Minimum interval between requests


- `api.global.limit.day` Maximum total requests per day
- `api.global.limit.hour` Maximum total requests per hour
- `api.global.limit.15s` Maximum total requests per 15s
- `api.global.minInterval` Minimum interval between requests

#### Setting file upload limits (RSpace 1.45 and later, using API v1.1 or later)

You can optionally choose to restrict the rate of file upload per user.
By default, there is no restriction, subject to the maximum size of an
individual file, configured by the `files.maxUploadSize` property.

`api.fileuploadRateLimit.enabled` `true/false`. Set to `true` to enable.
The limit properties below are ignored if this property is `false`.

`api.user.fileuploadRateLimit.hour`. Maximum file upload per user per hour,
in Mb. Default is 100 Mb.
`api.user.fileuploadRateLimit.day`. Maximum file upload per user per day,
in Mb. Default is 1000 Mb.

## Enabling beta API

Occasionally we introduce new API that we feel may be useful but may not be
fully stable yet. This is clearly documented as such, and is also disabled
by default. To use this API, set the property: `api.beta.enabled=true`
