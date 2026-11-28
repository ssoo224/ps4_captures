import React, { useState } from "react";
import { Gamepad2, Info, Wifi } from "lucide-react";
import { GlassCard, StepRow } from "./UiPrimitives";

export function ConnectScreen({
  savedIp,
  connecting,
  status,
  error,
  onConnect,
}: {
  savedIp: string;
  connecting: boolean;
  status: string;
  error: string | null;
  onConnect: (ip: string) => void;
}) {
  const [ip, setIp] = useState(savedIp);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!connecting) onConnect(ip);
  };

  return (
    <div className="min-h-screen w-full flex flex-col items-center px-[22px] overflow-y-auto">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-[480px] flex flex-col items-center"
      >
        <div className="h-[34px]" />

        <div className="relative flex items-center justify-center w-[140px] h-[140px]">
          <div
            className="absolute inset-0 rounded-full animate-pulse-glow"
            style={{
              background:
                "radial-gradient(circle, rgba(59,130,246,0.38) 0%, transparent 70%)",
            }}
          />
          <div
            className="relative w-[88px] h-[88px] rounded-[28px] flex items-center justify-center shadow-lg"
            style={{ background: "linear-gradient(135deg, #3B82F6, #8B5CF6)" }}
          >
            <Gamepad2 className="w-[46px] h-[46px] text-white" strokeWidth={2} />
          </div>
        </div>

        <h1 className="mt-3.5 text-[#F1F5FF] text-[32px] font-extrabold tracking-tight">
          PS4 Captures
        </h1>
        <p className="mt-1.5 text-[#93A0C8] text-sm leading-[21px] text-center">
          استعرض لقطات ومقاطع جهازك مباشرةً من هاتفك،
          <br />
          واحفظها بضغطة زر.
        </p>

        <div className="h-6" />
        <GlassCard>
          <div className="flex flex-col gap-3.5">
            <StepRow
              n={1}
              title="فعّل خادم FTP"
              sub="من إعدادات GoldHEN على جهاز PS4"
            />
            <StepRow
              n={2}
              title="أدخل عنوان IP"
              sub="نجرّب كل المنافذ المعروفة تلقائيًا"
            />
            <StepRow
              n={3}
              title="تصفّح واحفظ"
              sub="كأنك تتصفح معرض هاتفك، والتحميل من داخل كل صورة أو مقطع"
            />
          </div>
        </GlassCard>

        <div className="h-[22px]" />
        <div className="w-full">
          <label className="block text-xs text-[#93A0C8] mb-1.5 px-1">
            عنوان IP الـ PS4
          </label>
          <div className="relative flex items-center">
            <Wifi className="w-5 h-5 text-[#93A0C8] absolute right-4 pointer-events-none" />
            <input
              type="text"
              inputMode="decimal"
              dir="ltr"
              value={ip}
              onChange={(e) =>
                setIp(e.target.value.replace(/[^0-9.]/g, ""))
              }
              placeholder="192.168.1.20"
              className="w-full rounded-[18px] bg-white/[0.04] border border-white/[0.16] focus:border-[#3B82F6] outline-none py-4 pr-12 pl-4 text-[#F1F5FF] text-lg font-semibold placeholder:text-[#93A0C8]/60 transition-colors text-left"
            />
          </div>
        </div>

        <div className="h-3" />
        <div className="w-full flex items-center gap-2.5 rounded-2xl bg-[#FFB020]/[0.12] border border-[#FFB020]/35 p-3.5">
          <Info className="w-[22px] h-[22px] text-[#FFB020] shrink-0" />
          <p className="text-[#FFB020] text-[13px] leading-[19px] font-medium">
            مهم: يجب أن يكون الـ PS4 والجهاز متصلين بنفس شبكة الواي فاي.
          </p>
        </div>

        {error ? (
          <p className="mt-3 text-[#F87171] text-[13px] leading-[19px] text-center">
            {error}
          </p>
        ) : null}

        <div className="h-[18px]" />
        <button
          type="submit"
          disabled={connecting}
          className="w-full h-[56px] rounded-[18px] flex items-center justify-center text-white font-bold text-[17px] cursor-pointer disabled:opacity-80 transition-opacity"
          style={{ background: "linear-gradient(90deg, #3B82F6, #8B5CF6)" }}
        >
          {connecting ? (
            <span className="flex items-center gap-3">
              <span className="w-5 h-5 rounded-full border-[2.5px] border-white/30 border-t-white animate-spin" />
              <span className="text-sm font-semibold">{status}</span>
            </span>
          ) : (
            "اتصال"
          )}
        </button>

        <p className="mt-[18px] text-[#93A0C8]/70 text-[11px] text-center">
          يعمل عبر FTP (GoldHEN) — بدون حقن أو صلاحيات خاصة.
        </p>
        <div className="h-6" />
      </form>
    </div>
  );
}
