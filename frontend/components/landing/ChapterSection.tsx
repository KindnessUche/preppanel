"use client";

import ScrollReveal from "@/components/ScrollReveal";
import { statusMeta, type Chapter } from "@/lib/content";

interface ChapterSectionProps {
  chapter: Chapter;
  layout?: "split" | "stacked";
  reverse?: boolean;
  children: React.ReactNode;
}

export default function ChapterSection({
  chapter,
  layout = "split",
  reverse = false,
  children,
}: ChapterSectionProps) {
  const meta = statusMeta[chapter.status];
  const anchorId = `chapter-${chapter.num.replace(".", "-")}`;

  const textBlock = (
    <ScrollReveal className={layout === "split" ? "" : "max-w-2xl"}>
      <div className="flex items-center gap-3">
        <span className="fig-label text-sm text-faint">{chapter.num}</span>
        <span className={`fig-label flex items-center gap-1.5 ${meta.text}`}>
          <span className={`h-1.5 w-1.5 rounded-full ${meta.dot}`} />
          {meta.label}
        </span>
      </div>

      <h2 className="mt-4 text-3xl font-medium leading-tight tracking-tight text-ink sm:text-4xl">
        {chapter.title}
      </h2>

      <p className="mt-5 max-w-lg text-base leading-relaxed text-muted">
        {chapter.body}
      </p>

      <div className="mt-6 flex flex-wrap gap-x-6 gap-y-2">
        {chapter.subnav.map((item) => (
          <span key={item} className="fig-label text-faint">
            {item}
          </span>
        ))}
      </div>
    </ScrollReveal>
  );

  const panelBlock = (
    <ScrollReveal delay={0.12} y={40}>
      {children}
    </ScrollReveal>
  );

  return (
    <section
      id={anchorId}
      className="scroll-mt-24 border-t border-line py-20 lg:py-28"
    >
      <div className="mx-auto max-w-[1400px] px-6 lg:px-10">
        {layout === "split" ? (
          <div
            className={`grid grid-cols-1 items-center gap-14 lg:grid-cols-2 lg:gap-20 ${
              reverse ? "lg:[&>*:first-child]:order-2" : ""
            }`}
          >
            {textBlock}
            {panelBlock}
          </div>
        ) : (
          <div>
            {textBlock}
            <div className="mt-14">{panelBlock}</div>
          </div>
        )}
      </div>
    </section>
  );
}
