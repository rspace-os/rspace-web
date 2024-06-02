#!/bin/bash
## Moving to this special folder will run the script 
## on 1st execution.
set -x
mv rs-dbbaseline-utf8.sql docker-entrypoint-initdb.d/