# Dance host pictures

The eight Animal Dance Party hosts were sketched as SVG rigs and approved in chat. These scripts draw each host's
static parts (everything except the arms) into `app/src/main/assets/dance/<name>_body.png` and `<name>_head.png`.
Arms, hands and the flamingo's wings are drawn every frame in `DanceArt`.

To redraw a host: `./render.sh <name> body` and `./render.sh <name> head` (headless Chrome), then crop each picture to
its visible pixels (2 px margin) and copy the crop offsets into `BODY_OFF` / `HEAD_OFF` in `DanceArt`.

Rig units: the floor is at y = 352, the middle at x = 200, and 1 rig unit = 1.7 host-picture pixels
(the 440 x 620 host picture shows rig x 70.6 .. 329.4 and rig y 7.3 .. 372).
