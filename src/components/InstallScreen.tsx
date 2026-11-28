import React from "react";
import {
  AlertCircle,
  ArrowRight,
  Check,
  ClipboardPaste,
  CloudDownload,
  Info,
} from "lucide-react";
import { CONN_OPTIONS, type ProxyView, type Ps4SessionInfo } from "../types";
import { fmtEta, fmtSize } from "../utils";
import { GlassCard, PageSwitch, SegPills } from "./UiPrimitives";

export function InstallScreen({
  session,
  page,
  link,
  mode,
  connIdx,
  installPort,
  installBusy,
  installOk,
  installMsg,
  installWarn,
  proxyRunning,
  proxyView,
  onPageChange,
  onLinkChange,
  onModeChange,
  onConnIdxChange,
  onPortChange,
  onStartInstall,
  onStopProxy,
}: {
  session: Ps4SessionInfo;
  page: number;
  link: string;
  mode: number;
  connIdx: number;
  installPort: string;
  installBusy: boolean;
  installOk: boolean | null;
  installMsg: string | null;
  installWarn: boolean;
  proxyRunning: boolean;
  proxyView: ProxyView;
  onPageChange: (page: number) => void;
  onLinkChange: (v: string) => void;
  onModeChange: (v: number) => void;
  onConnIdxChange: (v: number) => void;
  onPortChange: (v: string) => void;
  onStartInstall: () => void;
  onStopProxy: () => void;
}) {
  const handlePaste = async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (text.trim()) onLinkChange(text.trim());
    } catch {
      // Clipboard API may require permission
    }
  };

  const frac =
    proxyView.total > 0
      ? Math.min(1, Math.max(0, proxyView.served / proxyView.total))
      : 0;
  const eta =
    proxyView.speed >= 100 * 1024 && proxyView.total > proxyView.served
      ? Math.floor((proxyView.total - proxyView.served) / proxyView.speed)
      : 0;

  return (
    <div className="min-h-screen w-full max-w-2xl mx-auto flex flex-col pb-10">
      <header className="flex items-center px-2 py-2.5 gap-1">
        <button
          type="button"
          onClick={() => onPageChange(0)}
          className="p-2.5 rounded-full text-[#F1F5FF] hover:bg-white/10 transition-colors cursor-pointer"
        >
          <ArrowRight className="w-6 h-6" />
        </button>
        <div className="flex-1 min-w-0">
          <h1 className="text-[#F1F5FF] text-[22px] font-extrabold leading-tight">
            تثبيت مباشر
          </h1>
          <div className="flex items-center gap-1.5 mt-0.5">
            <span className="w-2 h-2 rounded-full bg-[#34D399]" />
            <span className="text-[#93A0C8] text-xs" dir="ltr">
              متصل · {session.ip}
            </span>
          </div>
        </div>
      </header>

      <PageSwitch page={page} onPageChange={onPageChange} />

      <div className="px-4 py-2 flex flex-col gap-3">
        <GlassCard>
          <h2 className="text-[#F1F5FF] font-semibold text-[15px]">
            رابط اللعبة
          </h2>
          <div className="mt-2.5 relative flex items-start">
            <textarea
              dir="ltr"
              rows={2}
              value={link}
              onChange={(e) => onLinkChange(e.target.value)}
              placeholder="https://…/game.pkg"
              className="w-full rounded-2xl bg-white/[0.04] border border-white/[0.16] focus:border-[#3B82F6] outline-none py-3 pl-11 pr-3.5 text-[#F1F5FF] text-[13px] placeholder:text-[#93A0C8]/60 resize-y min-h-[70px]"
            />
            <button
              type="button"
              onClick={handlePaste}
              title="لصق"
              className="absolute left-2 top-2 p-2 rounded-lg text-[#3B82F6] hover:bg-white/10 cursor-pointer"
            >
              <ClipboardPaste className="w-5 h-5" />
            </button>
          </div>
          <p className="mt-2 text-[#93A0C8] text-xs leading-[18px]">
            رابط مباشر لملف .pkg. تكدر تشارك الرابط من المتصفح لهذا التطبيق
            وينلصق هنا تلقائيًا.
          </p>
        </GlassCard>

        <GlassCard>
          <h2 className="text-[#F1F5FF] font-semibold text-[15px]">
            طريقة النقل
          </h2>
          <div className="mt-2.5">
            <SegPills
              options={["مباشر", "عبر السيرفر الوسيط (أسرع)"]}
              selected={mode}
              onSelect={onModeChange}
            />
          </div>
          <p className="mt-2 text-[#93A0C8] text-xs leading-[18px]">
            {mode === 0
              ? "الـ PS4 ينزّل الرابط من الإنترنت بنفسه. أبسط طريقة، والسيرفر ما يشتغل."
              : "السيرفر الوسيط يسحب الملف بعدة اتصالات متوازية ثابتة مع إعادة المحاولة وتجديد الروابط تلقائيًا ويمرره للـ PS4 بأقصى سرعة."}
          </p>

          {mode === 1 ? (
            <>
              <h3 className="mt-4 text-[#F1F5FF] font-semibold text-[13px]">
                عدد الاتصالات المتوازية
              </h3>
              <div className="mt-2">
                <SegPills
                  options={CONN_OPTIONS.map((c) => `${c}`)}
                  selected={connIdx}
                  onSelect={onConnIdxChange}
                />
              </div>
              <p className="mt-2 text-[#93A0C8] text-[11px] leading-[17px]">
                السرعة تتكيف تلقائيًا إذا السيرفر قيّد الاتصالات، وترجع ترتفع تلقائيًا عند الاستقرار.
              </p>
            </>
          ) : null}
        </GlassCard>

        <GlassCard>
          <div className="flex items-center gap-3">
            <div className="flex-1">
              <div className="text-[#F1F5FF] font-semibold text-sm">
                منفذ أداة التثبيت
              </div>
              <div className="text-[#93A0C8] text-xs">الافتراضي 12800</div>
            </div>
            <input
              type="text"
              inputMode="numeric"
              dir="ltr"
              value={installPort}
              onChange={(e) => onPortChange(e.target.value)}
              className="w-[110px] rounded-[14px] bg-white/[0.04] border border-white/[0.16] focus:border-[#3B82F6] outline-none py-2.5 px-3 text-[#F1F5FF] text-base text-center"
            />
          </div>
        </GlassCard>

        <button
          type="button"
          disabled={installBusy}
          onClick={onStartInstall}
          className="w-full h-[56px] rounded-[18px] flex items-center justify-center gap-2.5 text-white font-bold text-[17px] cursor-pointer disabled:opacity-80"
          style={{ background: "linear-gradient(90deg, #3B82F6, #8B5CF6)" }}
        >
          {installBusy ? (
            <span className="w-[22px] h-[22px] rounded-full border-[2.5px] border-white/30 border-t-white animate-spin" />
          ) : (
            <>
              <CloudDownload className="w-[22px] h-[22px]" />
              <span>إرسال للـ PS4</span>
            </>
          )}
        </button>

        {installOk !== null && installMsg ? (
          <div
            className="w-full flex items-start gap-2.5 rounded-2xl p-3.5 border"
            style={{
              backgroundColor: installWarn
                ? "rgba(255,176,32,0.12)"
                : installOk
                ? "rgba(52,211,153,0.12)"
                : "rgba(248,113,113,0.12)",
              borderColor: installWarn
                ? "rgba(255,176,32,0.35)"
                : installOk
                ? "rgba(52,211,153,0.35)"
                : "rgba(248,113,113,0.35)",
              color: installWarn
                ? "#FFB020"
                : installOk
                ? "#34D399"
                : "#F87171",
            }}
          >
            {installWarn ? (
              <Info className="w-[22px] h-[22px] shrink-0" />
            ) : installOk ? (
              <Check className="w-[22px] h-[22px] shrink-0" />
            ) : (
              <AlertCircle className="w-[22px] h-[22px] shrink-0" />
            )}
            <p className="text-[13px] leading-[19px]">{installMsg}</p>
          </div>
        ) : null}

        {proxyRunning ? (
          <GlassCard>
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-[#34D399]" />
              <span className="text-[#34D399] font-semibold text-[13px] flex-1">
                السيرفر الوسيط شغّال
              </span>
              <button
                type="button"
                onClick={onStopProxy}
                className="rounded-full border border-[#F87171]/50 px-3.5 py-1.5 text-[#F87171] text-xs font-semibold hover:bg-[#F87171]/10 cursor-pointer"
              >
                إيقاف
              </button>
            </div>

            <div
              className="mt-2.5 text-[#F1F5FF] text-xs truncate text-left"
              dir="ltr"
            >
              {proxyView.name}
            </div>

            <div className="mt-2.5 w-full h-2 rounded-full bg-white/[0.12] overflow-hidden">
              <div
                className="h-full bg-[#3B82F6] transition-all duration-300"
                style={{ width: `${Math.round(frac * 100)}%` }}
              />
            </div>

            <div className="mt-2.5 flex items-center justify-between">
              <span className="text-[#F1F5FF] text-xl font-extrabold" dir="ltr">
                {proxyView.speed >= 1024
                  ? `${fmtSize(proxyView.speed)}/s`
                  : "—"}
              </span>
              <span className="text-[#93A0C8] text-[13px]">
                {eta > 0 ? `المتبقي ${fmtEta(eta)}` : ""}
              </span>
            </div>

            <div className="text-[#93A0C8] text-xs mt-1">
              {fmtSize(proxyView.served)} / {fmtSize(proxyView.total)} ·{" "}
              {proxyView.activeConns}/{proxyView.effectiveMax || 0} اتصال نشط
            </div>
            <div className="text-[#93A0C8] text-xs mt-0.5">
              طلبات الـ PS4 للسيرفر: {proxyView.requests}
            </div>

            {proxyView.error ? (
              <div className="text-[#F87171] text-[11px] mt-1">
                آخر خطأ: {proxyView.error}
              </div>
            ) : null}

            {proxyView.served < 5 * 1024 * 1024 && proxyView.speed < 1024 ? (
              <p className="mt-1.5 text-[#FFB020] text-xs leading-[18px]">
                بانتظار الـ PS4 يبدأ السحب… شوف إشعارات الجهاز (التنزيلات).
                المرحلة الأولى قد تأخذ شوية.
              </p>
            ) : null}

            {proxyView.total > 0 && proxyView.served >= proxyView.total ? (
              <p className="mt-1.5 text-[#34D399] text-xs">
                اكتمل النقل ✓ تكدر توقف السيرفر.
              </p>
            ) : null}
          </GlassCard>
        ) : null}
      </div>
    </div>
  );
}
