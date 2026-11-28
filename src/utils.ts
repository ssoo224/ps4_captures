import type { Capture } from "./types";

export function mimeOf(name: string): string {
  const ext = name.split(".").pop()?.toLowerCase() ?? "";
  switch (ext) {
    case "jpg":
    case "jpeg":
      return "image/jpeg";
    case "png":
      return "image/png";
    case "webp":
      return "image/webp";
    case "mp4":
      return "video/mp4";
    case "webm":
      return "video/webm";
    default:
      return "application/octet-stream";
  }
}

export function fmtSize(b: number): string {
  if (!b || b <= 0) return "";
  const units = ["B", "KB", "MB", "GB"];
  let v = b;
  let i = 0;
  while (v >= 1024 && i < 3) {
    v /= 1024;
    i++;
  }
  return i <= 1 ? `${Math.floor(v)} ${units[i]}` : `${v.toFixed(1)} ${units[i]}`;
}

export function captureTitle(c: Capture): string {
  if (c.time > 0) {
    try {
      return new Intl.DateTimeFormat("ar", {
        day: "numeric",
        month: "short",
        hour: "numeric",
        minute: "2-digit",
      }).format(new Date(c.time));
    } catch {
      // fallback below
    }
  }
  const dot = c.name.lastIndexOf(".");
  return dot > 0 ? c.name.slice(0, dot) : c.name;
}

export function fmtEta(sec: number): string {
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const x = Math.floor(sec % 60);
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(x)}` : `${m}:${pad(x)}`;
}

export function friendlyError(msg: string): string {
  const m = msg.toLowerCase();
  if (
    m.includes("no route") ||
    m.includes("unreachable") ||
    m.includes("timed out") ||
    m.includes("timeout") ||
    m.includes("etimedout") ||
    m.includes("ehostunreach") ||
    m.includes("enetunreach") ||
    m.includes("failed to connect")
  ) {
    return "تعذّر الوصول للجهاز. تأكد من صحة الـ IP وأن الجهاز والـ PS4 على نفس شبكة الواي فاي وأن سيرفر FTP شغّال.";
  }
  if (m.includes("refused") || m.includes("econnrefused")) {
    return "الجهاز رفض الاتصال. فعّل سيرفر FTP من إعدادات GoldHEN.";
  }
  return `تعذّر الاتصال: ${msg || "خطأ غير معروف"}`;
}
