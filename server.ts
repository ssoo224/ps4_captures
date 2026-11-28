import express, { type Request, type Response } from "express";
import http from "node:http";
import https from "node:https";
import os from "node:os";
import path from "node:path";
import { PassThrough, Writable } from "node:stream";
import { fileURLToPath } from "node:url";
import * as ftp from "basic-ftp";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = 3000;

const KNOWN_PORTS = [2121, 1337, 21, 2122, 1338, 2020, 9090];
const ROOTS = ["/user/av_contents", "/user/home"];
const PHOTO_EXT = [".jpg", ".jpeg", ".png", ".webp"];
const VIDEO_EXT = [".mp4", ".webm"];
const NAME_DATE = /^(\d{8})_(\d{6})/;

interface Capture {
  path: string;
  name: string;
  size: number;
  time: number;
  isVideo: boolean;
  root: string;
  thumbPath: string;
}

interface OriginInfo {
  originalUrl: string;
  url: string;
  size: number;
  name: string;
  ranges: boolean;
}

interface ProxyView {
  name: string;
  served: number;
  total: number;
  speed: number;
  nets: string;
  requests: number;
  activeConns: number;
  effectiveMax: number;
  error: string;
}

function parseNameTime(name: string): number | null {
  const m = NAME_DATE.exec(name);
  if (!m) return null;
  const d = m[1];
  const t = m[2];
  const year = Number(d.slice(0, 4));
  const month = Number(d.slice(4, 6)) - 1;
  const day = Number(d.slice(6, 8));
  const hour = Number(t.slice(0, 2));
  const min = Number(t.slice(2, 4));
  const sec = Number(t.slice(4, 6));
  const dt = new Date(year, month, day, hour, min, sec);
  return Number.isNaN(dt.getTime()) ? null : dt.getTime();
}

