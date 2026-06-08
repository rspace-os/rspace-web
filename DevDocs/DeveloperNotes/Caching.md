# Caching

Caching is used in various contexts within RSpace to reduce the load on the server,
and the database, to improve the performance for the user.

## Hibernate 2nd level cache

This is used to cache database entities outside of individual transactions. RSpace
uses [Ehcache 3](https://www.ehcache.org/) via JCache for the implementation.

Entities that are read more than written to are good candidates for caching.
To add to the cache, use Hibernate `@Cache` annotation, and configure the caching
policy in `ehcache.xml` (Ehcache 3 format).

## Spring Cache abstraction

Spring cache is used for:
- caching objects that are not database entities.
- when we want to cache in the service or controller layer.
- when we want more control over caching behaviour.
Objects to be cached are also configured in `ehcache.xml`.

## Http Request caching

Caching of static resources can also be achieved by setting the cache headers,
see `@BrowserCacheAdvice`.

## In-browser caching

If there is little risk the server response changes during user's activity on a
page, then you can just save the response in js context. For requests that should
be only stored for a short period of time, you can use JavaScript cache defined
in `global.js` (this is useful for short-term caching of HTML fragments within a
single page, e.g. when browsing through paged listings). 

Finally, for storing objects between page requests you can use html5 session/local
storage, but __only__ for non-sensitive data (always have a shared-computer
scenario in mind).

### Static resources caching

Static resource caching is tricky - in most cases we want things like `js/css`
files to be downloaded once and then reused, for better performance. However,
setting longer caching date on a particular resource means that browser won't
even check if there is a newer version of it (without user explicitly 'clearing
the cache'), so if the resource eventually changes the user has partially stale
view (JSPs are dynamic and non-cacheable, but will link to non-updated files).

To solve this problem, JSPs should emit browser-cacheable resource URLs through
the `rst:assetUrl` tag. The tag appends the shared cache-busting version query
parameter so browsers re-download changed resources without requiring filename
rewrites during packaging.
  
