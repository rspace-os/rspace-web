## Datetime handling in RSpace

All RSpace servers store datetimes in UTC, wherever they are deployed.
For users, we display their local time on a best effort basis. Here is a
description of the code to handle this:

When the user first logs in, we get a timezone request parameter collected
from Javascript on the login page. This is set as a session attribute, on
successful authentication, in class `TimezoneAdjusterImpl`, which
configures JSP engine to use the correct timezone. This saves having to 
re-query the timezone on every page load.

#### To make sure your display times in UI are in user timezone

- For JSPs, format dates using `<fmt:formatDate>` standard tag, or
  `<rs:relDate>`, which is our own tag to display relative dates. These
  will adjust automatically to user timezone.
- For objects that are returned via Ajax requests and marshalled by
  Spring's Json converter, provide
  a `getXXX()` method to return the timezone-corrected string. There are
  methods in `com.axiope.DateUtil` class to do this.
