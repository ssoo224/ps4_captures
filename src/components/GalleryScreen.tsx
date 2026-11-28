import React, { useMemo, useState } from "react";
import {
  AlertCircle,
  ArrowRight,
  Check,
  Image as ImageIcon,
  Images,
  Play,
  RefreshCw,
  Video,
} from "lucide-react";
import type { Capture, Ps4SessionInfo } from "../types";
import { captureTitle, fmtSize } from "../utils";
import { CenterMessage, PageSwitch } from "./UiPrimitives";

function CaptureTile({
  item,
  sessionId,
  saved,
  onOpen,
}: {
  item: Capture;
  sessionId: string;
  saved: boolean;
  onOpen: (item: Capture) => void;
}) {
  const [thumbFailed, setThumbFailed] = useState(false);

  const thumbUrl = `/api/thumb?sessionId=${encodeURIComponent(
    sessionId
  )}&path=${encodeURIComponent(item.path)}&thumbPath=${encodeURIComponent(
    item.thumbPath
  )}&isVideo=${item.isVideo ? "1" : "0"}`;

  return (
    <button
      type="button"
      onClick={() => onOpen(item)}
      className="relative aspect-square w-full rounded-[22px] bg-[#151D3B] overflow-hidden cursor-pointer group text-right"
    >
      {!thumbFailed ? (
        <img
          src={thumbUrl}
          alt={item.name}
          loading="lazy"
          onError={() => setThumbFailed(true)}
          className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
        />
      ) : (
        <div
          className="w-full h-full flex items-center justify-center"
          style={{
            background: item.isVideo
              ? "linear-gradient(135deg, #3B1E6E, #1B2A6B)"
              : "linear-gradient(135deg, #16305E, #1B1F45)",
          }}
        >
          {item.isVideo ? (
            <Video className="w-11 h-11 text-white/25" />
          ) : (
            <ImageIcon className="w-11 h-11 text-white/25" />
          )}
        </div>
      )}

      {item.isVideo ? (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <div className="w-[46px] h-[46px] rounded-full bg-black/45 border border-white/35 flex items-center justify-center">
            <Play className="w-7 h-7 text-white fill-white" />
          </div>
        </div>
      ) : null}

      <div className="absolute top-2.5 right-2.5 rounded-[10px] bg-black/45 px-2 py-[3px] text-[10px] font-semibold text-white">
        {item.isVideo ? "فيديو" : "صورة"}
      </div>

      {saved ? (
        <div className="absolute top-2.5 left-2.5 w-6 h-6 rounded-full bg-[#34D399] flex items-center justify-center">
          <Check className="w-[15px] h-[15px] text-white" strokeWidth={2.5} />
        </div>
      ) : null}

      <div
        className="absolute inset-x-0 bottom-0 px-3 pt-7 pb-2.5 flex flex-col"
        style={{
          background:
            "linear-gradient(to bottom, transparent, rgba(0,0,0,0.78))",
        }}
      >
        <span className="text-white text-xs font-semibold truncate">
          {captureTitle(item)}
        </span>
        <span className="text-white/70 text-[11px]">{fmtSize(item.size)}</span>
      </div>
    </button>
  );
}

export function GalleryScreen({
  session,
  items,
  scanning,
  scanError,
  downloads,
  page,
  onPageChange,
  onDisconnect,
  onRefresh,
  onOpenViewer,
}: {
  session: Ps4SessionInfo;
  items: Capture[];
  scanning: boolean;
  scanError: string | null;
  downloads: Record<string, number>;
  page: number;
  onPageChange: (page: number) => void;
  onDisconnect: () => void;
  onRefresh: () => void;
  onOpenViewer: (item: Capture) => void;
}) {
  const [tab, setTab] = useState(0);

  const photos = useMemo(() => items.filter((i) => !i.isVideo), [items]);
  const videos = useMemo(() => items.filter((i) => i.isVideo), [items]);
  const shown = tab === 1 ? photos : tab === 2 ? videos : items;

  const tabs = [
    { label: "الكل", count: items.length },
    { label: "الصور", count: photos.length },
    { label: "المقاطع", count: videos.length },
  ];

  return (
    <div className="min-h-screen w-full max-w-6xl mx-auto flex flex-col">
      <header className="flex items-center px-2 py-2.5 gap-1">
        <button
          type="button"
          onClick={onDisconnect}
          title="قطع الاتصال"
          className="p-2.5 rounded-full text-[#F1F5FF] hover:bg-white/10 transition-colors cursor-pointer"
        >
          <ArrowRight className="w-6 h-6" />
        </button>
        <div className="flex-1 min-w-0">
          <h1 className="text-[#F1F5FF] text-[22px] font-extrabold leading-tight">
            معرض الـ PS4
          </h1>
          <div className="flex items-center gap-1.5 mt-0.5">
            <span className="w-2 h-2 rounded-full bg-[#34D399]" />
            <span className="text-[#93A0C8] text-xs" dir="ltr">
              متصل · {session.ip}:{session.port}
            </span>
          </div>
        </div>
        <button
          type="button"
          onClick={onRefresh}
          title="تحديث"
          className="p-2.5 rounded-full text-[#F1F5FF] hover:bg-white/10 transition-colors cursor-pointer"
        >
          <RefreshCw className={`w-6 h-6 ${scanning ? "animate-spin" : ""}`} />
        </button>
      </header>

      <PageSwitch page={page} onPageChange={onPageChange} />

      <div className="flex items-center gap-2 px-4 py-2 overflow-x-auto">
        {tabs.map((t, i) => {
          const sel = tab === i;
          return (
            <button
              key={t.label}
              type="button"
              onClick={() => setTab(i)}
              className={`rounded-full px-4 py-2 text-[13px] font-semibold shrink-0 cursor-pointer transition-all ${
                sel
                  ? "text-white"
                  : "bg-white/[0.07] text-[#93A0C8] hover:text-[#F1F5FF]"
              }`}
              style={
                sel
                  ? { background: "linear-gradient(90deg, #3B82F6, #8B5CF6)" }
                  : undefined
              }
            >
              {t.label} &nbsp;{t.count}
            </button>
          );
        })}
      </div>

      <div className="flex-1 w-full">
        {scanning ? (
          <CenterMessage loading text="جاري فحص الجهاز…" />
        ) : scanError ? (
          <CenterMessage
            icon={AlertCircle}
            text={scanError}
            actionLabel="إعادة المحاولة"
            onAction={onRefresh}
          />
        ) : shown.length === 0 ? (
          <CenterMessage
            icon={Images}
            text={"لا توجد ملفات هنا بعد.\nالتقط لقطة أو سجّل مقطعًا في اللعبة ثم حدّث."}
          />
        ) : (
          <div className="grid grid-cols-[repeat(auto-fill,minmax(150px,1fr))] gap-3 px-4 pt-1.5 pb-12">
            {shown.map((item) => (
              <CaptureTile
                key={item.path}
                item={item}
                sessionId={session.sessionId}
                saved={(downloads[item.path] ?? -1) >= 1}
                onOpen={onOpenViewer}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
