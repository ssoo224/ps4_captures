import React from "react";
import type { LucideIcon } from "lucide-react";

export function GlassCard({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={`w-full rounded-[22px] bg-white/[0.06] border border-white/[0.09] p-[18px] ${className}`}
    >
      {children}
    </div>
  );
}

export function StepRow({
  n,
  title,
  sub,
}: {
  n: number;
  title: string;
  sub: string;
}) {
  return (
    <div className="flex items-start gap-3">
      <div
        className="w-[30px] h-[30px] shrink-0 rounded-full flex items-center justify-center text-white font-bold text-sm"
        style={{ background: "linear-gradient(135deg, #3B82F6, #8B5CF6)" }}
      >
        {n}
      </div>
      <div className="flex flex-col">
        <span className="text-[#F1F5FF] font-semibold text-sm">{title}</span>
        <span className="text-[#93A0C8] text-xs leading-[17px]">{sub}</span>
      </div>
    </div>
  );
}

export function SegPills({
  options,
  selected,
  onSelect,
}: {
  options: string[];
  selected: number;
  onSelect: (index: number) => void;
}) {
  return (
    <div className="w-full flex rounded-[14px] bg-white/[0.06] p-[3px] gap-1">
      {options.map((label, i) => {
        const sel = i === selected;
        return (
          <button
            key={label}
            type="button"
            onClick={() => onSelect(i)}
            className={`flex-1 rounded-[11px] py-[9px] px-1 text-xs font-semibold text-center transition-all cursor-pointer ${
              sel ? "text-white shadow-sm" : "text-[#93A0C8] hover:text-[#F1F5FF]"
            }`}
            style={
              sel
                ? { background: "linear-gradient(90deg, #3B82F6, #8B5CF6)" }
                : undefined
            }
          >
            {label}
          </button>
        );
      })}
    </div>
  );
}

export function PageSwitch({
  page,
  onPageChange,
}: {
  page: number;
  onPageChange: (page: number) => void;
}) {
  return (
    <div className="px-4 py-1">
      <SegPills
        options={["المعرض", "تثبيت مباشر"]}
        selected={page}
        onSelect={onPageChange}
      />
    </div>
  );
}

export function CenterMessage({
  loading = false,
  icon: Icon,
  text,
  actionLabel,
  onAction,
}: {
  loading?: boolean;
  icon?: LucideIcon;
  text: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <div className="w-full h-full min-h-[320px] flex flex-col items-center justify-center p-8 text-center">
      {loading ? (
        <div className="w-9 h-9 rounded-full border-[3px] border-[#3B82F6]/30 border-t-[#3B82F6] animate-spin" />
      ) : Icon ? (
        <Icon className="w-[52px] h-[52px] text-[#93A0C8]/70" strokeWidth={1.6} />
      ) : null}
      <p className="mt-3.5 text-[#93A0C8] text-sm leading-[21px] whitespace-pre-line max-w-md">
        {text}
      </p>
      {actionLabel && onAction ? (
        <button
          type="button"
          onClick={onAction}
          className="mt-4 rounded-full px-[22px] py-[10px] text-white font-semibold text-sm cursor-pointer transition-opacity hover:opacity-90"
          style={{ background: "linear-gradient(90deg, #3B82F6, #8B5CF6)" }}
        >
          {actionLabel}
        </button>
      ) : null}
    </div>
  );
}
