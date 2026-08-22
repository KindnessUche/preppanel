"use client";

import { useEffect } from "react";
import { getReduceMotion, applyReduceMotionClass } from "@/lib/motion-pref";

export default function MotionPrefInit() {
  useEffect(() => {
    applyReduceMotionClass(getReduceMotion());
  }, []);

  return null;
}
