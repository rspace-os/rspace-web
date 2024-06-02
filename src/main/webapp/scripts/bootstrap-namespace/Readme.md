This folder contains pre-processed bootstrap css with all styles prefixed by `.bootstrap-namespace`.
That allows bootstrap to used just on a fragment of the page - inside an element marked with `bootstrap-namespace` class. 

Idea taken from http://stackoverflow.com/a/19796985/639863.

## Steps to (re)generate bootstrap-namespace.min.css
1.  open command line, navigate to directory containing this Readme file 
2.  make sure you have LESS css preprocessor installed (or install with `npm install -g less`)
3.  make sure you have clean-css plugin for LESS installed (or install with `npm install -g less-plugin-clean-css`)
4.  run: `lessc bootstrap-namespace.less bootstrap-namespace.css`
That should generate css file, compare to orignal bootstrap to ensure it's fine
5.  run: `lessc --clean-css bootstrap-namespace.css bootstrap-namespace.min.css`
That should generate minified css file that we link in RSpace

## Note about import glyphicon problem
1. We have to import fonts folder here as well, because inside bootstrap fonts are referenced with relative path.
2. The right way is to import bootstrap dependency as less (sass) in bower-components and change path variable so this namespace reference the same font. Although I am not sure whether we use raw-bootstrap anywhere. If we don't then it doesn't matter. 
3. Or alternatively, we can change references in css files directly although I am not sure how valid is that (cause it will interfere with direct import from bower_components folder)