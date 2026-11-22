var PKF = '#FF5CB8';
function sh(p, mode, type, a, col) { var s = mode === 'outer' ? {fill: col, stroke: col, 'stroke-width': 2 * T, 'stroke-linejoin': 'round'} : {fill: '#000', stroke: 'none'}; for (var k in a) s[k] = a[k]; return el(p, type, s); }
function scal(pts, r, sweep) { var d = 'M' + pts[0][0] + ',' + pts[0][1] + ' '; for (var i = 1; i < pts.length; i++) d += 'A' + r + ',' + r + ' 0 0 ' + sweep + ' ' + pts[i][0] + ',' + pts[i][1] + ' '; return d; }
function build(parent, mode, part) {
  var R = {mode: mode, part: part};
  if (part === 'legs') { var o = mode === 'outer'; ['legL', 'legR'].forEach(function (k) { R[k] = el(parent, 'line', {'stroke-linecap': 'butt', fill: 'none', stroke: o ? 'url(#lg)' : '#000', 'stroke-width': o ? 7 + 2 * T : 7}); }); return R; }
  if (part === 'bodydet') { R.footL = el(parent, 'g', {}); R.footR = el(parent, 'g', {});
    [R.footL, R.footR].forEach(function (f) { el(f, 'path', {d: 'M0,-5 L-19,7 Q-13,3 -9,9 Q-4,4 0,10 Q4,4 9,9 Q13,3 19,7 Z', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linejoin': 'round'}); }); }
  R.body = el(parent, 'g', {});
  if (part === 'core') {
    sh(R.body, mode, 'ellipse', {cx: 0, cy: -46, rx: 38, ry: 46}, PKF);
    [[-20, -8], [0, -4], [20, -8]].forEach(function (c) { sh(R.body, mode, 'circle', {cx: c[0], cy: c[1], r: 11}, PKF); });
    el(R.body, 'line', {x1: 0, y1: -84, x2: 0, y2: -150, 'stroke-linecap': 'round', stroke: mode === 'outer' ? PKF : '#000', 'stroke-width': mode === 'outer' ? 14 + 2 * T : 14});
  }
  if (part === 'bodydet') el(R.body, 'path', {d: scal([[-37, -40], [-18.5, -40], [0, -40], [18.5, -40], [37, -40]], 9.6, 0), fill: 'none', stroke: CY, 'stroke-width': 4, 'stroke-linecap': 'round'});
  if (part === 'neck') el(R.body, 'line', {x1: 0, y1: mode === 'outer' ? -91 : -87, x2: 0, y2: -150, 'stroke-linecap': 'butt', stroke: mode === 'outer' ? PKF : '#000', 'stroke-width': mode === 'outer' ? 14 + 2 * T : 14});
  R.head = el(R.body, 'g', {});
  if (part === 'neck') {
    sh(R.head, mode, 'ellipse', {cx: 0, cy: 0, rx: 44, ry: 42}, PKF);
    [[-9, -48, -25], [0, -52, 0], [9, -48, 25]].forEach(function (c) { sh(R.head, mode, 'ellipse', {cx: 0, cy: 0, rx: 5.5, ry: 10, transform: 'translate(' + c[0] + ',' + c[1] + ') rotate(' + c[2] + ')'}, PKF); });
  }
  if (part === 'face') {
    [-16, 16].forEach(function (x) { var e = el(R.head, 'g', {transform: 'translate(' + x + ',-6)'}); el(e, 'circle', {r: 10.5, fill: '#fff'}); el(e, 'circle', {r: 7, fill: '#111'}); el(e, 'circle', {cx: 2.5, cy: -2.5, r: 2.6, fill: '#fff'}); });
    el(R.head, 'path', {d: 'M-25,-22 Q-16,-29 -7,-23', fill: 'none', stroke: YL, 'stroke-width': 4, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: 'M25,-22 Q16,-29 7,-23', fill: 'none', stroke: YL, 'stroke-width': 4, 'stroke-linecap': 'round'});
    [-28, 28].forEach(function (x) { el(R.head, 'ellipse', {cx: x, cy: 10, rx: 6, ry: 3.5, fill: PKF, opacity: 0.6}); });
    el(R.head, 'path', {d: 'M-8,22 Q0,26 8,22 Q5,31 2,37 Q0,40 -2,37 Q-5,31 -8,22 Z', fill: '#6A6A70'});
    el(R.head, 'path', {d: 'M-14,4 Q0,-6 14,4 Q13,20 3,38 Q0,42 -3,38 Q-13,20 -14,4 Z', fill: 'none', stroke: YL, 'stroke-width': 4, 'stroke-linejoin': 'round'});
    el(R.head, 'path', {d: 'M-8,22 Q0,26 8,22', fill: 'none', stroke: YL, 'stroke-width': 3, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: 'M3,27 Q4,31 2,34', fill: 'none', stroke: '#fff', 'stroke-width': 1.6, 'stroke-linecap': 'round', opacity: 0.8});
  }
  return R;
}
function apply(R) {
  var hipY = 270, gy = 352;
  if (R.legL) { [[R.legL, -12, -15], [R.legR, 12, 15]].forEach(function (L) { var x1 = 200 + L[1], y1 = hipY - 20, x2 = 200 + L[2], y2 = gy - 6; if (R.mode === 'inner') { var dx = x2 - x1, dy = y2 - y1, m = Math.sqrt(dx * dx + dy * dy); x2 += dx / m * 4; y2 += dy / m * 4; } L[0].setAttribute('x1', x1); L[0].setAttribute('y1', y1); L[0].setAttribute('x2', x2); L[0].setAttribute('y2', y2); }); return; }
  if (R.footL) { R.footL.setAttribute('transform', 'translate(185,' + gy + ')'); R.footR.setAttribute('transform', 'translate(215,' + gy + ')'); }
  R.body.setAttribute('transform', 'translate(200,' + hipY + ')');
  R.head.setAttribute('transform', 'translate(0,-184)');
}
function draw(svg, defs) {
  var lg = el(defs, 'linearGradient', {id: 'lg', gradientUnits: 'userSpaceOnUse', x1: 0, y1: 300, x2: 0, y2: 330});
  el(lg, 'stop', {offset: 0, 'stop-color': PKF}); el(lg, 'stop', {offset: 1, 'stop-color': YL});
  var B = function (p, m, part) { apply(build(p, m, part)); };
  if (BL) {
    var mc = mk(defs, 'fc'); B(mc, 'inner', 'core');
    var ml = mk(defs, 'fl'); B(ml, 'inner', 'legs'); B(ml, 'inner', 'core');
    var s = silG(svg); B(s, 'inner', 'legs'); B(s, 'inner', 'core');
    B(el(svg, 'g', {mask: 'url(#fl)'}), 'outer', 'legs');
    B(el(svg, 'g', {mask: 'url(#fc)'}), 'outer', 'core');
    B(el(svg, 'g', {}), 'detail', 'bodydet');
  } else {
    var mh = mk(defs, 'fh'); B(mh, 'inner', 'neck');
    B(silG(svg), 'inner', 'neck');
    B(el(svg, 'g', {mask: 'url(#fh)'}), 'outer', 'neck');
    B(el(svg, 'g', {}), 'detail', 'face');
  }
}
