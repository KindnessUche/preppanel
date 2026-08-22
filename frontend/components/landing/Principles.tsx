"use client";

import ScrollReveal from "@/components/ScrollReveal";

const principles = [
  {
    title: "Never the same question twice",
    body: "Every question is generated live and aware of your session history — not pulled from a fixed script.",
  },
  {
    title: "Scored, not just heard",
    body: "An LLM judge grades content and structure against a defined rubric — specific feedback, not a vibe check.",
  },
  {
    title: "Built to keep going",
    body: "Primary provider down? It falls back to a secondary model, then a curated bank. A session never just breaks.",
  },
];

export default function Principles() {
  return (
    <section className="border-y border-line">
      <div className="mx-auto grid max-w-[1400px] grid-cols-1 divide-y divide-line md:grid-cols-3 md:divide-x md:divide-y-0">
        {principles.map((p, i) => (
          <ScrollReveal key={p.title} delay={i * 0.08} className="px-8 py-10 lg:px-10">
            <span className="fig-label">0{i + 1}</span>
            <h3 className="mt-4 text-lg font-medium text-ink">{p.title}</h3>
            <p className="mt-2 text-sm leading-relaxed text-muted">{p.body}</p>
          </ScrollReveal>
        ))}
      </div>
    </section>
  );
}
