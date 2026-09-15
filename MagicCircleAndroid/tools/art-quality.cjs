'use strict';

function applyQuality(svg, id) {
  // Measured native columns; sharp contain-padding is 27px (A04) and 30px (A05).
  const panel = id === 'A04' ? [231, 220, 27] : id === 'A05' ? [237, 230, 30] : null;
  if (!panel || svg.includes('data-quality="panel-separator"')) return svg;
  if (!/\bviewBox="0 0 1024 1024"/.test(svg) || !/<\/svg>\s*$/.test(svg)) {
    throw new Error('Panel cleanup requires the audited 1024 viewBox');
  }
  const [size, x, top] = panel;
  // Half a native pixel covers contour antialiasing; the surrounding artwork is retained.
  const mask = `<rect data-quality="panel-separator" transform="scale(${1024 / size})" x="${x - .5}" y="${top - .5}" width="3" height="177" fill="#000"/>`;
  return svg.replace(/<\/svg>\s*$/, mask + '</svg>');
}

module.exports = { applyQuality };
