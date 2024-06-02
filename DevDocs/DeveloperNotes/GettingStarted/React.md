# Getting started as a React developer

In general, we use [Flow](https://flow.org/) and [Prettier](https://prettier.io/) for
making the development less error prone and more convenient.

[ESlint](https://eslint.org/) is used for editors that don't come with great out of
the box JavaScript IDE functionalities.

## Do
- To enable the use of Flow, add `// @flow` at the top of a JavaScript file
- Please familiarise yourself with [Flow types](https://flow.org/en/docs/types/)
- If Prettier is not run on autosave, run Prettier on all modified JavaScript files
  before commit
- If you are installing a new npm package and need flow typings, do `npm run
  flow-typed search <package-name>` and install with `npm run flow-typed install <annotations-package-name>`.
  For example `npm run flow-typed install react-router-dom` installs type annotations
  for `react-router-dom`.

## Notes
- Flow does not support typescript type information files `.d.ts` :confused:
  [link](https://github.com/facebook/flow/issues/7)
- The more of the codebase is covered with Flow, the easier it will become to migrate
  to TypeScript if desired
- Prettier works with JavaScript, JSON, Markdown, ...

## Editor set up

### VSCode
1. Make sure you have the latest version of VS Code
2. Install ESlint plugin `dbaeumer.vscode-eslint`
3. Install Prettier plugin `esbenp.prettier-vscode`. VSCode will now automatically
   format code on save. In addition, you can use the following shortcuts to manually
   trigger Prettier:
   - Windows `Shift + Alt + F`
   - Mac `Shift + Option + F`
   - Ubuntu `Ctrl + Shift + I`
4. Install Flow language support `flowtype.flow-for-vscode`

#### VSCode comments
_Correct as of 2020 June:_
In VSCode JavaScript and TypeScript are tighly coupled. VSCode intellisense and other IDE
features for JavaScript are dependent on TypeScript. When Flow is enabled, this
results in double type annotations on hover and double autocomplete suggestions.
Generally, the top type pop-up annotation comes from Flow and the bottom one from
TypeScript. At the moment it is not possible to disable the double annotation pop-ups  without disabling TypeScript and hence disabling some important IDE features.

#### VSCode __recommendations__
- Additional plugins:
  1. npm - `eg2.vscode-npm-script`
  1. npm-intellisense - `christian-kohler.npm-intellisense`
- Replace VSCode with VSCodium. VSCodium is a drop-in replacement for VSCode
  without Microsoft telemetry and tracking

### WebStorm / Intellij
- ESLint is optional, WebStorm/Intellij provide decent inspections out of the box
- Install Prettier plugin in `File > Settings/Preferences > Plugins`.
  Two ways to trigger it:
  1. Use the “Reformat with Prettier” action (`Alt-Shift-Cmd-P` on macOS or
     `Alt-Shift-Ctrl-P` on Windows and Linux) or find it using the “Find Action”
     popup (`Cmd/Ctrl-Shift-A`)
  1. Tick the "Run on save for files" option in `File > Settings/Preferences >
     Languages & Frameworks > JavaScript > Prettier`
- Change the project language level to Flow:
  1. Go to `File > Settings/Preferences > Languages & Frameworks > JavaScript`
  1. From the `JavaScript Language Version` list, choose Flow
  1. In the Flow package or executable field, specify the path to `node_modules/flow-bin`
  1. In the Use Flow server for area tick: `Type checking` and `Navigation, code
     completion, and type hinting`
  1. Ensure `Save all modified files automatically` checkbox is selected

### Other editors
You need to set up Flow, Prettier and Eslint. If your editor/IDE does not provide
plugins for these, it is possible to run these from the command line.

### Flow
A lot of editor plugins for flow are simple wrappers around calls to the
[Language Server][lsp] that the [flow binary exposes][flow-cli]. If your editor
is having trouble displaying any flow errors then ensure that this server is
running correctly (to restart run `npm run flow stop ; npm run flow start`). If
your editor does not have a dedicated flow plugin, then see if you editor's
Language Server Protocol integration will do the same job.

[lsp]: https://microsoft.github.io/language-server-protocol/
[flow-cli]: https://flow.org/en/docs/cli/


## General Notes

Some notes on things to watch out for with using React in this codebase.


### Lazy loading components

React has a mechanism for lazily loading sections of the UI, so that only users
who use a part of the UI incur the costs of downloading the code that makes
that part of the UI work. This is implemented using the `lazy` function and the
`Suspense` component. This technique is used a few times in the codebase,
especially where some feature is implemented using a library such as editing
images or visualising data on a map.

One thing to note about this technique, however, is that if ANY other part of
the codebase imports the lazily loaded code in the usual manner then it causes
webpack to entirely ignore the lazy loading. This does make some sense as if
the code has already been loaded in the main bundle then there is no need to
dynamically loaded the same code again but it does mean that it is very easy to
inadvertently break some lazy loading through spooky action at a distance.

Let's say we have a component called BigInfrequentUsedFeature that is lazy loaded
```
  const LazyBigInfrequentlyUsedFeature = lazy(() => import("./BigInfrequentlyUsedFeature"));
```
This will dynamically create a new JS bundle in ../../../src/main/webapp/ui/dist
that will be fetched only once `LazyBigInfrequentlyUsedFeature` is rendered.
However, if some other code imports BigInfrequentlyUsedFeature in the usual
manner then it will be included in the main bundle and the new bundle will not
be generated. In other words, if a component is EVER imported lazily then it
MUST ALWAYS be imported lazily (either directly or indirectly). This is a
problem as it may not always be immediately obvious if a given component (or any
component that it relies all the way down the component tree) is being lazily
loaded somewhere else in the codebase.

If that wasn't bad enough, the component itself doesn't even have to be
imported; if ANYTHING is imported in the usual manner from the same module as
the lazily loaded component then it will invalidate the lazily loading. Let's
say that the module `BigInfrequentlyUsedFeature`, in addition to the default
export of the `BigInfrequentlyUsedFeature` component itself, also exports as
constant `SOME_CONSTANT`. If `SOME_CONSTANT` is imported ANYWHERE by code in the
main bundle then the lazily loading will not work.

All of this is to say, that whilst lazy loading components is a powerful
feature, and something we ought to use more to keep the bundle sizes small, it
is also a feature that doens't have any guardrails. Which components are lazily
loaded must be carefully evaluated to minimise the risk that the additional
complexity in the code results in no change to page load performance simply
because the lazy loading is just not happening.
