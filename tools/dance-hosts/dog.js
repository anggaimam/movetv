function sh(p, mode, type, a) { var s = mode === 'outer' ? {fill: CY, stroke: CY, 'stroke-width': 2 * T, 'stroke-linejoin': 'round'} : {fill: '#000', stroke: 'none'}; for (var k in a) s[k] = a[k]; return el(p, type, s); }
var EAR = 'M-30,-50 C-66,-66 -104,-52 -112,-14 C-116,8 -106,26 -94,24 C-84,22 -78,6 -58,-6 Z';
var BODY = 'M-30,-92 Q-44,-90 -44,-72 Q-50,-54 -48,-34 Q-46,0 0,0 Q46,0 48,-34 Q50,-54 44,-72 Q44,-90 30,-92 Z';
var TAIL = 'M30,-14 Q42,36 60,30 Q74,22 74,-6 Q66,16 58,17 Q48,16 42,-14 Z';
var SHOE = 'M-24,0 L-24,-10 Q-24,-23 -10,-23 L10,-23 Q24,-23 24,-10 L24,0 Z';
function build(parent, mode, part) {
  var R = {mode: mode, part: part};
  if (part === 'legs') { var o = mode === 'outer'; ['L', 'R'].forEach(function (s) { R['leg' + s] = el(parent, 'line', {'stroke-linecap': 'butt', fill: 'none', stroke: o ? CY : '#000', 'stroke-width': o ? 13 + 2 * T : 13}); var g = el(parent, 'g', {}); R['shoe' + s] = g; sh(g, mode, 'path', {d: SHOE}); }); return R; }
  if (part === 'bodydet') { ['L', 'R'].forEach(function (s) { var g = el(parent, 'g', {}); R['shoe' + s] = g;
    el(g, 'path', {d: 'M-23,0 L23,0', stroke: PK, 'stroke-width': 5.5, 'stroke-linecap': 'round'});
    el(g, 'path', {d: 'M-9,-1 L-9,-9 M0,-1 L0,-10 M9,-1 L9,-9', fill: 'none', stroke: PK, 'stroke-width': 3.5, 'stroke-linecap': 'round'}); }); return R; }
  R.body = el(parent, 'g', {});
  if (part === 'core') sh(R.body, mode, 'path', {d: BODY});
  if (part === 'tail') sh(R.body, mode, 'path', {d: TAIL});
  R.head = el(R.body, 'g', {});
  if (part === 'head') { sh(R.head, mode, 'ellipse', {cx: 0, cy: 0, rx: 63, ry: 58}); if (mode === 'inner') el(R.head, 'circle', {cx: 0, cy: 28, r: 27, fill: '#000'}); }
  if (part === 'ears') [-1, 1].forEach(function (s) { sh(R.head, mode, 'path', {d: EAR, transform: 'scale(' + s + ',1)'}); });
  if (part === 'face') {
    [-1, 1].forEach(function (s) { el(R.head, 'path', {d: 'M-70,-42 Q-90,-30 -93,6', transform: 'scale(' + s + ',1)', fill: 'none', stroke: PK, 'stroke-width': 5, 'stroke-linecap': 'round'}); });
    [-29, 29].forEach(function (x) { var e = el(R.head, 'g', {transform: 'translate(' + x + ',-2)'}); el(e, 'circle', {r: 14, fill: '#fff'}); el(e, 'circle', {r: 9.5, fill: '#111'}); el(e, 'circle', {cx: 3, cy: -3, r: 3.2, fill: '#fff'}); });
    el(R.head, 'path', {d: 'M-41,-25 Q-30,-34 -17,-29', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: 'M41,-25 Q30,-34 17,-29', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linecap': 'round'});
    el(R.head, 'circle', {cx: 0, cy: 28, r: 26, fill: 'none', stroke: YL, 'stroke-width': 4.5});
    el(R.head, 'path', {d: 'M-12,36 Q0,40 12,36 Q10,48 0,49 Q-10,48 -12,36 Z', fill: PK});
    el(R.head, 'ellipse', {cx: 0, cy: 17, rx: 11, ry: 7.5, fill: '#4A4A50', stroke: YL, 'stroke-width': 3.5});
    el(R.head, 'ellipse', {cx: -3, cy: 15, rx: 3, ry: 1.8, fill: '#fff', opacity: 0.7});
  }
  return R;
}
function apply(R) {
  var hipY = 257, gy = 352;
  if (R.shoeL) { [[-1, 'L'], [1, 'R']].forEach(function (s) { var ax = 200 + s[0] * 27; R['shoe' + s[1]].setAttribute('transform', 'translate(' + (ax + s[0] * 3) + ',' + gy + ')');
    if (R['leg' + s[1]]) { var L = R['leg' + s[1]]; L.setAttribute('x1', 200 + s[0] * 18); L.setAttribute('y1', hipY - 24); L.setAttribute('x2', ax); L.setAttribute('y2', gy - 20); } }); return; }
  R.body.setAttribute('transform', 'translate(200,' + hipY + ')');
  R.head.setAttribute('transform', 'translate(0,-150)');
}
function draw(svg, defs) {
  var B = function (p, m, part) { apply(build(p, m, part)); };
  if (BL) {
    var mt = mk(defs, 'dt'); B(mt, 'inner', 'tail'); B(mt, 'inner', 'legs'); B(mt, 'inner', 'core');
    var ml = mk(defs, 'dl'); B(ml, 'inner', 'legs'); B(ml, 'inner', 'core');
    var mc = mk(defs, 'dc'); B(mc, 'inner', 'core');
    var s = silG(svg); B(s, 'inner', 'tail'); B(s, 'inner', 'legs'); B(s, 'inner', 'core');
    B(el(svg, 'g', {mask: 'url(#dt)'}), 'outer', 'tail');
    B(el(svg, 'g', {mask: 'url(#dl)'}), 'outer', 'legs');
    B(el(svg, 'g', {mask: 'url(#dc)'}), 'outer', 'core');
    B(el(svg, 'g', {}), 'detail', 'bodydet');
  } else {
    var me = mk(defs, 'de'); B(me, 'inner', 'ears'); B(me, 'inner', 'head');
    var mh = mk(defs, 'dh'); B(mh, 'inner', 'head');
    var s2 = silG(svg); B(s2, 'inner', 'ears'); B(s2, 'inner', 'head');
    B(el(svg, 'g', {mask: 'url(#de)'}), 'outer', 'ears');
    B(el(svg, 'g', {mask: 'url(#dh)'}), 'outer', 'head');
    B(el(svg, 'g', {}), 'detail', 'face');
  }
}
