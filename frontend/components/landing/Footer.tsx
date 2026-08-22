"use client";

const columns = [
  { heading: "Product", links: ["Meet the panel", "Practice", "Score", "Research", "Progress", "Pricing"] },
  { heading: "Company", links: ["About", "Roadmap", "Blog", "Careers"] },
  { heading: "Resources", links: ["Docs", "API", "Status", "Changelog"] },
  { heading: "Connect", links: ["X / Twitter", "GitHub", "Contact"] },
];

export default function Footer() {
  return (
    <footer className="border-t border-line">
      <div className="mx-auto max-w-[1400px] px-6 py-16 lg:px-10">
        <div className="grid grid-cols-2 gap-10 sm:grid-cols-4">
          {columns.map((col) => (
            <div key={col.heading}>
              <h5 className="fig-label mb-4 text-faint">{col.heading}</h5>
              <ul className="flex flex-col gap-2.5">
                {col.links.map((link) => (
                  <li key={link}>
                    <a href="#" className="text-sm text-muted transition hover:text-ink">
                      {link}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="mt-16 flex flex-col items-center justify-between gap-6 border-t border-line pt-8 font-mono text-xs text-faint sm:flex-row">
          <div className="flex items-center gap-2">
            <span className="h-1.5 w-1.5 rounded-full bg-accent" />
            <span>PrepPanel — built in the open</span>
          </div>
          <span>© 2026 PrepPanel</span>
        </div>
      </div>
    </footer>
  );
}
