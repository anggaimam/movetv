function sh(p, mode, type, a) { var s = mode === 'outer' ? {fill: CY, stroke: CY, 'stroke-width': 2 * T, 'stroke-linejoin': 'round'} : {fill: '#000', stroke: 'none'}; for (var k in a) s[k] = a[k]; return el(p, type, s); }
var EAR = 'M-62,-24 L-66,-76 L-24,-50 Z';
var BODY = 'M-26,-98 Q-38,-96 -38,-80 Q-44,-54 -42,-32 Q-40,0 0,0 Q40,0 42,-32 Q44,-54 38,-80 Q38,-96 26,-98 Z';
var TAIL = 'M30,-10 C40,40 60,64 80,44 C92,32 94,14 84,6';
var SHOE = 'M-23,0 L-23,-9 Q-23,-21 -10,-21 L10,-21 Q23,-21 23,-9 L23,0 Z';
var MUZ = 'M-29,15 Q-14,5 0,19 Q14,5 29,15 C31,42 14,53 0,53 C-14,53 -31,42 -29,15 Z';
function build(parent, mode, part) {
  var R = {mode: mode, part: part};
  if (part === 'legs') { var o = mode === 'outer'; ['L', 'R'].forEach(function (s) { R['leg' + s] = el(parent, 'line', {'stroke-linecap': 'butt', fill: 'none', stroke: o ? CY : '#000', 'stroke-width': o ? 12 + 2 * T : 12}); var g = el(parent, 'g', {}); R['shoe' + s] = g; sh(g, mode, 'path', {d: SHOE}); }); return R; }
  if (part === 'shoes') { ['L', 'R'].forEach(function (s) { var g = el(parent, 'g', {}); R['shoe' + s] = g;
    el(g, 'path', {d: 'M-22,0 L22,0', stroke: PK, 'stroke-width': 5.5, 'stroke-linecap': 'round'});
    el(g, 'path', {d: 'M-8,-1 L-8,-9 M0,-1 L0,-10 M8,-1 L8,-9', fill: 'none', stroke: PK, 'stroke-width': 3.5, 'stroke-linecap': 'round'}); }); return R; }
  R.body = el(parent, 'g', {});
  if (part === 'core') sh(R.body, mode, 'path', {d: BODY});
  if (part === 'bib') el(R.body, 'path', {d: 'M-30,-88 L-23,-74 L-16,-78 L-11,-64 L-5,-68 L0,-55 L5,-68 L11,-64 L16,-78 L23,-74 L30,-88', fill: 'none', stroke: CY, 'stroke-width': 4, 'stroke-linejoin': 'round', 'stroke-linecap': 'round'});
  if (part === 'tail') el(R.body, 'path', {d: TAIL, fill: 'none', 'stroke-linecap': 'round', 'stroke-linejoin': 'round', stroke: mode === 'outer' ? CY : '#000', 'stroke-width': mode === 'outer' ? 9 + 2 * T : 9});
  R.head = el(R.body, 'g', {});
  if (part === 'head') { sh(R.head, mode, 'ellipse', {cx: 0, cy: 0, rx: 70, ry: 54}); [-1, 1].forEach(function (s) { sh(R.head, mode, 'path', {d: EAR, transform: 'scale(' + s + ',1)'}); }); }
  if (part === 'face') {
    el(R.head, 'path', {d: 'M-8,-52 Q-12,-64 -5,-72 M1,-53 Q3,-68 12,-75 M9,-52 Q15,-60 22,-62', fill: 'none', stroke: CY, 'stroke-width': 4, 'stroke-linecap': 'round'});
    [-1, 1].forEach(function (s) {
      el(R.head, 'path', {d: 'M-58,-36 L-61,-64 L-36,-50', transform: 'scale(' + s + ',1)', fill: 'none', stroke: PK, 'stroke-width': 4.5, 'stroke-linejoin': 'round', 'stroke-linecap': 'round'});
      el(R.head, 'path', {d: 'M-36,14 L-76,4 M-36,22 L-78,21 M-36,30 L-74,38', transform: 'scale(' + s + ',1)', fill: 'none', stroke: YL, 'stroke-width': 3.5, 'stroke-linecap': 'round'}); });
    [-30, 30].forEach(function (x) { var e = el(R.head, 'g', {transform: 'translate(' + x + ',0)'}); el(e, 'circle', {r: 14, fill: '#fff'}); el(e, 'circle', {r: 9.5, fill: '#111'}); el(e, 'circle', {cx: 3, cy: -3, r: 3.2, fill: '#fff'}); });
    el(R.head, 'path', {d: 'M-42,-26 Q-31,-35 -18,-30', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: 'M42,-26 Q31,-35 18,-30', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linecap': 'round'});
    el(R.head, 'ellipse', {cx: 0, cy: 35, rx: 9, ry: 6, fill: PK});
    el(R.head, 'path', {d: MUZ, fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linejoin': 'round'});
    el(R.head, 'path', {d: 'M-9,12 Q0,9 9,12 Q6,20 0,23 Q-6,20 -9,12 Z', fill: YL, stroke: YL, 'stroke-width': 2, 'stroke-linejoin': 'round'});
  }
  return R;
}
function apply(R) {
  var hipY = 262, gy = 352;
  if (R.shoeL) { [[-1, 'L'], [1, 'R']].forEach(function (s) { var ax = 200 + s[0] * 25; R['shoe' + s[1]].setAttribute('transform', 'translate(' + (ax + s[0] * 3) + ',' + gy + ')');
    if (R['leg' + s[1]]) { var L = R['leg' + s[1]]; L.setAttribute('x1', 200 + s[0] * 16); L.setAttribute('y1', hipY - 24); L.setAttribute('x2', ax); L.setAttribute('y2', gy - 18); } }); return; }
  R.body.setAttribute('transform', 'translate(200,' + hipY + ')');
  R.head.setAttribute('transform', 'translate(0,-153)');
}
function draw(svg, defs) {
  var B = function (p, m, part) { apply(build(p, m, part)); };
  if (BL) {
    var mt = mk(defs, 'ct'); B(mt, 'inner', 'tail'); B(mt, 'inner', 'legs'); B(mt, 'inner', 'core');
    var ml = mk(defs, 'cl'); B(ml, 'inner', 'legs'); B(ml, 'inner', 'core');
    var mc = mk(defs, 'cc'); B(mc, 'inner', 'core');
    var s = silG(svg); B(s, 'inner', 'tail'); B(s, 'inner', 'legs'); B(s, 'inner', 'core');
    B(el(svg, 'g', {mask: 'url(#ct)'}), 'outer', 'tail');
    B(el(svg, 'g', {mask: 'url(#cl)'}), 'outer', 'legs');
    B(el(svg, 'g', {mask: 'url(#cc)'}), 'outer', 'core');
    B(el(svg, 'g', {}), 'detail', 'bib');
    B(el(svg, 'g', {}), 'detail', 'shoes');
  } else {
    var mh = mk(defs, 'ch'); B(mh, 'inner', 'head');
    B(silG(svg), 'inner', 'head');
    B(el(svg, 'g', {mask: 'url(#ch)'}), 'outer', 'head');
    B(el(svg, 'g', {}), 'detail', 'face');
  }
}