function mimeOf(name: string): string {
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

function isAllowedCapturePath(p: string): boolean {
  return ROOTS.some((r) => p === r || p.startsWith(`${r}/`));
}

async function openFtpClient(ip: string, port: number, timeoutMs = 4000): Promise<ftp.Client> {
  const client = new ftp.Client(timeoutMs);
  client.ftp.verbose = false;
  try {
    await client.access({
      host: ip,
      port,
      user: "anonymous",
      password: "anonymous",
      secure: false,
    });
    return client;
  } catch (err) {
    try {
      client.close();
    } catch {
      // ignore
    }
    throw err;
  }
}

class Ps4FtpSession {
  id: string;
  ip: string;
  port: number;
  private shared: ftp.Client | null;
  private queue: Promise<unknown> = Promise.resolve();

  constructor(id: string, ip: string, port: number, initial: ftp.Client) {
    this.id = id;
    this.ip = ip;
    this.port = port;
    this.shared = initial;
  }

  private async ensure(): Promise<ftp.Client> {
    if (this.shared && !this.shared.closed) {
      try {
        await this.shared.send("NOOP");
        return this.shared;
      } catch {
        try {
          this.shared.close();
        } catch {
          // ignore
        }
      }
    }
    const next = await openFtpClient(this.ip, this.port, 5000);
    this.shared = next;
    return next;
  }

  private useShared<T>(fn: (client: ftp.Client) => Promise<T>): Promise<T> {
    const run = this.queue.then(async () => {
      try {
        const client = await this.ensure();
        return await fn(client);
      } catch (err) {
        try {
          this.shared?.close();
        } catch {
          // ignore
        }
        this.shared = null;
        throw err;
      }
    });
    this.queue = run.catch(() => undefined);
    return run;
  }

  async scan(): Promise<Capture[]> {
    return this.useShared(async (client) => {
      let result: Capture[] = [];
      for (const root of ROOTS) {
        const out: Capture[] = [];
        await this.walk(client, root, root, 0, out);
        if (out.length > 0) {
          result = out.sort((a, b) => {
            if (b.time !== a.time) return b.time - a.time;
            return b.name.localeCompare(a.name);
          });
          break;
        }
      }
      return result;
    });
  }

  private async walk(
    client: ftp.Client,
    root: string,
    dir: string,
    depth: number,
    out: Capture[]
  ): Promise<void> {
    if (depth > 8) return;
    let entries: ftp.FileInfo[];
    try {
      entries = await client.list(dir);
    } catch {
      return;
    }
    for (const f of entries) {
      const name = (f.name ?? "").split("/").pop() ?? "";
      if (!name || name === "." || name === "..") continue;
      const full = `${dir.replace(/\/+$/, "")}/${name}`;
      if (full === dir) continue;
      if (f.isDirectory) {
        if (name.toLowerCase() === "thumbnails") continue;
        await this.walk(client, root, full, depth + 1, out);
      } else {
        const lower = name.toLowerCase();
        const isVideo = VIDEO_EXT.some((ext) => lower.endsWith(ext));
        const isPhoto = PHOTO_EXT.some((ext) => lower.endsWith(ext));
        if (isVideo || isPhoto) {
          const parsedTime = parseNameTime(name);
          const fallbackTime = f.modifiedAt ? f.modifiedAt.getTime() : 0;
          const t = parsedTime ?? fallbackTime;
          const thumbPath = `${root}/thumbnails${full.startsWith(root) ? full.slice(root.length) : full}.jpg`;
          out.push({
            path: full,
            name,
            size: f.size ?? 0,
            time: t,
            isVideo,
            root,
            thumbPath,
          });
        }
      }
    }
  }

  async readBytes(remotePath: string): Promise<Buffer | null> {
    return this.useShared(async (client) => {
      const chunks: Buffer[] = [];
      const sink = new Writable({
        write(chunk, _encoding, cb) {
          chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
          cb();
        },
      });
      try {
        await client.downloadTo(sink, remotePath);
        return Buffer.concat(chunks);
      } catch {
        return null;
      }
    });
  }

  async streamToResponse(
    remotePath: string,
    res: Response,
    filename: string,
    size: number,
    asAttachment: boolean
  ): Promise<void> {
    const client = await openFtpClient(this.ip, this.port, 6000);
    const pass = new PassThrough();
    res.setHeader("Content-Type", mimeOf(filename));
    if (size > 0) {
      res.setHeader("Content-Length", String(size));
    }
    if (asAttachment) {
      res.setHeader(
        "Content-Disposition",
        `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`
      );
    }
    pass.pipe(res);
    try {
      await client.downloadTo(pass, remotePath);
    } finally {
      try {
        client.close();
      } catch {
        // ignore
      }
    }
  }

  close(): void {
    try {
      this.shared?.close();
    } catch {
      // ignore
    }
    this.shared = null;
  }
}

const sessions = new Map<string, Ps4FtpSession>();

// ───────────── Keep-Alive Pooled HTTP/HTTPS Client for Proxy ─────────────
const httpAgent = new http.Agent({
  keepAlive: true,
  keepAliveMsecs: 15000,
  maxSockets: 64,
  maxFreeSockets: 32,
});

const httpsAgent = new https.Agent({
  keepAlive: true,
  keepAliveMsecs: 15000,
  maxSockets: 64,
  maxFreeSockets: 32,
});

interface UpstreamResponse {
  statusCode: number;
  headers: http.IncomingHttpHeaders;
  stream: http.IncomingMessage;
  finalUrl: string;
  abort: () => void;
}

class HttpStatusError extends Error {
  statusCode: number;
  retryAfterMs: number;
  constructor(statusCode: number, message: string, retryAfterMs = 0) {
    super(message);
    this.statusCode = statusCode;
    this.retryAfterMs = retryAfterMs;
  }
}

function openUpstreamOnce(
  targetUrl: string,
  method: "GET" | "HEAD",
  extraHeaders: Record<string, string>,
  connectTimeoutMs: number,
  idleTimeoutMs: number
): Promise<UpstreamResponse> {
  return new Promise((resolve, reject) => {
    const parsed = new URL(targetUrl);
    const isHttps = parsed.protocol === "https:";
    const mod = isHttps ? https : http;
    let settled = false;
    let connected = false;

    const req = mod.request(
      parsed,
      {
        method,
        agent: isHttps ? httpsAgent : httpAgent,
        headers: {
          "User-Agent":
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
          "Accept-Encoding": "identity",
          Connection: "keep-alive",
          ...extraHeaders,
        },
      },
      (res) => {
        connected = true;
        clearTimeout(connectTimer);
        res.setTimeout(idleTimeoutMs, () => {
          res.destroy(new Error("انقطاع استجابة السيرفر (مهلة القراءة)"));
        });
        if (!settled) {
          settled = true;
          resolve({
            statusCode: res.statusCode ?? 0,
            headers: res.headers,
            stream: res,
            finalUrl: targetUrl,
            abort: () => {
              req.destroy();
              res.destroy();
            },
          });
        }
      }
    );

    const connectTimer = setTimeout(() => {
      if (!connected) {
        req.destroy(new Error("انتهت مهلة الاتصال بالسيرفر"));
      }
    }, connectTimeoutMs);

    req.on("error", (err) => {
      clearTimeout(connectTimer);
      if (!settled) {
        settled = true;
        reject(err);
      }
    });

    req.end();
  });
}

async function openUpstreamFollowing(
  targetUrl: string,
  method: "GET" | "HEAD",
  rangeHeader?: string,
  connectTimeoutMs = 10000,
  idleTimeoutMs = 15000
): Promise<UpstreamResponse> {
  let cur = targetUrl;
  const headers: Record<string, string> = {};
  if (rangeHeader) headers["Range"] = rangeHeader;

  for (let hop = 0; hop < 8; hop++) {
    const resp = await openUpstreamOnce(cur, method, headers, connectTimeoutMs, idleTimeoutMs);
    const code = resp.statusCode;
    if (code === 301 || code === 302 || code === 303 || code === 307 || code === 308) {
      const loc = resp.headers["location"];
      resp.abort();
      if (!loc) throw new Error("تحويل بدون عنوان");
      cur = new URL(Array.isArray(loc) ? loc[0] : loc, cur).toString();
      continue;
    }
    return resp;
  }
  throw new Error("تحويلات كثيرة");
}

function localIpFor(ps4Ip: string): string | null {
  const prefix = ps4Ip.slice(0, ps4Ip.lastIndexOf(".") + 1);
  let fallback: string | null = null;
  const ifs = os.networkInterfaces();
  for (const list of Object.values(ifs)) {
    if (!list) continue;
    for (const addr of list) {
      if (addr.family === "IPv4" && !addr.internal) {
        if (addr.address.startsWith(prefix)) return addr.address;
        if (!fallback) fallback = addr.address;
      }
    }
  }
  return fallback;
}

function safePkgName(raw: string): string {
  let dec = raw;
  try {
    dec = decodeURIComponent(raw);
  } catch {
    // ignore
  }
  let n = dec.replace(/[^A-Za-z0-9._-]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 80);
  if (!n) n = "game";
  if (!n.toLowerCase().endsWith(".pkg")) n += ".pkg";
  return n;
}

function headerStr(headers: http.IncomingHttpHeaders, key: string): string {
  const v = headers[key.toLowerCase()];
  if (!v) return "";
  return Array.isArray(v) ? v[0] ?? "" : v;
}

class PkgProxyServer {
  private maxConns: number;
  private effectiveMax: number;
  private allowedIp: string;
  private subnetPrefix: string;
  private server: http.Server | null = null;
  private stopped = false;

  origin: OriginInfo | null = null;
  served = 0;
  requests = 0;
  lastError = "";

  private lastServed = 0;
  private lastTick = Date.now();
  private emaSpeed = 0;

  private readonly blockSize = 2 * 1024 * 1024; // 2MB chunks for low latency + high throughput
  private readonly maxBlocks = 48; // up to 96MB sliding window cache
  private readonly ahead: number;
  private blocks = new Map<number, { promise: Promise<Buffer>; touched: number; done: boolean }>();

  private inflight = 0;
  private waitQueue: Array<() => void> = [];
  private consecutiveOk = 0;
  private refreshPromise: Promise<string> | null = null;

  constructor(conns: number, allowedIp: string) {
    this.maxConns = conns;
    this.effectiveMax = conns;
    this.allowedIp = allowedIp;
    this.subnetPrefix = allowedIp.slice(0, allowedIp.lastIndexOf(".") + 1);
    this.ahead = Math.min(conns, 16);
  }

  async prepare(url: string): Promise<OriginInfo> {
    let resp: UpstreamResponse | null = null;
    try {
      resp = await openUpstreamFollowing(url, "GET", "bytes=0-0", 10000, 15000);
      const ctype = headerStr(resp.headers, "content-type").toLowerCase();
      if (ctype.includes("text/html")) {
        throw new Error("الرابط يرجع صفحة ويب (HTML) وليس ملف .pkg مباشر");
      }

      let size = -1;
      let ranges = false;

      if (resp.statusCode === 206) {
        const cr = headerStr(resp.headers, "content-range");
        const parsed = Number(cr.split("/").pop()?.trim());
        if (Number.isFinite(parsed) && parsed > 0) {
          size = parsed;
          ranges = true;
        }
      } else if (resp.statusCode === 200) {
        const cl = Number(headerStr(resp.headers, "content-length"));
        if (Number.isFinite(cl) && cl > 0) size = cl;
        const ar = headerStr(resp.headers, "accept-ranges").toLowerCase();
        ranges = ar.includes("bytes");
      } else if (resp.statusCode === 416 || resp.statusCode === 405 || resp.statusCode === 400) {
        resp.abort();
        resp = await openUpstreamFollowing(url, "HEAD", undefined, 10000, 15000);
        if (resp.statusCode >= 200 && resp.statusCode < 300) {
          const cl = Number(headerStr(resp.headers, "content-length"));
          if (Number.isFinite(cl) && cl > 0) size = cl;
          ranges = headerStr(resp.headers, "accept-ranges").toLowerCase().includes("bytes");
        } else {
          throw new Error(`رد السيرفر: HTTP ${resp.statusCode}`);
        }
      } else {
        throw new Error(`رد السيرفر: HTTP ${resp.statusCode}`);
      }

      if (size <= 0) throw new Error("حجم الملف غير معروف أو السيرفر لا يرسل Content-Length");

      const disp = headerStr(resp.headers, "content-disposition");
      const fnMatch = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disp);
      const raw = fnMatch?.[1] ?? new URL(resp.finalUrl).pathname.split("/").pop() ?? "game.pkg";
      const info: OriginInfo = {
        originalUrl: url,
        url: resp.finalUrl,
        size,
        name: safePkgName(raw),
        ranges,
      };
      this.origin = info;
      return info;
    } finally {
      resp?.abort();
    }
  }

  private async refreshFinalUrl(): Promise<string> {
    if (!this.origin) throw new Error("لا يوجد ملف");
    if (this.refreshPromise) return this.refreshPromise;

    this.refreshPromise = (async () => {
      const origin = this.origin!;
      const resp = await openUpstreamFollowing(origin.originalUrl, "GET", "bytes=0-0", 10000, 15000);
      try {
        if (resp.statusCode === 206 || resp.statusCode === 200) {
          origin.url = resp.finalUrl;
          return resp.finalUrl;
        }
        throw new Error(`فشل تجديد الرابط: HTTP ${resp.statusCode}`);
      } finally {
        resp.abort();
        this.refreshPromise = null;
      }
    })();

    return this.refreshPromise;
  }

  async start(preferredPort = 8765): Promise<number> {
    const srv = http.createServer((req, res) => {
      const remoteAddr = req.socket.remoteAddress?.replace(/^::ffff:/, "") ?? "";
      const allowed =
        !remoteAddr ||
        remoteAddr === this.allowedIp ||
        remoteAddr === "127.0.0.1" ||
        (this.subnetPrefix && remoteAddr.startsWith(this.subnetPrefix));
      if (!allowed) {
        res.writeHead(403);
        res.end();
        return;
      }
      req.socket.setNoDelay(true);
      this.requests += 1;
      void this.handleRequest(req, res);
    });

    // Critical for multi-hour 50GB+ PKG transfers over LAN
    srv.requestTimeout = 0;
    srv.headersTimeout = 30000;
    srv.keepAliveTimeout = 120000;

    const listenOn = (port: number) =>
      new Promise<number>((resolve, reject) => {
        srv.once("error", reject);
        srv.listen(port, "0.0.0.0", () => {
          srv.removeListener("error", reject);
          const addr = srv.address();
          resolve(typeof addr === "object" && addr ? addr.port : port);
        });
      });

    this.server = srv;
    try {
      return await listenOn(preferredPort);
    } catch {
      return await listenOn(0);
    }
  }

  stop(): void {
    this.stopped = true;
    try {
      this.server?.close();
    } catch {
      // ignore
    }
    this.server = null;
    this.blocks.clear();
    while (this.waitQueue.length > 0) {
      const fn = this.waitQueue.shift();
      fn?.();
    }
  }

  getView(): ProxyView {
    const now = Date.now();
    const dt = Math.max(now - this.lastTick, 1);
    if (dt >= 400) {
      const inst = ((this.served - this.lastServed) * 1000) / dt;
      this.emaSpeed = this.emaSpeed === 0 ? inst : this.emaSpeed * 0.7 + inst * 0.3;
      this.lastServed = this.served;
      this.lastTick = now;
    }
    return {
      name: this.origin?.name ?? "",
      served: this.served,
      total: this.origin?.size ?? 0,
      speed: Math.round(this.emaSpeed),
      nets: `${this.effectiveMax} اتصال متوازي`,
      requests: this.requests,
      activeConns: this.inflight,
      effectiveMax: this.effectiveMax,
      error: this.lastError,
    };
  }

  private acquireSlot(): Promise<void> {
    if (this.inflight < this.effectiveMax) {
      this.inflight++;
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      this.waitQueue.push(() => {
        this.inflight++;
        resolve();
      });
    });
  }

  private releaseSlot(): void {
    this.inflight = Math.max(0, this.inflight - 1);
    while (this.inflight < this.effectiveMax && this.waitQueue.length > 0) {
      const next = this.waitQueue.shift();
      if (next) next();
    }
  }

  private onChunkSuccess(): void {
    this.consecutiveOk++;
    if (this.effectiveMax < this.maxConns && this.consecutiveOk >= 12) {
      this.effectiveMax = Math.min(this.maxConns, this.effectiveMax + 2);
      this.consecutiveOk = 0;
    }
  }

  private onRateLimitOrDrop(): void {
    this.consecutiveOk = 0;
    if (this.effectiveMax > 4) {
      this.effectiveMax = Math.max(4, Math.floor(this.effectiveMax * 0.7));
    }
  }

  private readExactRange(resp: UpstreamResponse, want: number): Promise<Buffer> {
    return new Promise((resolve, reject) => {
      const buf = Buffer.allocUnsafe(want);
      let off = 0;
      let finished = false;

      const cleanup = () => {
        resp.stream.removeListener("data", onData);
        resp.stream.removeListener("end", onEnd);
        resp.stream.removeListener("error", onError);
      };

      const onData = (chunk: Buffer) => {
        if (this.stopped) {
          finished = true;
          cleanup();
          resp.abort();
          reject(new Error("تم إيقاف السيرفر"));
          return;
        }
        const copyLen = Math.min(chunk.length, want - off);
        chunk.copy(buf, off, 0, copyLen);
        off += copyLen;
        if (off >= want) {
          finished = true;
          cleanup();
          resolve(buf);
        }
      };

      const onEnd = () => {
        if (finished) return;
        finished = true;
        cleanup();
        if (off >= want) {
          resolve(buf);
        } else {
          reject(new Error("انقطاع أثناء السحب"));
        }
      };

      const onError = (err: Error) => {
        if (finished) return;
        finished = true;
        cleanup();
        reject(err);
      };

      resp.stream.on("data", onData);
      resp.stream.on("end", onEnd);
      resp.stream.on("error", onError);
    });
  }

  private async fetchChunk(s: number, e: number): Promise<Buffer> {
    const origin = this.origin;
    if (!origin) throw new Error("لا يوجد ملف");
    const want = e - s + 1;
    let attempt = 0;
    let refreshedOnce = false;

    while (true) {
      if (this.stopped) throw new Error("تم إيقاف السيرفر");
      await this.acquireSlot();
      let resp: UpstreamResponse | null = null;
      try {
        resp = await openUpstreamFollowing(origin.url, "GET", `bytes=${s}-${e}`, 10000, 15000);
        if (resp.statusCode === 429 || resp.statusCode === 503) {
          const ra = Number(headerStr(resp.headers, "retry-after"));
          const waitMs = Number.isFinite(ra) && ra > 0 ? Math.min(ra * 1000, 8000) : 1200;
          throw new HttpStatusError(resp.statusCode, `HTTP ${resp.statusCode}`, waitMs);
        }
        if ((resp.statusCode === 401 || resp.statusCode === 403 || resp.statusCode === 410) && !refreshedOnce) {
          refreshedOnce = true;
          resp.abort();
          resp = null;
          await this.refreshFinalUrl();
          continue;
        }
        if (resp.statusCode !== 206) {
          throw new HttpStatusError(resp.statusCode, `HTTP ${resp.statusCode}`);
        }
        const buf = await this.readExactRange(resp, want);
        this.onChunkSuccess();
        this.lastError = "";
        return buf;
      } catch (err) {
        attempt++;
        const msg = err instanceof Error ? err.message : String(err);
        this.lastError = msg;
        if (err instanceof HttpStatusError && (err.statusCode === 429 || err.statusCode === 503)) {
          this.onRateLimitOrDrop();
        } else if (attempt >= 2) {
          this.onRateLimitOrDrop();
        }
        if (attempt >= 8 || this.stopped) throw err;
        const backoff =
          err instanceof HttpStatusError && err.retryAfterMs > 0
            ? err.retryAfterMs
            : 250 * Math.min(attempt, 6);
        await new Promise((r) => setTimeout(r, backoff));
      } finally {
        resp?.abort();
        this.releaseSlot();
      }
    }
  }

  private getBlock(idx: number) {
    let b = this.blocks.get(idx);
    if (!b) {
      const origin = this.origin!;
      const s = idx * this.blockSize;
      const e = Math.min(s + this.blockSize - 1, origin.size - 1);
      const entry = {
        touched: Date.now(),
        done: false,
        promise: this.fetchChunk(s, e)
          .then((buf) => {
            entry.done = true;
            return buf;
          })
          .catch((err) => {
            this.blocks.delete(idx);
            throw err;
          }),
      };
      this.blocks.set(idx, entry);
      b = entry;
    }
    b.touched = Date.now();
    return b;
  }

  private trimBlocks(): void {
    if (this.blocks.size <= this.maxBlocks) return;
    let excess = this.blocks.size - this.maxBlocks;
    const doneEntries = Array.from(this.blocks.entries())
      .filter(([, v]) => v.done)
      .sort((a, b) => a[1].touched - b[1].touched);
    for (const [idx] of doneEntries) {
      if (excess <= 0) break;
      this.blocks.delete(idx);
      excess--;
    }
  }

  private async handleRequest(req: http.IncomingMessage, res: http.ServerResponse): Promise<void> {
    const o = this.origin;
    if (!o) {
      res.writeHead(503, { "Content-Length": "0", Connection: "close" });
      res.end();
      return;
    }

    let clientClosed = false;
    const markClosed = () => {
      clientClosed = true;
    };
    req.on("aborted", markClosed);
    res.on("close", markClosed);

    const size = o.size;
    let start = 0;
    let end = size - 1;
    let partial = false;
    const range = req.headers.range;
    if (range && o.ranges) {
      const m = /bytes=(\d*)-(\d*)/.exec(range);
      if (m) {
        const a = m[1];
        const b = m[2];
        if (!a && b) {
          start = Math.max(0, size - Number(b));
        } else if (a) {
          start = Number(a);
          if (b) end = Math.min(Number(b), size - 1);
        }
        partial = true;
      }
    }
    if (start >= size || start > end) {
      res.writeHead(416, {
        "Content-Range": `bytes */${size}`,
        "Content-Length": "0",
        Connection: "close",
      });
      res.end();
      return;
    }

    const len = end - start + 1;
    const headers: Record<string, string> = {
      "Content-Type": "application/octet-stream",
      "Accept-Ranges": o.ranges ? "bytes" : "none",
      "Content-Length": String(len),
      Connection: "keep-alive",
    };
    if (partial) {
      headers["Content-Range"] = `bytes ${start}-${end}/${size}`;
    }
    res.writeHead(partial ? 206 : 200, headers);
    if (req.method === "HEAD") {
      res.end();
      return;
    }

    try {
      if (o.ranges) {
        const lastBlock = Math.floor((size - 1) / this.blockSize);
        let pos = start;
        while (pos <= end && !this.stopped && !clientClosed && !res.destroyed) {
          const idx = Math.floor(pos / this.blockSize);
          const blk = this.getBlock(idx);

          // Only prefetch ahead when serving larger reads (> 128KB remaining)
          const remaining = end - pos + 1;
          if (remaining > 128 * 1024 && !clientClosed) {
            const lookAhead = Math.min(this.ahead, this.effectiveMax);
            for (let k = 1; k <= lookAhead && idx + k <= lastBlock; k++) {
              this.getBlock(idx + k);
            }
          }

          const data = await blk.promise;
          if (clientClosed || res.destroyed || this.stopped) break;

          const blockStart = idx * this.blockSize;
          const from = pos - blockStart;
          const lastByte = Math.min(end, blockStart + data.length - 1);
          const to = lastByte - blockStart;
          const slice = data.subarray(from, to + 1);

          if (!res.write(slice)) {
            await new Promise<void>((resolve) => {
              const done = () => {
                res.removeListener("drain", done);
                res.removeListener("close", done);
                resolve();
              };
              res.once("drain", done);
              res.once("close", done);
            });
          }

          if (clientClosed || res.destroyed) break;
          this.served += slice.length;
          pos = lastByte + 1;
          if (lastByte === blockStart + data.length - 1) {
            this.blocks.delete(idx);
          }
          this.trimBlocks();
        }
        if (!clientClosed && !res.destroyed) res.end();
      } else {
        const upstream = await openUpstreamFollowing(o.url, "GET", undefined, 10000, 20000);
        try {
          for await (const chunk of upstream.stream) {
            if (this.stopped || clientClosed || res.destroyed) break;
            const buf = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
            if (!res.write(buf)) {
              await new Promise<void>((resolve) => {
                const done = () => {
                  res.removeListener("drain", done);
                  res.removeListener("close", done);
                  resolve();
                };
                res.once("drain", done);
                res.once("close", done);
              });
            }
            if (!clientClosed && !res.destroyed) {
              this.served += buf.length;
            }
          }
          if (!clientClosed && !res.destroyed) res.end();
        } finally {
          upstream.abort();
        }
      }
    } catch (err) {
      if (!this.stopped && !clientClosed) {
        this.lastError = err instanceof Error ? err.message : String(err);
      }
      res.destroy();
    }
  }
}

