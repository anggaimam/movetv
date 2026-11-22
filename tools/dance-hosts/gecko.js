var GN = '#A6F03C';
function sh(p, mode, type, a) { var s = mode === 'outer' ? {fill: CY, stroke: CY, 'stroke-width': 2 * T, 'stroke-linejoin': 'round'} : {fill: '#000', stroke: 'none'}; for (var k in a) s[k] = a[k]; return el(p, type, s); }
function ln(p, mode, x1, y1, x2, y2, w) { el(p, 'line', {x1: x1, y1: y1, x2: x2, y2: y2, 'stroke-linecap': 'round', stroke: mode === 'outer' ? CY : '#000', 'stroke-width': mode === 'outer' ? w + 2 * T : w}); }
var HEAD = 'M0,-61 C40,-61 60,-40 66,-16 C74,4 84,20 76,42 C68,60 40,66 0,66 C-40,66 -68,60 -76,42 C-84,20 -74,4 -66,-16 C-60,-40 -40,-61 0,-61 Z';
var BODY = 'M-24,-100 Q-34,-98 -34,-80 Q-38,-50 -36,-28 Q-34,0 0,0 Q34,0 36,-28 Q38,-50 34,-80 Q34,-98 24,-100 Z';
var TAIL = 'M24,-16 C26,30 52,58 80,48 C96,42 104,22 104,0 C96,18 84,28 70,28 C54,26 42,10 40,-16 Z';
var TOES = [[-26, 9], [-11, 13], [5, 13], [20, 9]];
function build(parent, mode, part) {
  var R = {mode: mode, part: part};
  if (part === 'legs' || part === 'feetdet') { ['L', 'R'].forEach(function (s) {
    if (part === 'legs') R['leg' + s] = el(parent, 'line', {'stroke-linecap': 'round', fill: 'none', stroke: mode === 'outer' ? CY : '#000', 'stroke-width': mode === 'outer' ? 10 + 2 * T : 10});
    var g = el(parent, 'g', {}); R['ft' + s] = g;
    TOES.forEach(function (t) { if (part === 'legs') ln(g, mode, 0, -4, t[0], t[1], 3.5); else el(g, 'circle', {cx: t[0], cy: t[1], r: 5.5, fill: PK, 'fill-opacity': 0.35, stroke: PK, 'stroke-width': 3.5}); });
    if (part === 'legs') sh(g, mode, 'circle', {cx: 0, cy: -3, r: 7}); }); return R; }
  R.body = el(parent, 'g', {});
  if (part === 'core') sh(R.body, mode, 'path', {d: BODY});
  if (part === 'bodydet') [[-70, 4.5], [-50, 9], [-28, 4.5]].forEach(function (d) { el(R.body, 'circle', {cx: 0, cy: d[0], r: d[1], fill: GN}); });
  if (part === 'tail') sh(R.body, mode, 'path', {d: TAIL});
  if (part === 'taildet') [[56, 36, 3.8], [68, 39, 3.8], [80, 37, 3.2], [90, 29, 2.8], [46, 26, 3]].forEach(function (d) { el(R.body, 'circle', {cx: d[0], cy: d[1], r: d[2], fill: GN}); });
  R.head = el(R.body, 'g', {});
  if (part === 'head') sh(R.head, mode, 'path', {d: HEAD});
  if (part === 'face') {
    [[-1, -46.5, 5], [-11, -36.5, 3], [9, -36.5, 3], [-1, -27, 5], [-65, 12, 5], [-60, 25, 4], [60, 14, 5], [57, 26, 4]].forEach(function (d) { el(R.head, 'circle', {cx: d[0], cy: d[1], r: d[2], fill: GN}); });
    [-37, 36].forEach(function (x) { var e = el(R.head, 'g', {transform: 'translate(' + x + ',-2)'}); el(e, 'circle', {r: 17, fill: '#fff'}); el(e, 'circle', {r: 12, fill: '#111'}); el(e, 'circle', {cx: 4, cy: -4, r: 4, fill: '#fff'}); });
    el(R.head, 'path', {d: 'M-52,-26 Q-38,-36 -22,-31', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: 'M51,-27 Q37,-37 21,-32', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: 'M-27,22 C-23,13 -12,11 0,12 C12,11 23,13 27,22', fill: 'none', stroke: CY, 'stroke-width': 4, 'stroke-linecap': 'round'});
    el(R.head, 'circle', {cx: -8, cy: 20, r: 2.8, fill: CY}); el(R.head, 'circle', {cx: 8, cy: 20, r: 2.8, fill: CY});
    el(R.head, 'path', {d: 'M-36,28 Q0,36 37,26 Q31,56 0,58 Q-29,56 -36,28 Z', fill: '#3A0A2E', stroke: YL, 'stroke-width': 4.5, 'stroke-linejoin': 'round'});
    el(R.head, 'ellipse', {cx: 0, cy: 47, rx: 12, ry: 7, fill: PK});
  }
  return R;
}
function apply(R) {
  var hipY = 252, gy = 352;
  if (R.ftL) { [[-1, 'L'], [1, 'R']].forEach(function (s) { var ax = 200 + s[0] * 30; R['ft' + s[1]].setAttribute('transform', 'translate(' + ax + ',' + (gy - 10) + ') scale(' + (-s[0]) + ',1)');
    if (R['leg' + s[1]]) { var L = R['leg' + s[1]]; L.setAttribute('x1', 200 + s[0] * 16); L.setAttribute('y1', hipY - 22); L.setAttribute('x2', ax); L.setAttribute('y2', gy - 14); } }); return; }
  R.body.setAttribute('transform', 'translate(200,' + hipY + ')');
  R.head.setAttribute('transform', 'translate(0,-155)');
}
function draw(svg, defs) {
  var B = function (p, m, part) { apply(build(p, m, part)); };
  if (BL) {
    var mti = mk(defs, 'gti'); B(mti, 'inner', 'tail');
    var mtc = mk(defs, 'gtc'); B(mtc, 'inner', 'legs'); B(mtc, 'inner', 'core');
    var ml = mk(defs, 'gl'); B(ml, 'inner', 'legs'); B(ml, 'inner', 'core');
    var mc = mk(defs, 'gc'); B(mc, 'inner', 'core');
    var s = silG(svg); B(s, 'inner', 'tail'); B(s, 'inner', 'legs'); B(s, 'inner', 'core');
    var tc = el(svg, 'g', {mask: 'url(#gtc)'}); B(el(tc, 'g', {mask: 'url(#gti)'}), 'outer', 'tail'); B(tc, 'detail', 'taildet');
    B(el(svg, 'g', {mask: 'url(#gl)'}), 'outer', 'legs');
    B(el(svg, 'g', {}), 'detail', 'feetdet');
    B(el(svg, 'g', {mask: 'url(#gc)'}), 'outer', 'core');
    B(el(svg, 'g', {}), 'detail', 'bodydet');
  } else {
    var mh = mk(defs, 'gh'); B(mh, 'inner', 'head');
    B(silG(svg), 'inner', 'head');
    B(el(svg, 'g', {mask: 'url(#gh)'}), 'outer', 'head');
    B(el(svg, 'g', {}), 'detail', 'face');
  }
}
