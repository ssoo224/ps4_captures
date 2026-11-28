import React, { useEffect, useState } from "react";
import { Check, Download, Play, X } from "lucide-react";
import type { Capture } from "../types";
import { captureTitle, fmtSize } from "../utils";

export function ViewerModal({
  item,
  sessionId,
  progress,
  savedObjectUrl,
  onClose,
  onDownload,
}: {
  item: Capture;
  sessionId: string;
  progress: number | undefined;
  savedObjectUrl: string | undefined;
  onClose: () => void;
  onDownload: (item: Capture) => void;
}) {
  const [scale, setScale] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const [fullLoaded, setFullLoaded] = useState(false);
  const [playingInline, setPlayingInline] = useState(false);

  useEffect(() => {
    setScale(1);
    setOffset({ x: 0, y: 0 });
    setFullLoaded(false);
    setPlayingInline(false);
  }, [item.path]);

  const running = progress !== undefined && progress >= 0 && progress < 1;
  const finished = progress !== undefined && progress >= 1;
  const canPlay = Boolean(savedObjectUrl);

  const fullUrl = `/api/file?sessionId=${encodeURIComponent(
    sessionId
  )}&path=${encodeURIComponent(item.path)}&name=${encodeURIComponent(
    item.name
  )}&size=${item.size}`;

  const thumbUrl = `/api/thumb?sessionId=${encodeURIComponent(
    sessionId
  )}&path=${encodeURIComponent(item.path)}&thumbPath=${encodeURIComponent(
    item.thumbPath
  )}&isVideo=${item.isVideo ? "1" : "0"}`;

  let label = "حفظ في المعرض";
  if (progress !== undefined) {
    if (progress < 0) label = "إعادة المحاولة";
    else if (progress >= 1) label = "تم الحفظ ✓";
    else
      label = `جاري التحميل ${Math.floor(progress * 100)}% · ${fmtSize(
        Math.floor(progress * item.size)
      )} / ${fmtSize(item.size)}`;
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/[0.97] flex flex-col justify-between select-none">
      {/* Top Bar */}
      <div
        className="relative z-10 flex items-center px-2 py-2 gap-1"
        style={{
          background:
            "linear-gradient(to bottom, rgba(0,0,0,0.7), transparent)",
        }}
      >
        <button
          type="button"
          onClick={onClose}
          className="p-2.5 rounded-full text-white hover:bg-white/10 cursor-pointer"
        >
          <X className="w-6 h-6" />
        </button>
        <div className="flex-1 min-w-0">
          <div className="text-white font-semibold text-[15px] truncate">
            {captureTitle(item)}
          </div>
          <div className="text-white/70 text-xs">{fmtSize(item.size)}</div>
        </div>
      </div>

      {/* Media Center */}
      <div className="flex-1 relative flex items-center justify-center overflow-hidden px-4">
        {item.isVideo ? (
          <div className="w-full max-w-xl flex flex-col items-center">
            <div
              className="relative w-full aspect-video rounded-[22px] overflow-hidden flex items-center justify-center"
              style={{
                background: "linear-gradient(135deg, #3B1E6E, #1B2A6B)",
              }}
            >
              {playingInline && savedObjectUrl ? (
                <video
                  src={savedObjectUrl}
                  controls
                  autoPlay
                  className="w-full h-full object-contain bg-black"
                />
              ) : (
                <>
                  <img
                    src={thumbUrl}
                    alt={item.name}
                    onError={(e) => {
                      e.currentTarget.style.display = "none";
                    }}
                    className="w-full h-full object-cover"
                  />
                  <button
                    type="button"
                    disabled={!canPlay}
                    onClick={() => setPlayingInline(true)}
                    className="absolute w-16 h-16 rounded-full bg-black/45 border border-white/35 flex items-center justify-center cursor-pointer disabled:opacity-60 disabled:cursor-default"
                  >
                    <Play className="w-[38px] h-[38px] text-white fill-white" />
                  </button>
                </>
              )}
            </div>
            <p className="mt-4 text-[#93A0C8] text-[13px] leading-[19px] text-center">
              {canPlay
                ? "تم الحفظ. اضغط تشغيل لفتحه."
                : "المقطع كبير ولا ينعرض مباشرة. حمّله ثم شغّله من جهازك."}
            </p>
          </div>
        ) : (
          <div
            className="w-full h-full flex items-center justify-center overflow-hidden"
            onWheel={(e) => {
              const next = Math.min(6, Math.max(1, scale - e.deltaY * 0.002));
              setScale(next);
              if (next <= 1) setOffset({ x: 0, y: 0 });
            }}
            onPointerDown={(e) => {
              if (scale <= 1) return;
              setDragging(true);
              setDragStart({ x: e.clientX - offset.x, y: e.clientY - offset.y });
            }}
            onPointerMove={(e) => {
              if (!dragging) return;
              setOffset({
                x: e.clientX - dragStart.x,
                y: e.clientY - dragStart.y,
              });
            }}
            onPointerUp={() => setDragging(false)}
            onPointerCancel={() => setDragging(false)}
            onDoubleClick={() => {
              if (scale > 1) {
                setScale(1);
                setOffset({ x: 0, y: 0 });
              } else {
                setScale(2.5);
              }
            }}
          >
            <img
              src={fullUrl}
              alt={item.name}
              onLoad={() => setFullLoaded(true)}
              draggable={false}
              style={{
                transform: `translate(${offset.x}px, ${offset.y}px) scale(${scale})`,
              }}
              className="max-w-full max-h-full object-contain transition-transform duration-75"
            />
            {!fullLoaded ? (
              <div className="absolute w-[34px] h-[34px] rounded-full border-[3px] border-[#3B82F6]/30 border-t-[#3B82F6] animate-spin" />
            ) : null}
          </div>
        )}
      </div>

      {/* Bottom Controls */}
      <div className="relative z-10 flex flex-col items-center gap-2.5 px-6 pb-7 pt-3">
        {running ? (
          <div className="w-full max-w-md h-1.5 rounded-full bg-white/15 overflow-hidden">
            <div
              className="h-full bg-[#3B82F6] transition-all duration-150"
              style={{ width: `${Math.round((progress ?? 0) * 100)}%` }}
            />
          </div>
        ) : null}

        <button
          type="button"
          disabled={running || finished}
          onClick={() => onDownload(item)}
          className="rounded-full px-7 py-3.5 flex items-center gap-2.5 text-white font-bold text-sm cursor-pointer disabled:cursor-default transition-opacity"
          style={{
            background: finished
              ? "linear-gradient(90deg, #34D399, #10B981)"
              : "linear-gradient(90deg, #3B82F6, #8B5CF6)",
          }}
        >
          {finished ? (
            <Check className="w-5 h-5" />
          ) : (
            <Download className="w-5 h-5" />
          )}
          <span>{label}</span>
        </button>

        {canPlay ? (
          <button
            type="button"
            onClick={() => {
              if (item.isVideo) {
                setPlayingInline(true);
              } else if (savedObjectUrl) {
                const a = document.createElement("a");
                a.href = savedObjectUrl;
                a.download = item.name;
                a.click();
              }
            }}
            className="rounded-full border border-white/35 px-7 py-3 text-white font-semibold text-sm hover:bg-white/10 cursor-pointer"
          >
            {item.isVideo ? "تشغيل" : "فتح"}
          </button>
        ) : null}
      </div>
    </div>
  );
}