let activeProxy: PkgProxyServer | null = null;

function stopActiveProxy(): void {
  if (activeProxy) {
    activeProxy.stop();
    activeProxy = null;
  }
}

type PostInstallOutcome =
  | { kind: "ok"; code: number; body: string }
  | { kind: "connect_failed"; message: string }
  | { kind: "read_timeout" };

function postInstallToPs4(ip: string, port: number, pkgUrl: string): Promise<PostInstallOutcome> {
  return new Promise((resolve) => {
    const payload = Buffer.from(
      JSON.stringify({
        type: "direct",
        packages: [pkgUrl],
      }),
      "utf8"
    );

    let connected = false;
    let settled = false;
    const finish = (outcome: PostInstallOutcome) => {
      if (settled) return;
      settled = true;
      clearTimeout(connectTimer);
      clearTimeout(readTimer);
      resolve(outcome);
    };

    const req = http.request(
      {
        hostname: ip,
        port,
        path: "/api/install",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Content-Length": String(payload.length),
        },
      },
      (res) => {
        connected = true;
        clearTimeout(connectTimer);
        const chunks: Buffer[] = [];
        res.on("data", (c) => chunks.push(Buffer.isBuffer(c) ? c : Buffer.from(c)));
        res.on("end", () => {
          const text = Buffer.concat(chunks).toString("utf8").slice(0, 400);
          finish({ kind: "ok", code: res.statusCode ?? 0, body: text });
        });
        res.on("error", () => finish({ kind: "read_timeout" }));
      }
    );

    const connectTimer = setTimeout(() => {
      if (!connected) {
        req.destroy();
        finish({ kind: "connect_failed", message: "انتهت مهلة الاتصال بالمنفذ" });
      }
    }, 4000);

    const readTimer = setTimeout(() => {
      req.destroy();
      finish(
        connected
          ? { kind: "read_timeout" }
          : { kind: "connect_failed", message: "انتهت مهلة الاتصال بالمنفذ" }
      );
    }, 120000);

    req.on("socket", (sock) => {
      sock.on("connect", () => {
        connected = true;
        clearTimeout(connectTimer);
      });
    });

    req.on("error", (err) => {
      if (!connected) {
        finish({ kind: "connect_failed", message: err.message });
      } else {
        finish({ kind: "read_timeout" });
      }
    });

    req.write(payload);
    req.end();
  });
}

