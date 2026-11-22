#!/bin/bash
# usage: render.sh host layer
cd "$(dirname "$0")"; mkdir -p out
h=$1; l=$2
cat > page_$h.html <<HTML
<!DOCTYPE html><html><head><style>html,body{margin:0;background:transparent}</style></head><body><div id="root"></div>
<script src="common.js"></script><script src="$h.js"></script><script>var r=root();draw(r.svg,r.defs);</script></body></html>
HTML
google-chrome --headless=new --no-sandbox --disable-gpu --hide-scrollbars --default-background-color=00000000 --window-size=440,620 --screenshot=out/${h}_$l.png "file://$(dirname "$0")/page_$h.html?layer=$l" >/dev/null 2>&1
