/**
 * Runs a real async operation but guarantees at least `minMs` elapses before
 * resolving - not by padding artificially when work is slow, but by never
 * letting a fast response feel instant/robotic. Matches the design doc's
 * rule: "nothing AI-generated should ever feel instant."
 */
export async function withMinimumDelay<T>(promise: Promise<T>, minMs: number): Promise<T> {
  const start = Date.now();
  const result = await promise;
  const elapsed = Date.now() - start;
  if (elapsed < minMs) {
    await new Promise((resolve) => setTimeout(resolve, minMs - elapsed));
  }
  return result;
}