function isValidIp(ip: string): boolean {
  if (!/^\d{1,3}(\.\d{1,3}){3}$/.test(ip)) return false;
  return ip.split(".").every((part) => {
    const n = Number(part);
    return n >= 0 && n <= 255;
  });
}

async function bootstrap() {
  const app = express();
  app.use(express.json());

  app.get("/api/connect-stream", async (req: Request, res: Response) => {
    const ip = String(req.query.ip ?? "").trim();
    if (!isValidIp(ip)) {
      res.status(400).json({ error: "اكتب عنوان IP صحيح مثل 192.168.1.20" });
      return;
    }
    res.setHeader("Content-Type", "text/event-stream");
    res.setHeader("Cache-Control", "no-cache");
    res.setHeader("Connection", "keep-alive");
    res.flushHeaders();

    let closed = false;
    req.on("close", () => {
      closed = true;
    });

    const sendEvent = (data: unknown) => {
      if (!closed) res.write(`data: ${JSON.stringify(data)}\n\n`);
    };

    let firstErr: Error | null = null;
    for (const p of KNOWN_PORTS) {
      if (closed) return;
      sendEvent({ type: "trying", port: p });
      try {
        const client = await openFtpClient(ip, p, 3500);
        if (closed) {
          client.close();
          return;
        }
        const sessionId = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
        sessions.set(sessionId, new Ps4FtpSession(sessionId, ip, p, client));
        sendEvent({ type: "connected", sessionId, ip, port: p });
        res.end();
        return;
      } catch (err) {
        if (!firstErr) {
          firstErr = err instanceof Error ? err : new Error(String(err));
        }
      }
    }

    sendEvent({ type: "error", message: firstErr?.message ?? "no port" });
    res.end();
  });

  app.post("/api/disconnect", (req: Request, res: Response) => {
    const sessionId = String(req.body?.sessionId ?? "");
    const session = sessions.get(sessionId);
    if (session) {
      session.close();
      sessions.delete(sessionId);
    }
    stopActiveProxy();
    res.json({ ok: true });
  });

  app.get("/api/scan", async (req: Request, res: Response) => {
    const sessionId = String(req.query.sessionId ?? "");
    const session = sessions.get(sessionId);
    if (!session) {
      res.status(404).json({ error: "الجلسة غير موجودة أو انتهت" });
      return;
    }
    try {
      const items = await session.scan();
      res.json({ items });
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      res.status(502).json({ error: message });
    }
  });

  app.get("/api/thumb", async (req: Request, res: Response) => {
    const sessionId = String(req.query.sessionId ?? "");
    const itemPath = String(req.query.path ?? "");
    const thumbPath = String(req.query.thumbPath ?? "");
    const isVideo = req.query.isVideo === "1";

    const session = sessions.get(sessionId);
    if (!session) {
      res.status(404).end();
      return;
    }
    if (!isAllowedCapturePath(itemPath)) {
      res.status(403).end();
      return;
    }
    try {
      let buf: Buffer | null = null;
      if (isVideo) {
        if (thumbPath && isAllowedCapturePath(thumbPath)) {
          buf = await session.readBytes(thumbPath);
        }
      } else {
        buf = await session.readBytes(itemPath);
        if (!buf && thumbPath && isAllowedCapturePath(thumbPath)) {
          buf = await session.readBytes(thumbPath);
        }
      }
      if (!buf) {
        res.status(404).end();
        return;
      }
      res.setHeader("Content-Type", mimeOf(isVideo ? thumbPath : itemPath));
      res.setHeader("Cache-Control", "private, max-age=3600");
      res.send(buf);
    } catch {
      res.status(502).end();
    }
  });

  app.get("/api/file", async (req: Request, res: Response) => {
    const sessionId = String(req.query.sessionId ?? "");
    const itemPath = String(req.query.path ?? "");
    const name = String(req.query.name ?? itemPath.split("/").pop() ?? "capture");
    const size = Number(req.query.size ?? 0);
    const asAttachment = req.query.download === "1";

    const session = sessions.get(sessionId);
    if (!session) {
      res.status(404).json({ error: "الجلسة غير موجودة" });
      return;
    }
    if (!isAllowedCapturePath(itemPath)) {
      res.status(403).json({ error: "مسار غير مسموح" });
      return;
    }
    try {
      await session.streamToResponse(itemPath, res, name, size, asAttachment);
    } catch (err) {
      if (!res.headersSent) {
        const message = err instanceof Error ? err.message : String(err);
        res.status(502).json({ error: message });
      }
    }
  });

  app.post("/api/install", async (req: Request, res: Response) => {
    const sessionId = String(req.body?.sessionId ?? "");
    const url = String(req.body?.url ?? "").trim();
    const mode = Number(req.body?.mode ?? 1);
    const connCount = Number(req.body?.connCount ?? 16);
    const installPort = Number(req.body?.installPort ?? 12800);

    const session = sessions.get(sessionId);
    if (!session) {
      res.status(404).json({ ok: false, message: "غير متصل بجهاز PS4" });
      return;
    }
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      res.status(400).json({ ok: false, message: "الصق رابطًا يبدأ بـ http أو https" });
      return;
    }
    if (!Number.isInteger(installPort) || installPort < 1 || installPort > 65535) {
      res.status(400).json({ ok: false, message: "رقم المنفذ غير صحيح" });
      return;
    }

    try {
      let targetUrl = url;
      let proxyView: ProxyView | null = null;
      if (mode === 1) {
        stopActiveProxy();
        const server = new PkgProxyServer(
          Math.max(1, Math.min(connCount, 32)),
          session.ip
        );
        const info = await server.prepare(url);
        const hostIp = localIpFor(session.ip);
        if (!hostIp) {
          throw new Error("ما كدرت أعرف IP الخادم على الشبكة");
        }
        const proxyPort = await server.start(8765);
        activeProxy = server;
        proxyView = server.getView();
        targetUrl = `http://${hostIp}:${proxyPort}/${info.name}`;
      }

      const outcome = await postInstallToPs4(session.ip, installPort, targetUrl);

      if (outcome.kind === "read_timeout") {
        res.json({
          ok: true,
          warn: true,
          proxyRunning: mode === 1 && activeProxy !== null,
          proxyView,
          message:
            mode === 1
              ? "الـ PS4 ما رد على الأمر لحد الآن، وقد يكون مشغولًا بتحضير اللعبة. السيرفر باقي شغّال: راقب إشعارات الجهاز وعدّاد الطلبات بالأسفل."
              : "الـ PS4 ما رد على الأمر لحد الآن. راقب إشعارات الجهاز.",
        });
      } else if (outcome.kind === "connect_failed") {
        stopActiveProxy();
        res.json({
          ok: false,
          warn: false,
          proxyRunning: false,
          message: `أداة التثبيت مو شغّالة على المنفذ ${installPort}. افتحها على الـ PS4 وأعد المحاولة.`,
        });
      } else if (outcome.code >= 200 && outcome.code <= 299) {
        res.json({
          ok: true,
          warn: false,
          proxyRunning: mode === 1 && activeProxy !== null,
          proxyView,
          message:
            mode === 0
              ? "تم إرسال الرابط. الـ PS4 ينزّل من الإنترنت مباشرة، تابع شاشته."
              : "تم إرسال الأمر. الـ PS4 هسه يسحب من السيرفر الوسيط، لا تغلق التطبيق.",
        });
      } else {
        stopActiveProxy();
        res.json({
          ok: false,
          warn: false,
          proxyRunning: false,
          message: `رد الجهاز (${outcome.code}): ${outcome.body}`,
        });
      }
    } catch (err) {
      stopActiveProxy();
      const message = err instanceof Error ? err.message : "خطأ غير معروف";
      res.json({
        ok: false,
        warn: false,
        proxyRunning: false,
        message: `تعذّر الإرسال: ${message}`,
      });
    }
  });

  app.get("/api/proxy/status", (_req: Request, res: Response) => {
    if (!activeProxy) {
      res.json({ running: false, view: null });
      return;
    }
    res.json({ running: true, view: activeProxy.getView() });
  });

  app.post("/api/proxy/stop", (_req: Request, res: Response) => {
    stopActiveProxy();
    res.json({ ok: true });
  });

  if (process.env.NODE_ENV === "production") {
    const distDir = path.join(__dirname, "dist");
    app.use(express.static(distDir));
    app.get("*", (_req, res) => {
      res.sendFile(path.join(distDir, "index.html"));
    });
  } else {
    const { createServer: createViteServer } = await import("vite");
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`PS4 Captures running on http://0.0.0.0:${PORT}`);
  });
}

bootstrap().catch((err) => {
  console.error("Failed to start server:", err);
  process.exit(1);
});
