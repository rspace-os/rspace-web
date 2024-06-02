# Evaluating JavaScript Packages

We make substantial use of the npm ecosystem and wider open source community,
leveraging a variety of libraries, frameworks, and other tools to ease the
development effort. However, utilising third-party code introduces substantial
risk to the project and the wider organisation, and so any decision should not
be taken lightly. All code becomes a burden in the long-term as it requires
maintenance due the evolution of the Web platform, third-party doubly so as the
decision about when the piece of code no longer gets support is out of our
hands. Unmaintained libraries become a security risk and prevent the product
from using new technologies as it becomes necessary to maintain compatibility
with the existing codebase. Listed below are a series of questions,
approximately sorted by importance, that should be asked of any new packages
added to the project.

## Is the library necessary?

* Is there some native browser API that would do the job?
  e.g. jQuery is no longer necessary because modern browsers expose the same
  capabilities

* Could we implement it ourselves?
  Trivial code libraries, like left-pad, are common in the JS ecosystem and add
  undue operational risk. E.g. we have our own Set data structure rather than
  using a library because the implementation is trivial and we remain in
  control of the risk that the code adds to the project.

## License

* Do we have legal right to use this work?
  It is crucial that we have the legal right to use the third-party code and
  simply because the code is open sourced does that not mean that it can be
  used for commercial purposes. Some open source licenses require that any
  derived works are themselves open sourced. Here are some links for those
  unfamiliar with the various open source licenses:
  * [A dev’s guide to open source software licensing](https://github.com/readme/guides/open-source-licensing)
  * [Choose an open source license](https://choosealicense.com/)
  Generally speaking, anything MIT (which most JS libraries are) will be fine.

## Is it maintained?

* Are there regular updates that fix bugs?
  This isn't necessarily a key metric and some projects are just "done", but JS
  libraries tend to rot very quickly when not being actively maintained
  compared to other environments and so a lack of any version change in the
  last 6 months is probably a sign that the project is likely not to keep up
  with changes to any underlying framework/browser APIs.

* Are there lots of open issues going unresolved?
  If there are lots of open issues for months and months is may mean that the
  maintainers don't have capacity to resolve bugs in a timely fashion. Again,
  not a good sign.

## Is it stable?

* Do they appear to follow semantic versioning?
  A strict adherence to semantic versioning is not ubiquitous in the JavaScript
  community and with the dynamic nature of the language there's no way to
  enforce a change to the major version when the API changes as there is with
  programming languages like Elm. As such, it can be difficult to judge the
  severity of an upgrade when any change to the version number could require
  substantial development work. Avoid libraries that don't follow this.
  * [Semantic Versioning 2.0.0](https://semver.org/)

* Is it still in early development?
  Quite simply, avoid libraries that are still in beta. This includes upgrading
  to the next major version that is still in beta. APIs change considerably
  when software is in beta and we don't want to be chasing every little change
  that might be made whilst a project is still be ironed out.

* How many versions have there been?
  Have there been lots of major versions with lots of breaking changes? Will
  the long term maintenance on our codebase of this dependency be worth it?

## Security

* Does the library demonstrate a commitment to fixing security issues?
  Generally, open source software is made available without any guarantees, and
  that includes identifying and fixing security issues in a timely manner.
  Nonetheless, many will provide a mechanism for ensuring that the project does
  not become a liability. Even if we don't even up using these channels, their
  very existence gives confidence to the quality of the software and the
  professionalism of the maintainers. Things to look out for include
  * A SECURITY.md file
  * Contact details, sometimes with a PGP key
  * A [security.txt](https://securitytxt.org/) file on the official website
  * Being part of a bug bounty programme
  This holds especially true for libraries that we use specifically for their
  security functionality — like HTML sanitisers — where seeing some or all of
  the above is a must.

## Documentation

* Is there extensive official documentation on how to use the library?

* Is there extensive unofficial documentation: conference talks, blog posts,
  StackOverflow discussion, etc?

## Who backs it/funds it?

* Is it made by a large corporation, like how Facebook open sourced react and
  jest?

* Is it well funded by various backers to ensure that maintenance work is
  sustainable?
  Is it backed by [Open collective](https://opencollective.com/) / Patreon /
  something similar? If it's just one guy in his spare time without financial
  backing, then we probably shouldn't use it. Essentially, how much does the
  maintainer have to lose by being malicious, reputationally and financially?

## Static Typing

* Does the library expose Flow.js types?
  Flow.js is a core part of our toolchain enabling us to catch a variety of
  bugs during development. Libraries that expose type definitions for their API
  allow for Flow.js to type check where our code interfaces with that API. If
  the project doesn't expose Flow.js type definitions (which are not the same
  thing as TypeScript type definitions) then
  * Does the [flow-typed project](https://flow-typed.github.io/flow-typed)?
  * Is the API small enough that we could reasonably implement a library
    definition?

## Dependencies

* How large is the package.json file?
  Will it be bringing in lots of indirect dependencies which will expand the
  number of libraries that include security vulnerabilities? This matters less
  if its a devDependency, like a testing tool, but should still be considered
  all the same.

## How big is it?

All code that ends up in the product contributes to the size of the JavaScript
bundles. Third-party code contributes the majority of the code that we ship to
our users whenever they load the webpages and whilst they end up being minified
and gzipped this still works out to be several megabytes, which is already much
too big. Therefore, we should aim to keep the size of the JavaScript packages
as small as possible. npmjs.com gives an approx size for each package under
"unpacked size", although this is perhaps only worth using as a metric to
compare different packages as it is unclear how accurate this is. With that in
mind, it is certainly worth asking the following

* Is that a reasonable size for the feature set of the library?

* Do we need all of this functionality; is there a smaller library that would
  be sufficient?

* After the library has been added to the project, [check the impact it's
  having on the bundle size](./AnalysingJavaScriptBundles.md).
