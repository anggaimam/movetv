var LW = 12;
function shape(p, mode, type, a) { var s = mode === 'outer' ? {fill: CY, stroke: CY, 'stroke-width': 2 * T, 'stroke-linejoin': 'round'} : {fill: '#000', stroke: 'none'}; for (var k in a) s[k] = a[k]; return el(p, type, s); }
function circles(cx, cy, rx, ry, n, r, a0, a1) { var o = []; for (var i = 0; i < n; i++) { var a = (a0 + (a1 - a0) * (n === 1 ? 0 : i / (n - 1))) * Math.PI / 180; o.push({x: cx + rx * Math.cos(a), y: cy + ry * Math.sin(a), r: r}); } return o; }
var HEADWOOL = circles(0, -26, 40, 32, 7, 16, -165, -15);
var BODYWOOL = circles(0, -52, 36, 44, 12, 13, 0, 330);
function drawC(p, mode, list) { list.forEach(function (c) { shape(p, mode, 'circle', {cx: c.x.toFixed(1), cy: c.y.toFixed(1), r: c.r}); }); }
function outsideHead(x, y) { if ((x * x) / (56 * 56) + (y * y) / (50 * 50) < 1) return false; for (var i = 0; i < HEADWOOL.length; i++) { var c = HEADWOOL[i], dx = x - c.x, dy = y - c.y; if (dx * dx + dy * dy < c.r * c.r) return false; } return true; }
function earPath(side, cx, cy, rx, ry, rot) {
  var seq = [], R = side * rot * Math.PI / 180; for (var a = 0; a < 360; a += 2) { var q = a * Math.PI / 180, ux = rx * Math.cos(q), uy = ry * Math.sin(q); var x = side * cx + ux * Math.cos(R) - uy * Math.sin(R), y = cy + ux * Math.sin(R) + uy * Math.cos(R); seq.push({x: x, y: y, o: outsideHead(x, y)}); }
  var n = seq.length, start = -1, all = true; for (var i = 0; i < n; i++) { if (!seq[i].o) all = false; if (!seq[i].o && seq[(i + 1) % n].o) { start = (i + 1) % n; break; } }
  var d = ''; if (all) { seq.forEach(function (s, j) { d += (j ? 'L' : 'M') + s.x.toFixed(1) + ',' + s.y.toFixed(1) + ' '; }); return d + 'Z'; }
  for (var j = 0; j < n; j++) { var s = seq[(start + j) % n]; if (!s.o) break; d += (d ? 'L' : 'M') + s.x.toFixed(1) + ',' + s.y.toFixed(1) + ' '; }
  return d;
}
function fringePath() { var d = '', N = 5; for (var i = 0; i <= N; i++) { var x = -53 + i * 106 / N, y = -30 - 10 * (1 - (x / 53) * (x / 53)); d += (i ? 'A11.5,11.5 0 0 0 ' : 'M') + x.toFixed(1) + ',' + y.toFixed(1) + ' '; } return d; }
function build(parent, mode) {
  var R = {mode: mode}, body = (mode === 'outer' || mode === 'inner');
  if (mode === 'detail' && BL) {
    R.footL = el(parent, 'g', {}); R.footR = el(parent, 'g', {});
    [R.footL, R.footR].forEach(function (f) { el(f, 'path', {d: 'M-17,2 C-17,-15 17,-15 17,2 L4,2 L0,-4 L-4,2 Z', fill: 'none', stroke: PK, 'stroke-width': 5, 'stroke-linejoin': 'round'}); });
  }
  R.body = el(parent, 'g', {});
  if (body && BL) { shape(R.body, mode, 'ellipse', {cx: 0, cy: -52, rx: 36, ry: 44}); drawC(R.body, mode, BODYWOOL); }
  R.head = el(R.body, 'g', {});
  if (mode === 'back' && HL) {
    [1, -1].forEach(function (s) {
      el(R.head, 'path', {d: earPath(s, 78, -4, 27, 12, 25), fill: 'none', stroke: PK, 'stroke-width': 5.5, 'stroke-linecap': 'round', 'stroke-linejoin': 'round'});
      el(R.head, 'path', {d: earPath(s, 79, -2, 15, 5, 25), fill: 'none', stroke: PK, 'stroke-width': 4, 'stroke-linecap': 'round', 'stroke-linejoin': 'round'});
    });
  } else if (body && HL) {
    shape(R.head, mode, 'ellipse', {cx: 0, cy: 0, rx: 56, ry: 50});
    drawC(R.head, mode, HEADWOOL);
  } else if (mode === 'detail' && HL) {
    el(R.head, 'path', {d: fringePath(), fill: 'none', stroke: CY, 'stroke-width': T, 'stroke-linecap': 'round', 'stroke-linejoin': 'round'});
    [-19, 19].forEach(function (x) { var e = el(R.head, 'g', {transform: 'translate(' + x + ',-3)'}); el(e, 'circle', {r: 12.5, fill: 'none', stroke: YL, 'stroke-width': 3.5}); el(e, 'circle', {r: 9.5, fill: '#fff'}); el(e, 'circle', {r: 5.5, fill: '#111'}); el(e, 'circle', {cx: 2, cy: -2.5, r: 2, fill: '#fff'}); });
    el(R.head, 'path', {d: 'M-28,-20 Q-19,-27 -10,-21', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: 'M28,-20 Q19,-27 10,-21', fill: 'none', stroke: YL, 'stroke-width': 4.5, 'stroke-linecap': 'round'});
    el(R.head, 'ellipse', {cx: 0, cy: 26, rx: 22, ry: 17, fill: 'none', stroke: YL, 'stroke-width': 4.5});
    el(R.head, 'path', {d: 'M-7,15 Q0,12 7,15 Q4,22 0,23 Q-4,22 -7,15 Z', fill: 'none', stroke: YL, 'stroke-width': 3, 'stroke-linejoin': 'round'});
    el(R.head, 'path', {d: 'M-10,28 Q0,31 10,28 Q8,39 0,39 Q-8,39 -10,28 Z', fill: PK, stroke: PK, 'stroke-width': 1.5, 'stroke-linejoin': 'round'});
  }
  return R;
}
function buildLegs(parent, mode) { var o = mode === 'outer'; var R = {mode: mode}; ['L', 'R'].forEach(function (s) { R[s] = el(parent, 'line', {'stroke-linecap': 'butt', fill: 'none', stroke: o ? CY : '#000', 'stroke-width': o ? LW + 2 * T : LW}); }); return R; }
function applyLegs(R) { var hipY = 272, gy = 352;
  [[R.L, -20, -25], [R.R, 20, 25]].forEach(function (L) { var x1 = 200 + L[1], y1 = hipY - 24, x2 = 200 + L[2], y2 = gy - 7; if (R.mode === 'inner') { var dx = x2 - x1, dy = y2 - y1, m = Math.sqrt(dx * dx + dy * dy); x2 += dx / m * 4; y2 += dy / m * 4; } L[0].setAttribute('x1', x1); L[0].setAttribute('y1', y1); L[0].setAttribute('x2', x2); L[0].setAttribute('y2', y2); }); }
function apply(R) {
  var hipY = 272, gy = 352;
  if (R.footL) { R.footL.setAttribute('transform', 'translate(175,' + gy + ')'); R.footR.setAttribute('transform', 'translate(225,' + gy + ')'); }
  R.body.setAttribute('transform', 'translate(200,' + hipY + ')');
  R.head.setAttribute('transform', 'translate(0,-152)');
}
function draw(svg, defs) {
  var B = function (p, m) { apply(build(p, m)); }, L = function (p, m) { if (BL) applyLegs(buildLegs(p, m)); };
  var mB = mk(defs, 'sb'); B(mB, 'inner');
  var mL = mk(defs, 'sl'); L(mL, 'inner'); B(mL, 'inner');
  B(el(svg, 'g', {}), 'back');
  var s = silG(svg); L(s, 'inner'); B(s, 'inner');
  L(el(svg, 'g', {mask: 'url(#sl)'}), 'outer');
  B(el(svg, 'g', {mask: 'url(#sb)'}), 'outer');
  B(el(svg, 'g', {}), 'detail');
}
