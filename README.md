# Sparrow
Bitcoin Transaction Editor

To clone this project, use `git clone --recursive git@github.com:craigraw/sparrow.git`

## Various ways to hex dump a file without spaces:
xxd -p file | tr -d '\n'
hexdump -ve '1/1 "%.2x"'
od -t x1 -An file | tr -d '\n '