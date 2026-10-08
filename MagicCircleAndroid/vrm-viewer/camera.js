export function fitDistance(width, height, depth, aspect, fov) {
  if (![width, height, depth, aspect, fov].every(Number.isFinite) ||
      width <= 0 || height <= 0 || depth < 0 || aspect <= 0 || fov <= 0 || fov >= 170) {
    throw new Error('Invalid model bounds');
  }
  return Math.max(height, width / aspect) * 0.56 / Math.tan(fov * Math.PI / 360) + depth / 2;
}
