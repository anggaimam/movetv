function sh(p, mode, type, a, col) { col = col || CY; var s = mode === 'outer' ? {fill: col, stroke: col, 'stroke-width': 2 * T, 'stroke-linejoin': 'round'} : {fill: '#000', stroke: 'none'}; for (var k in a) s[k] = a[k]; return el(p, type, s); }
var HEAD = 'M-58,-6 C-60,-44 -30,-52 0,-52 C30,-52 60,-44 58,-6 L77,-12 L62,6 L85,0 L62,18 Q54,46 24,58 Q0,64 -24,58 Q-54,46 -62,18 L-85,0 L-62,6 L-77,-12 Z';
var TUFT = 'M-14,-48 L-7,-65 L0,-52 L10,-69 L14,-48 Z';
var EAR = 'M-58,-16 L-72,-80 L-20,-46 Z';
var BODY = 'M-26,-104 Q-38,-102 -38,-84 Q-44,-56 -42,-32 Q-40,0 0,0 Q40,0 42,-32 Q44,-56 38,-84 Q38,-102 26,-104 Z';
var TAIL = 'M28,-20 C30,30 60,52 88,40 C104,30 106,4 98,-24 C92,0 80,14 66,14 C52,12 44,-4 42,-20 Z';
var SHOE = 'M-23,0 L-23,-9 Q-23,-21 -10,-21 L10,-21 Q23,-21 23,-9 L23,0 Z';
var MASK = 'M-45,20 Q-26,29 -11,23 Q0,19 11,23 Q26,29 45,20 Q37,43 19,52 Q0,58 -19,52 Q-37,43 -45,20 Z';
function build(parent, mode, part) {
  var R = {mode: mode, part: part};
  if (part === 'legs') { var o = mode === 'outer'; ['L', 'R'].forEach(function (s) { R['leg' + s] = el(parent, 'line', {'stroke-linecap': 'butt', fill: 'none', stroke: o ? CY : '#000', 'stroke-width': o ? 12 + 2 * T : 12}); var g = el(parent, 'g', {}); R['shoe' + s] = g; sh(g, mode, 'path', {d: SHOE}); }); return R; }
  if (part === 'shoes') { ['L', 'R'].forEach(function (s) { var g = el(parent, 'g', {}); R['shoe' + s] = g;
    el(g, 'path', {d: 'M-22,0 L22,0', stroke: PK, 'stroke-width': 5.5, 'stroke-linecap': 'round'});
    el(g, 'path', {d: 'M-6,-1 L-6,-9 M6,-1 L6,-9', fill: 'none', stroke: PK, 'stroke-width': 3.5, 'stroke-linecap': 'round'}); }); return R; }
  R.body = el(parent, 'g', {});
  if (part === 'core') sh(R.body, mode, 'path', {d: BODY});
  if (part === 'bib') el(R.body, 'path', {d: 'M-30,-92 L-23,-77 L-16,-81 L-11,-66 L-5,-70 L0,-56 L5,-70 L11,-66 L16,-81 L23,-77 L30,-92', fill: 'none', stroke: CY, 'stroke-width': 4, 'stroke-linejoin': 'round', 'stroke-linecap': 'round'});
  if (part === 'tail') sh(R.body, mode, 'path', {d: TAIL}, mode === 'outer' ? 'url(#tg)' : CY);
  if (part === 'taildet') el(R.body, 'path', {d: 'M74,13 L80,22 L84,15 L90,24 L94,17 L102,22', fill: 'none', stroke: PK, 'stroke-width': 4, 'stroke-linejoin': 'round', 'stroke-linecap': 'round'});
  R.head = el(R.body, 'g', {});
  if (part === 'head') { sh(R.head, mode, 'path', {d: HEAD}); sh(R.head, mode, 'path', {d: TUFT}); [-1, 1].forEach(function (s) { sh(R.head, mode, 'path', {d: EAR, transform: 'scale(' + s + ',1)'}); }); }
  if (part === 'face') {
    [-1, 1].forEach(function (s) { el(R.head, 'path', {d: 'M-55,-28 L-64,-67 L-33,-47', transform: 'scale(' + s + ',1)', fill: 'none', stroke: PK, 'stroke-width': 4.5, 'stroke-linejoin': 'round', 'stroke-linecap': 'round'}); });
    el(R.head, 'path', {d: MASK, fill: YL, 'fill-opacity': 0.9, stroke: YL, 'stroke-width': 3, 'stroke-linejoin': 'round'});
    [-29, 29].forEach(function (x) { var e = el(R.head, 'g', {transform: 'translate(' + x + ',2)'}); el(e, 'circle', {r: 14, fill: '#fff'}); el(e, 'circle', {r: 9, fill: '#111'}); el(e, 'circle', {cx: 3, cy: -3, r: 3, fill: '#fff'}); });
    el(R.head, 'path', {d: 'M-42,-17 Q-31,-26 -18,-21', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: 'M42,-17 Q31,-26 18,-21', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: 'M-15,32 Q0,37 15,32 Q13,49 0,51 Q-13,49 -15,32 Z', fill: '#3A0A2E'});
    el(R.head, 'ellipse', {cx: 0, cy: 45, rx: 8.5, ry: 5, fill: PK});
    el(R.head, 'path', {d: 'M0,27 L0,33', stroke: '#111', 'stroke-width': 2.5, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: 'M-9,19 Q0,15 9,19 Q6,27 0,28 Q-6,27 -9,19 Z', fill: '#111'});
  }
  return R;
}
function apply(R) {
  var hipY = 272, gy = 352;
  if (R.shoeL) { [[-1, 'L'], [1, 'R']].forEach(function (s) { var ax = 200 + s[0] * 24; R['shoe' + s[1]].setAttribute('transform', 'translate(' + (ax + s[0] * 3) + ',' + gy + ')');
    if (R['leg' + s[1]]) { var L = R['leg' + s[1]]; L.setAttribute('x1', 200 + s[0] * 16); L.setAttribute('y1', hipY - 24); L.setAttribute('x2', ax); L.setAttribute('y2', gy - 18); } }); return; }
  R.body.setAttribute('transform', 'translate(200,' + hipY + ')');
  R.head.setAttribute('transform', 'translate(0,-160)');
}
function draw(svg, defs) {
  var tg = el(defs, 'linearGradient', {id: 'tg', gradientUnits: 'userSpaceOnUse', x1: 88, y1: 18, x2: 97, y2: -7});
  el(tg, 'stop', {offset: 0, 'stop-color': CY}); el(tg, 'stop', {offset: 0, 'stop-color': PK}); el(tg, 'stop', {offset: 1, 'stop-color': PK});
  var B = function (p, m, part) { apply(build(p, m, part)); };
  if (BL) {
    var mti = mk(defs, 'xti'); B(mti, 'inner', 'tail');
    var mtc = mk(defs, 'xtc'); B(mtc, 'inner', 'legs'); B(mtc, 'inner', 'core');
    var ml = mk(defs, 'xl'); B(ml, 'inner', 'legs'); B(ml, 'inner', 'core');
    var mc = mk(defs, 'xc'); B(mc, 'inner', 'core');
    var s = silG(svg); B(s, 'inner', 'tail'); B(s, 'inner', 'legs'); B(s, 'inner', 'core');
    var tc = el(svg, 'g', {mask: 'url(#xtc)'}); B(el(tc, 'g', {mask: 'url(#xti)'}), 'outer', 'tail'); B(tc, 'detail', 'taildet');
    B(el(svg, 'g', {mask: 'url(#xl)'}), 'outer', 'legs');
    B(el(svg, 'g', {mask: 'url(#xc)'}), 'outer', 'core');
    B(el(svg, 'g', {}), 'detail', 'bib');
    B(el(svg, 'g', {}), 'detail', 'shoes');
  } else {
    var mh = mk(defs, 'xh'); B(mh, 'inner', 'head');
    B(silG(svg), 'inner', 'head');
    B(el(svg, 'g', {mask: 'url(#xh)'}), 'outer', 'head');
    B(el(svg, 'g', {}), 'detail', 'face');
  }
}
