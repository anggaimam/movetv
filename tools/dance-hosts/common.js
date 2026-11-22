// Shared helpers for rendering the dance hosts' static layers (no arms) at 440 x 620.
var NS = 'http://www.w3.org/2000/svg', S = 1.7, UID = 0;
var CY = '#22E3F0', PK = '#FF4DA6', YL = '#FFD21F', T = 4.5;
var LAYER = new URLSearchParams(location.search).get('layer') || 'body';
var BL = LAYER === 'body', HL = LAYER === 'head';
function el(p, t, a) { var e = document.createElementNS(NS, t); for (var k in a) e.setAttribute(k, a[k]); p.appendChild(e); return e; }
function root() {
  var svg = el(document.getElementById('root'), 'svg', {width: 440, height: 620,
    viewBox: (200 - 220 / S) + ' ' + (372 - 620 / S) + ' ' + (440 / S) + ' ' + (620 / S)});
  var defs = el(svg, 'defs', {});
  var f = el(defs, 'filter', {id: 'sil', 'color-interpolation-filters': 'sRGB', filterUnits: 'userSpaceOnUse', x: -200, y: -200, width: 800, height: 800});
  el(f, 'feColorMatrix', {type: 'matrix', values: '0 0 0 0 0.0784 0 0 0 0 0.0431 0 0 0 0 0.1804 0 0 0 1 0'});
  return {svg: svg, defs: defs};
}
function mk(defs, id) {
  var m = el(defs, 'mask', {id: id, maskUnits: 'userSpaceOnUse', x: -200, y: -200, width: 800, height: 800});
  el(m, 'rect', {x: -200, y: -200, width: 800, height: 800, fill: '#fff'});
  return el(m, 'g', {});
}
function silG(svg) { return el(svg, 'g', {filter: 'url(#sil)'}); }
var REST = {lu: 20, lf: 10, ru: 20, rf: 10, tilt: 0, lean: 0, brow: 0, bob: 0, talk: 0, wag: 0};
