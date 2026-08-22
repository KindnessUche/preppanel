"use client";

interface FieldProps {
  id: string;
  label: string;
  type?: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete?: string;
  placeholder?: string;
  minLength?: number;
  helperText?: string;
}

export default function Field({
  id,
  label,
  type = "text",
  value,
  onChange,
  autoComplete,
  placeholder,
  minLength,
  helperText,
}: FieldProps) {
  return (
    <div>
      <label htmlFor={id} className="fig-label block text-faint">
        {label}
      </label>
      <input
        id={id}
        type={type}
        required
        value={value}
        minLength={minLength}
        autoComplete={autoComplete}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        className="mt-2 w-full rounded-lg border border-line bg-canvas px-4 py-3 text-sm text-ink outline-none transition placeholder:text-faint focus:border-accent focus:ring-1 focus:ring-accent"
      />
      {helperText && <p className="mt-1.5 font-mono text-[11px] text-faint">{helperText}</p>}
    </div>
  );
}
