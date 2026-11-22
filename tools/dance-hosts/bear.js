function sh(p, mode, type, a) { var s = mode === 'outer' ? {fill: CY, stroke: CY, 'stroke-width': 2 * T, 'stroke-linejoin': 'round'} : {fill: '#000', stroke: 'none'}; for (var k in a) s[k] = a[k]; return el(p, type, s); }
var BODY = 'M-30,-100 Q-44,-98 -44,-80 Q-52,-60 -50,-36 Q-48,0 0,0 Q48,0 50,-36 Q52,-60 44,-80 Q44,-98 30,-100 Z';
var MUZ = 'M-22,12 Q0,6 22,12 Q36,18 36,38 Q36,60 0,61 Q-36,60 -36,38 Q-36,18 -22,12 Z';
var SHOE = 'M-25,0 C-26,-27 26,-27 25,0 Z';
function earArc(side) { var cx = side * 62, cy = -40, r = 14, seq = []; for (var a = 0; a < 360; a += 3) { var x = cx + r * Math.cos(a * Math.PI / 180), y = cy + r * Math.sin(a * Math.PI / 180); seq.push({x: x, y: y, o: (x * x) / (77 * 77) + (y * y) / (61 * 61) >= 1}); }
  var n = seq.length, st = 0; for (var i = 0; i < n; i++) { if (!seq[i].o && seq[(i + 1) % n].o) { st = (i + 1) % n; break; } }
  var d = ''; for (var j = 0; j < n; j++) { var s = seq[(st + j) % n]; if (!s.o) break; d += (d ? 'L' : 'M') + s.x.toFixed(1) + ',' + s.y.toFixed(1) + ' '; } return d; }
function build(parent, mode, part) {
  var R = {mode: mode, part: part};
  if (part === 'legs') { var o = mode === 'outer'; ['L', 'R'].forEach(function (s) { R['leg' + s] = el(parent, 'line', {'stroke-linecap': 'butt', fill: 'none', stroke: o ? CY : '#000', 'stroke-width': o ? 13 + 2 * T : 13}); var g = el(parent, 'g', {}); R['shoe' + s] = g; sh(g, mode, 'path', {d: SHOE}); }); return R; }
  if (part === 'bodydet') { ['L', 'R'].forEach(function (s) { var g = el(parent, 'g', {}); R['shoe' + s] = g;
    el(g, 'path', {d: 'M-24,0 L24,0', stroke: PK, 'stroke-width': 5.5, 'stroke-linecap': 'round'});
    el(g, 'path', {d: 'M-9,-1 L-9,-8 M0,-1 L0,-9 M9,-1 L9,-8', fill: 'none', stroke: PK, 'stroke-width': 3.5, 'stroke-linecap': 'round'}); }); return R; }
  R.body = el(parent, 'g', {});
  if (part === 'core') sh(R.body, mode, 'path', {d: BODY});
  R.head = el(R.body, 'g', {});
  if (part === 'head') { sh(R.head, mode, 'ellipse', {cx: 0, cy: 0, rx: 72, ry: 56}); [-62, 62].forEach(function (x) { sh(R.head, mode, 'circle', {cx: x, cy: -40, r: 26}); }); if (mode === 'inner') el(R.head, 'path', {d: MUZ, fill: '#000'}); }
  if (part === 'face') {
    [-1, 1].forEach(function (s) { el(R.head, 'path', {d: earArc(s), fill: 'none', stroke: PK, 'stroke-width': 6, 'stroke-linecap': 'round'}); });
    [-30, 30].forEach(function (x) { var e = el(R.head, 'g', {transform: 'translate(' + x + ',0)'}); el(e, 'circle', {r: 14.5, fill: 'none', stroke: YL, 'stroke-width': 4}); el(e, 'circle', {r: 11, fill: '#fff'}); el(e, 'circle', {r: 7, fill: '#111'}); el(e, 'circle', {cx: 2.5, cy: -2.5, r: 2.4, fill: '#fff'}); });
    el(R.head, 'path', {d: 'M-42,-22 Q-30,-31 -17,-26', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: 'M42,-22 Q30,-31 17,-26', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: MUZ, fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linejoin': 'round'});
    el(R.head, 'path', {d: 'M-9,18 Q0,14 9,18 Q8,26 0,28 Q-8,26 -9,18 Z', fill: 'none', stroke: YL, 'stroke-width': 3.5, 'stroke-linejoin': 'round'});
    el(R.head, 'path', {d: 'M-13,37 Q0,41 13,37 Q11,50 0,51 Q-11,50 -13,37 Z', fill: PK});
  }
  return R;
}
function apply(R) {
  var hipY = 262, gy = 352;
  if (R.shoeL) { [[-1, 'L'], [1, 'R']].forEach(function (s) { var ax = 200 + s[0] * 40; R['shoe' + s[1]].setAttribute('transform', 'translate(' + (ax + s[0] * 3) + ',' + gy + ')');
    if (R['leg' + s[1]]) { var L = R['leg' + s[1]]; L.setAttribute('x1', 200 + s[0] * 18); L.setAttribute('y1', hipY - 24); L.setAttribute('x2', ax); L.setAttribute('y2', gy - 14); } }); return; }
  R.body.setAttribute('transform', 'translate(200,' + hipY + ')');
  R.head.setAttribute('transform', 'translate(0,-158)');
}
function draw(svg, defs) {
  var B = function (p, m, part) { apply(build(p, m, part)); };
  if (BL) {
    var mc = mk(defs, 'bc'); B(mc, 'inner', 'core');
    var ml = mk(defs, 'bl'); B(ml, 'inner', 'legs'); B(ml, 'inner', 'core');
    var s = silG(svg); B(s, 'inner', 'legs'); B(s, 'inner', 'core');
    B(el(svg, 'g', {mask: 'url(#bl)'}), 'outer', 'legs');
    B(el(svg, 'g', {mask: 'url(#bc)'}), 'outer', 'core');
    B(el(svg, 'g', {}), 'detail', 'bodydet');
  } else {
    var mh = mk(defs, 'bh'); B(mh, 'inner', 'head');
    B(silG(svg), 'inner', 'head');
    B(el(svg, 'g', {mask: 'url(#bh)'}), 'outer', 'head');
    B(el(svg, 'g', {}), 'detail', 'face');
  }
}
