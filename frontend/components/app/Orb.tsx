"use client";

export type OrbState = "idle" | "thinking" | "speaking" | "listening";

interface OrbProps {
  state?: OrbState;
  size?: number;
  color?: string;
  label?: string;
}

/**
 * Abstract breathing orb - the product's one deliberate signature animation.
 * Idle: slow ambient breathing pulse. Thinking: faster pulse, slightly dimmed.
 * Speaking: outward ripple. Listening: soft steady glow, no pulse.
 * Respects prefers-reduced-motion by falling back to a static glow.
 */
export default function Orb({ state = "idle", size = 56, color = "#5b8def", label }: OrbProps) {
  return (
    <div className="flex flex-col items-center gap-2">
      <div
        className="orb relative flex items-center justify-center rounded-full"
        style={{ width: size, height: size }}
        data-state={state}
      >
        <span
          className="orb-core absolute inset-0 rounded-full"
          style={{ background: color }}
        />
        <span
          className="orb-ring absolute inset-0 rounded-full border"
          style={{ borderColor: color }}
        />
        {state === "speaking" && (
          <span
            className="orb-ripple absolute inset-0 rounded-full border"
            style={{ borderColor: color }}
          />
        )}
      </div>
      {label && <span className="fig-label text-faint">{label}</span>}

      <style jsx>{`
        .orb-core {
          opacity: 0.55;
          filter: blur(1px);
        }
        .orb-ring {
          opacity: 0.9;
        }
        .orb[data-state="idle"] .orb-core {
          animation: breathe 3.6s ease-in-out infinite;
        }
        .orb[data-state="thinking"] .orb-core {
          animation: breathe 1.1s ease-in-out infinite;
          opacity: 0.4;
        }
        .orb[data-state="listening"] .orb-core {
          opacity: 0.7;
        }
        .orb[data-state="speaking"] .orb-core {
          animation: breathe 1.6s ease-in-out infinite;
        }
        .orb-ripple {
          animation: ripple 1.4s ease-out infinite;
        }
        @keyframes breathe {
          0%, 100% { transform: scale(0.85); opacity: 0.35; }
          50% { transform: scale(1.05); opacity: 0.6; }
        }
        @keyframes ripple {
          0% { transform: scale(0.9); opacity: 0.6; }
          100% { transform: scale(1.6); opacity: 0; }
        }
        @media (prefers-reduced-motion: reduce) {
          .orb-core, .orb-ripple {
            animation: none !important;
            opacity: 0.5 !important;
            transform: none !important;
          }
        }
      `}</style>
    </div>
  );
}
