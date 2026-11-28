export const CONN_OPTIONS = [8, 16, 24, 32] as const;

export const KNOWN_PORTS = [2121, 1337, 21, 2122, 1338, 2020, 9090] as const;

export interface Capture {
  path: string;
  name: string;
  size: number;
  time: number;
  isVideo: boolean;
  root: string;
  thumbPath: string;
}

export interface Ps4SessionInfo {
  sessionId: string;
  ip: string;
  port: number;
}

export interface ProxyView {
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
