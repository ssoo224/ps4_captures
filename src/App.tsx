import React, { useCallback, useEffect, useRef, useState } from "react";
import { ConnectScreen } from "./components/ConnectScreen";
import { GalleryScreen } from "./components/GalleryScreen";
import { InstallScreen } from "./components/InstallScreen";
import { ViewerModal } from "./components/ViewerModal";
import {
  CONN_OPTIONS,
  type Capture,
  type ProxyView,
  type Ps4SessionInfo,
} from "./types";
import { friendlyError } from "./utils";

const EMPTY_PROXY: ProxyView = {
  name: "",
  served: 0,
  total: 0,
  speed: 0,
  nets: "",
  requests: 0,
  activeConns: 0,
  effectiveMax: 0,
  error: "",
};

function readNum(key: string, fallback: number): number {
  const raw = localStorage.getItem(key);
  if (!raw) return fallback;
  const n = Number(raw);
  return Number.isFinite(n) ? n : fallback;
}

function readDoneSet(): Set<string> {
  try {
    const raw = localStorage.getItem("ps4_done");
    if (!raw) return new Set();
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? new Set(arr.map(String)) : new Set();
  } catch {
    return new Set();
  }
}

export function App() {
  const [savedIp] = useState(
    () => localStorage.getItem("ps4_ip") ?? "192.168."
  );
  const [session, setSession] = useState<Ps4SessionInfo | null>(null);
  const [items, setItems] = useState<Capture[]>([]);
  const [connecting, setConnecting] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [status, setStatus] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [scanError, setScanError] = useState<string | null>(null);
  const [viewer, setViewer] = useState<Capture | null>(null);

  const [downloads, setDownloads] = useState<Record<string, number>>({});
  const [savedUris, setSavedUris] = useState<Record<string, string>>({});

  // Direct install state
  const [page, setPage] = useState(0);
  const [link, setLink] = useState("");
  const [mode, setMode] = useState(() => readNum("ps4_mode", 1));
  const [connIdx, setConnIdx] = useState(() => readNum("ps4_connidx", 1));
  const [installPort, setInstallPort] = useState(
    () => localStorage.getItem("ps4_iport") ?? "12800"
  );
  const [installBusy, setInstallBusy] = useState(false);
  const [installOk, setInstallOk] = useState<boolean | null>(null);
  const [installMsg, setInstallMsg] = useState<string | null>(null);
  const [installWarn, setInstallWarn] = useState(false);
  const [proxyRunning, setProxyRunning] = useState(false);
  const [proxyView, setProxyView] = useState<ProxyView>(EMPTY_PROXY);

  const esRef = useRef<EventSource | null>(null);

  // Check shared URL parameter
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const shared = params.get("url") ?? params.get("shared") ?? "";
    const match = /https?:\/\/\S+/.exec(shared);
    if (match) {
      setLink(match[0]);
      setPage(1);
      setInstallOk(null);
      setInstallMsg(null);
    }
  }, []);

  // Poll proxy status while running
  useEffect(() => {
    if (!proxyRunning) return;
    const id = window.setInterval(async () => {
      try {
        const res = await fetch("/api/proxy/status");
        if (!res.ok) return;
        const data = await res.json();
        if (!data.running) {
          setProxyRunning(false);
        } else if (data.view) {
          setProxyView(data.view);
        }
      } catch {
        // ignore transient poll errors
      }
    }, 500);
    return () => window.clearInterval(id);
  }, [proxyRunning]);

  const scan = useCallback(async (s: Ps4SessionInfo) => {
    setScanning(true);
    setScanError(null);
    try {
      const res = await fetch(
        `/api/scan?sessionId=${encodeURIComponent(s.sessionId)}`
      );
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? "فشل الفحص");
      setItems(Array.isArray(data.items) ? data.items : []);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      setScanError(friendlyError(msg));
    } finally {
      setScanning(false);
    }
  }, []);

  const connect = useCallback(
    (rawIp: string) => {
      const ip = rawIp.trim();
      if (connecting) return;
      if (!/^\d{1,3}(\.\d{1,3}){3}$/.test(ip)) {
        setError("اكتب عنوان IP صحيح مثل 192.168.1.20");
        return;
      }
      localStorage.setItem("ps4_ip", ip);
      setError(null);
      setConnecting(true);
      setStatus("جاري البحث عن الجهاز…");

      esRef.current?.close();
      const es = new EventSource(
        `/api/connect-stream?ip=${encodeURIComponent(ip)}`
      );
      esRef.current = es;

      es.onmessage = (ev) => {
        try {
          const msg = JSON.parse(ev.data);
          if (msg.type === "trying") {
            setStatus(`جاري تجربة المنفذ ${msg.port} …`);
          } else if (msg.type === "connected") {
            es.close();
            esRef.current = null;
            const newSession: Ps4SessionInfo = {
              sessionId: msg.sessionId,
              ip: msg.ip,
              port: msg.port,
            };
            const doneSet = readDoneSet();
            const initialDownloads: Record<string, number> = {};
            doneSet.forEach((p) => {
              initialDownloads[p] = 1;
            });
            setDownloads(initialDownloads);
            setSession(newSession);
            setConnecting(false);
            void scan(newSession);
          } else if (msg.type === "error") {
            es.close();
            esRef.current = null;
            setError(friendlyError(msg.message ?? ""));
            setConnecting(false);
          }
        } catch {
          // ignore malformed event
        }
      };

      es.onerror = () => {
        es.close();
        esRef.current = null;
        setError(friendlyError("failed to connect"));
        setConnecting(false);
      };
    },
    [connecting, scan]
  );

  const disconnect = useCallback(async () => {
    const s = session;
    setSession(null);
    setItems([]);
    setDownloads({});
    setSavedUris((prev) => {
      Object.values(prev).forEach((u) => URL.revokeObjectURL(u));
      return {};
    });
    setViewer(null);
    setProxyRunning(false);
    setPage(0);
    if (s) {
      try {
        await fetch("/api/disconnect", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ sessionId: s.sessionId }),
        });
      } catch {
        // ignore
      }
    }
  }, [session]);

  const downloadItem = useCallback(
    async (item: Capture) => {
      const s = session;
      if (!s) return;
      const cur = downloads[item.path];
      if (cur !== undefined && cur >= 0) return;

      setDownloads((prev) => ({ ...prev, [item.path]: 0 }));
      try {
        const url = `/api/file?sessionId=${encodeURIComponent(
          s.sessionId
        )}&path=${encodeURIComponent(item.path)}&name=${encodeURIComponent(
          item.name
        )}&size=${item.size}`;
        const res = await fetch(url);
        if (!res.ok || !res.body) {
          throw new Error(`HTTP ${res.status}`);
        }

        const total = Number(res.headers.get("Content-Length")) || item.size;
        const reader = res.body.getReader();
        const chunks: Uint8Array[] = [];
        let received = 0;

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          if (value) {
            chunks.push(value);
            received += value.length;
            if (total > 0) {
              const frac = Math.min(0.99, received / total);
              setDownloads((prev) => ({ ...prev, [item.path]: frac }));
            }
          }
        }

        const blob = new Blob(chunks as BlobPart[], {
          type: res.headers.get("Content-Type") ?? "application/octet-stream",
        });
        const objectUrl = URL.createObjectURL(blob);

        const a = document.createElement("a");
        a.href = objectUrl;
        a.download = item.name;
        document.body.appendChild(a);
        a.click();
        a.remove();

        const doneSet = readDoneSet();
        doneSet.add(item.path);
        localStorage.setItem("ps4_done", JSON.stringify(Array.from(doneSet)));

        setSavedUris((prev) => ({ ...prev, [item.path]: objectUrl }));
        setDownloads((prev) => ({ ...prev, [item.path]: 1 }));
      } catch {
        setDownloads((prev) => ({ ...prev, [item.path]: -1 }));
      }
    },
    [downloads, session]
  );

  const startInstall = useCallback(async () => {
    const s = session;
    if (!s || installBusy) return;
    const url = link.trim();
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      setInstallOk(false);
      setInstallMsg("الصق رابطًا يبدأ بـ http أو https");
      return;
    }

    const port = Number(installPort) || 12800;
    const connCount =
      CONN_OPTIONS[Math.min(Math.max(connIdx, 0), CONN_OPTIONS.length - 1)];

    setInstallBusy(true);
    setInstallOk(null);
    setInstallWarn(false);
    setInstallMsg(null);

    try {
      const res = await fetch("/api/install", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          sessionId: s.sessionId,
          url,
          mode,
          connCount,
          installPort: port,
        }),
      });
      const data = await res.json();
      setInstallOk(Boolean(data.ok));
      setInstallWarn(Boolean(data.warn));
      setInstallMsg(data.message ?? null);
      setProxyRunning(Boolean(data.proxyRunning));
      if (data.proxyView) setProxyView(data.proxyView);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "خطأ غير معروف";
      setProxyRunning(false);
      setInstallOk(false);
      setInstallMsg(`تعذّر الإرسال: ${msg}`);
    } finally {
      setInstallBusy(false);
    }
  }, [connIdx, installBusy, installPort, link, mode, session]);

  const stopProxy = useCallback(async () => {
    setProxyRunning(false);
    try {
      await fetch("/api/proxy/stop", { method: "POST" });
    } catch {
      // ignore
    }
  }, []);

  return (
    <div
      dir="rtl"
      className="min-h-screen w-full text-[#F1F5FF]"
      style={{
        background:
          "linear-gradient(to bottom, #0B1235 0%, #070B18 50%, #04060E 100%)",
      }}
    >
      {!session ? (
        <ConnectScreen
          savedIp={savedIp}
          connecting={connecting}
          status={status}
          error={error}
          onConnect={connect}
        />
      ) : page === 1 ? (
        <InstallScreen
          session={session}
          page={page}
          link={link}
          mode={mode}
          connIdx={connIdx}
          installPort={installPort}
          installBusy={installBusy}
          installOk={installOk}
          installMsg={installMsg}
          installWarn={installWarn}
          proxyRunning={proxyRunning}
          proxyView={proxyView}
          onPageChange={setPage}
          onLinkChange={setLink}
          onModeChange={(v) => {
            setMode(v);
            localStorage.setItem("ps4_mode", String(v));
          }}
          onConnIdxChange={(v) => {
            setConnIdx(v);
            localStorage.setItem("ps4_connidx", String(v));
          }}
          onPortChange={(p) => {
            const clean = p.replace(/\D/g, "").slice(0, 5);
            setInstallPort(clean);
            localStorage.setItem("ps4_iport", clean);
          }}
          onStartInstall={startInstall}
          onStopProxy={stopProxy}
        />
      ) : (
        <GalleryScreen
          session={session}
          items={items}
          scanning={scanning}
          scanError={scanError}
          downloads={downloads}
          page={page}
          onPageChange={setPage}
          onDisconnect={disconnect}
          onRefresh={() => scan(session)}
          onOpenViewer={setViewer}
        />
      )}

      {viewer && session ? (
        <ViewerModal
          item={viewer}
          sessionId={session.sessionId}
          progress={downloads[viewer.path]}
          savedObjectUrl={savedUris[viewer.path]}
          onClose={() => setViewer(null)}
          onDownload={downloadItem}
        />
      ) : null}
    </div>
  );
}

export default App;
