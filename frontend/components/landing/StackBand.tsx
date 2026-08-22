"use client";

import MarqueeRow from "@/components/MarqueeRow";
import { stack } from "@/lib/content";

export default function StackBand() {
  return (
    <section className="border-y border-line py-10">
      <div className="mx-auto max-w-[1400px] px-6 lg:px-10">
        <p className="fig-label mb-6">RUNNING ON</p>
      </div>
      <MarqueeRow items={stack} variant="plain" direction="right" speed={26} />
    </section>
  );
}
