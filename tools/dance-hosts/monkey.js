var OR = '#FF9A3C';
function shape(p, mode, type, a) { var s = mode === 'outer' ? {fill: CY, stroke: CY, 'stroke-width': 2 * T, 'stroke-linejoin': 'round'} : {fill: '#000', stroke: 'none'}; for (var k in a) s[k] = a[k]; return el(p, type, s); }
function earPath(side, cx, cy, r) {
  var seq = []; for (var a = 0; a < 360; a += 1.5) { var x = side * cx + r * Math.cos(a * Math.PI / 180), y = cy + r * Math.sin(a * Math.PI / 180); var v = (x * x) / (62 * 62) + (y * y) / (58 * 58); seq.push({x: x, y: y, o: v >= 0.93}); }
  var n = seq.length, start = 0; for (var i = 0; i < n; i++) { if (!seq[i].o && seq[(i + 1) % n].o) { start = (i + 1) % n; break; } }
  var d = ''; for (var j = 0; j < n; j++) { var s = seq[(start + j) % n]; if (!s.o) break; d += (d ? 'L' : 'M') + s.x.toFixed(1) + ',' + s.y.toFixed(1) + ' '; }
  return d;
}
function build(parent, mode) {
  var R = {mode: mode}, body = (mode === 'outer' || mode === 'inner');
  if (body && BL) {
    R.legL = el(parent, 'line', {'stroke-linecap': 'round', fill: 'none', stroke: mode === 'outer' ? CY : '#000', 'stroke-width': mode === 'outer' ? 20 + 2 * T : 20});
    R.legR = el(parent, 'line', {'stroke-linecap': 'round', fill: 'none', stroke: mode === 'outer' ? CY : '#000', 'stroke-width': mode === 'outer' ? 20 + 2 * T : 20});
    R.footL = el(parent, 'g', {}); R.footR = el(parent, 'g', {});
    shape(R.footL, mode, 'ellipse', {cx: 0, cy: 0, rx: 22, ry: 10}); shape(R.footR, mode, 'ellipse', {cx: 0, cy: 0, rx: 22, ry: 10});
  }
  R.body = el(parent, 'g', {});
  if (mode === 'back' && BL) R.tail = el(R.body, 'path', {d: 'M0,0 c20,4 40,-4 42,-24 c2,-17 -17,-22 -24,-9 c-4,9 7,15 13,8', fill: 'none', stroke: PK, 'stroke-width': 7, 'stroke-linecap': 'round', 'stroke-linejoin': 'round'});
  if (body && BL) shape(R.body, mode, 'rect', {x: -34, y: -96, width: 68, height: 104, rx: 30});
  R.head = el(R.body, 'g', {});
  if (mode === 'back' && HL) {
    [1, -1].forEach(function (s) {
      el(R.head, 'path', {d: earPath(s, 64, -14, 40), fill: 'none', stroke: PK, 'stroke-width': 6, 'stroke-linecap': 'round', 'stroke-linejoin': 'round'});
      el(R.head, 'path', {d: earPath(s, 64, -14, 20), fill: 'none', stroke: PK, 'stroke-width': 6, 'stroke-linecap': 'round', 'stroke-linejoin': 'round'});
    });
  } else if (body && HL) {
    shape(R.head, mode, 'path', {d: 'M-16,-52 q-8,-26 10,-34 q3,12 12,16 q10,-16 22,-12 q-5,18 -12,30 z'});
    shape(R.head, mode, 'ellipse', {cx: 0, cy: 0, rx: 62, ry: 58});
  } else if (mode === 'detail' && HL) {
    [-19, 19].forEach(function (x) { var e = el(R.head, 'g', {transform: 'translate(' + x + ',-6)'}); el(e, 'circle', {r: 16, fill: 'none', stroke: YL, 'stroke-width': 4.5}); el(e, 'circle', {r: 10, fill: '#B9B9B1'}); el(e, 'circle', {r: 5, fill: '#55554E'}); el(e, 'circle', {cx: 2, cy: -2.5, r: 2, fill: '#fff'}); });
    el(R.head, 'path', {d: 'M-34,-33 Q-21,-45 -6,-36', fill: 'none', stroke: OR, 'stroke-width': 6, 'stroke-linecap': 'round'});
    el(R.head, 'path', {d: 'M34,-33 Q21,-45 6,-36', fill: 'none', stroke: OR, 'stroke-width': 6, 'stroke-linecap': 'round'});
    el(R.head, 'ellipse', {cx: 0, cy: 24, rx: 26, ry: 19, fill: 'none', stroke: YL, 'stroke-width': 4.5});
    el(R.head, 'ellipse', {cx: -7, cy: 16, rx: 3, ry: 2.2, fill: 'none', stroke: YL, 'stroke-width': 2.5});
    el(R.head, 'ellipse', {cx: 7, cy: 16, rx: 3, ry: 2.2, fill: 'none', stroke: YL, 'stroke-width': 2.5});
    el(R.head, 'ellipse', {cx: 0, cy: 30, rx: 10, ry: 6, fill: PK});
  }
  return R;
}
function apply(R) {
  var hipY = 268, gy = 352;
  if (R.legL) {
    R.legL.setAttribute('x1', 186); R.legL.setAttribute('y1', hipY - 6); R.legL.setAttribute('x2', 172); R.legL.setAttribute('y2', gy - 4);
    R.legR.setAttribute('x1', 214); R.legR.setAttribute('y1', hipY - 6); R.legR.setAttribute('x2', 228); R.legR.setAttribute('y2', gy - 4);
    R.footL.setAttribute('transform', 'translate(166,' + gy + ')'); R.footR.setAttribute('transform', 'translate(234,' + gy + ')');
  }
  R.body.setAttribute('transform', 'translate(200,' + hipY + ')');
  if (R.tail) R.tail.setAttribute('transform', 'translate(32,-14)');
  R.head.setAttribute('transform', 'translate(0,-150)');
}
function draw(svg, defs) {
  var B = function (p, m) { apply(build(p, m)); };
  B(mk(defs, 'mi'), 'inner');
  B(el(svg, 'g', {}), 'back');
  B(silG(svg), 'inner');
  B(el(svg, 'g', {mask: 'url(#mi)'}), 'outer');
  B(el(svg, 'g', {}), 'detail');
}
