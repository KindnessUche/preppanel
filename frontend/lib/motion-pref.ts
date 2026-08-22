const KEY = "preppanel_reduce_motion";

export function getReduceMotion(): boolean {
  try {
    return localStorage.getItem(KEY) === "true";
  } catch {
    return false;
  }
}

export function setReduceMotion(value: boolean) {
  localStorage.setItem(KEY, String(value));
  applyReduceMotionClass(value);
}

export function applyReduceMotionClass(value: boolean) {
  document.documentElement.classList.toggle("reduce-motion", value);
}
