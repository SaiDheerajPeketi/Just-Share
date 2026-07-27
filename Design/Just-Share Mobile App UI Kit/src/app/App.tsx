import { useState, useEffect } from "react";
import {
  ArrowUp, ArrowDown, Image, Video, Music, FileText, Bluetooth,
  Wifi, ChevronLeft, ChevronRight, Check, X, Settings, Clock,
  Shield, QrCode, Camera, Star, Zap, Users, RefreshCw, Trash2,
  Send, Download, Phone, Plus, Minus, History, Lock, Unlock,
  AlertCircle, CheckCircle, Circle, MoreVertical, Search,
  MessageCircle, Smartphone, Monitor, Headphones, Volume2,
  Crown, Gift, Repeat, Infinity, Eye, EyeOff, ArrowRight,
  Radio, ScanLine, UserCheck, Ban, Bell, Info, ChevronDown,
  ToggleLeft, ToggleRight, Play
} from "lucide-react";

// ─── Design Tokens ────────────────────────────────────────────────────────────
const RED = "#EC1C22";
const DARK_RED = "#B71C1C";
const LIGHT_RED = "#FFCDD2";
const BLACK = "#0D0D0D";
const WHITE = "#FFFFFF";
const SURFACE = "#F8F8F8";
const CARD_BG = "#FFFFFF";
const MUTED_FG = "#6B6B6B";
const BORDER = "rgba(0,0,0,0.08)";
const GOLD = "#D4A017";
const GREEN = "#4CAF50";

// ─── Types ─────────────────────────────────────────────────────────────────────
type Screen =
  | "splash" | "permissions" | "home" | "select-files"
  | "discover-bt" | "discover-wifi" | "transfer-progress"
  | "history" | "settings" | "secure-send" | "qr-scan"
  | "incoming-confirm" | "trusted-devices" | "pro-upgrade"
  | "interstitial-ad";

// ─── Shared Primitives ────────────────────────────────────────────────────────

function PillButton({
  label, onClick, variant = "primary", icon, size = "lg", disabled = false
}: {
  label: string; onClick?: () => void; variant?: "primary" | "outline" | "ghost" | "danger" | "gold";
  icon?: React.ReactNode; size?: "sm" | "md" | "lg"; disabled?: boolean;
}) {
  const sizeClass = size === "lg" ? "py-4 px-8 text-base" : size === "md" ? "py-3 px-6 text-sm" : "py-2 px-4 text-xs";
  const variants = {
    primary: `bg-[${RED}] text-white shadow-lg shadow-red-500/25 active:scale-95`,
    outline: `border-2 border-[${RED}] text-[${RED}] bg-transparent active:scale-95`,
    ghost: `text-[${MUTED_FG}] bg-transparent active:scale-95`,
    danger: `bg-[${DARK_RED}] text-white active:scale-95`,
    gold: `bg-[${GOLD}] text-white shadow-lg shadow-yellow-500/25 active:scale-95`,
  };
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`flex items-center justify-center gap-2 rounded-full font-semibold transition-all duration-150 ${sizeClass} ${variants[variant]} ${disabled ? "opacity-40 cursor-not-allowed" : "cursor-pointer"}`}
      style={variant === "primary" ? { backgroundColor: RED } : variant === "danger" ? { backgroundColor: DARK_RED } : variant === "gold" ? { backgroundColor: GOLD } : {}}
    >
      {icon && <span className="flex-shrink-0">{icon}</span>}
      {label}
    </button>
  );
}

function ProBadge({ onClick }: { onClick?: () => void }) {
  return (
    <span
      onClick={e => { e.stopPropagation(); onClick?.(); }}
      role="button"
      tabIndex={0}
      onKeyDown={e => e.key === "Enter" && onClick?.()}
      className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-bold tracking-wide cursor-pointer transition-transform active:scale-95"
      style={{ backgroundColor: GOLD, color: WHITE }}
    >
      <Crown size={10} />
      PRO
    </span>
  );
}

function AdBanner() {
  return (
    <div className="mx-4 mb-2 rounded-2xl border-2 border-dashed flex items-center justify-center h-14"
      style={{ borderColor: BORDER, backgroundColor: "#F0F0F0" }}>
      <span className="text-xs font-medium" style={{ color: MUTED_FG }}>📢 Ad Banner</span>
    </div>
  );
}

function RewardedAdPrompt({ onClose, featureName }: { onClose: () => void; featureName: string }) {
  return (
    <div className="fixed inset-0 z-50 flex items-end" style={{ backgroundColor: "rgba(0,0,0,0.5)" }}>
      <div className="w-full rounded-t-3xl p-6 space-y-4" style={{ backgroundColor: CARD_BG }}>
        <div className="flex items-center justify-between">
          <h3 className="font-bold text-lg" style={{ color: BLACK }}>Unlock {featureName}</h3>
          <button onClick={onClose}><X size={20} style={{ color: MUTED_FG }} /></button>
        </div>
        <p className="text-sm" style={{ color: MUTED_FG }}>Try this premium feature once by watching a short ad, or go Pro to unlock it forever.</p>
        <PillButton label="▶  Watch an Ad to Try Once" variant="outline" size="md" onClick={onClose} />
        <PillButton label="Unlock Pro — No Ads" variant="primary" size="md" />
      </div>
    </div>
  );
}

function EncryptedTag() {
  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold"
      style={{ backgroundColor: "#E8F5E9", color: "#2E7D32" }}>
      <Lock size={10} />
      Encrypted
    </span>
  );
}

function StatusDot({ status }: { status: "online" | "busy" | "offline" }) {
  const colors = { online: GREEN, busy: "#FF9800", offline: MUTED_FG };
  return <span className="inline-block w-2.5 h-2.5 rounded-full" style={{ backgroundColor: colors[status] }} />;
}

function BackBar({ title, onBack, rightEl }: { title: string; onBack: () => void; rightEl?: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between px-4 py-3 border-b" style={{ borderColor: BORDER, backgroundColor: CARD_BG }}>
      <button onClick={onBack} className="p-1 rounded-full transition-colors active:bg-gray-100">
        <ChevronLeft size={24} style={{ color: BLACK }} />
      </button>
      <span className="font-bold text-base" style={{ fontFamily: "'Roboto', sans-serif", color: BLACK }}>{title}</span>
      <div className="w-8 flex justify-end">{rightEl || null}</div>
    </div>
  );
}

function BottomNav({ active, onNavigate }: { active: string; onNavigate: (s: Screen) => void }) {
  const tabs = [
    { id: "home", label: "Transfer", icon: <Send size={20} /> },
    { id: "history", label: "History", icon: <History size={20} /> },
    { id: "settings", label: "Settings", icon: <Settings size={20} /> },
  ];
  return (
    <div className="border-t flex" style={{ borderColor: BORDER, backgroundColor: CARD_BG }}>
      {tabs.map(t => (
        <button key={t.id} onClick={() => onNavigate(t.id as Screen)}
          className="flex-1 flex flex-col items-center py-3 gap-1 transition-colors"
          style={{ color: active === t.id ? RED : MUTED_FG }}>
          {t.icon}
          <span className="text-xs font-medium">{t.label}</span>
        </button>
      ))}
    </div>
  );
}

// ─── Lottie Placeholder ───────────────────────────────────────────────────────
function AnimPlaceholder({ icon, label, size = 120, bg = LIGHT_RED }: {
  icon: React.ReactNode; label: string; size?: number; bg?: string;
}) {
  return (
    <div className="flex flex-col items-center gap-2">
      <div className="rounded-full flex items-center justify-center animate-pulse"
        style={{ width: size, height: size, backgroundColor: bg }}>
        <span style={{ color: RED }}>{icon}</span>
      </div>
      <span className="text-xs text-center max-w-[160px]" style={{ color: MUTED_FG }}>{label}</span>
    </div>
  );
}

// ─── RadarAnim ─────────────────────────────────────────────────────────────────
function RadarAnim() {
  return (
    <div className="relative flex items-center justify-center" style={{ width: 120, height: 120 }}>
      {[0, 1, 2].map(i => (
        <div key={i} className="absolute rounded-full border-2 animate-ping"
          style={{
            width: 40 + i * 30, height: 40 + i * 30,
            borderColor: RED, opacity: 0.3 - i * 0.08,
            animationDelay: `${i * 0.4}s`, animationDuration: "2s"
          }} />
      ))}
      <div className="rounded-full flex items-center justify-center z-10"
        style={{ width: 44, height: 44, backgroundColor: RED }}>
        <Radio size={22} color={WHITE} />
      </div>
    </div>
  );
}

// ─── Screen 1: Splash ─────────────────────────────────────────────────────────
function SplashScreen({ onContinue }: { onContinue: () => void }) {
  return (
    <div className="flex flex-col min-h-full" style={{ background: `linear-gradient(160deg, #0D0D0D 60%, #1a0505 100%)` }}>
      <div className="flex-1 flex flex-col items-center justify-center px-8 pt-16 pb-8 gap-8">
        <div className="space-y-1 text-center">
          <div className="flex items-center justify-center gap-2 mb-2">
            <div className="w-10 h-10 rounded-2xl flex items-center justify-center" style={{ backgroundColor: RED }}>
              <Zap size={22} color={WHITE} />
            </div>
          </div>
          <h1 className="text-4xl font-black leading-tight" style={{ fontFamily: "'Roboto', sans-serif", color: WHITE }}>
            Welcome to<br />
            <span style={{ color: RED }}>Just Share</span>
          </h1>
        </div>

        <AnimPlaceholder
          icon={<Send size={52} />}
          label="Lottie animation placeholder"
          size={160}
          bg="rgba(236,28,34,0.15)"
        />

        <div className="text-center space-y-2 max-w-xs">
          <p className="text-base font-medium" style={{ color: WHITE }}>
            Transfer files instantly — no internet needed
          </p>
          <p className="text-sm" style={{ color: "#9A9A9A" }}>
            Share photos, videos, documents, and audio files via Bluetooth & Wi-Fi Direct at blazing speeds.
          </p>
        </div>

        <div className="flex gap-2">
          {[RED, LIGHT_RED, "#666"].map((c, i) => (
            <div key={i} className="h-1.5 rounded-full transition-all"
              style={{ width: i === 0 ? 24 : 8, backgroundColor: c }} />
          ))}
        </div>
      </div>

      <div className="px-6 pb-10 space-y-3">
        <PillButton label="Continue" onClick={onContinue} size="lg" />
        <p className="text-center text-xs" style={{ color: "#666" }}>
          By continuing you agree to our Terms & Privacy Policy
        </p>
      </div>
    </div>
  );
}

// ─── Screen 2: Permissions ────────────────────────────────────────────────────
function PermissionsScreen({ onContinue }: { onContinue: () => void }) {
  const perms = [
    { icon: <Bluetooth size={20} />, label: "Bluetooth", desc: "Find & connect nearby devices" },
    { icon: <Wifi size={20} />, label: "Wi-Fi Direct", desc: "High-speed peer-to-peer transfers" },
    { icon: <Image size={20} />, label: "Storage", desc: "Read & save transferred files" },
    { icon: <Bell size={20} />, label: "Notifications", desc: "Transfer progress updates" },
  ];
  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: SURFACE }}>
      <div className="flex-1 flex flex-col items-center justify-center px-6 pt-12 pb-8 gap-8">
        <AnimPlaceholder
          icon={<Wifi size={52} />}
          label="Connectivity animation placeholder"
          size={140}
          bg="rgba(236,28,34,0.1)"
        />
        <div className="text-center space-y-2">
          <h2 className="text-2xl font-black" style={{ fontFamily: "'Roboto', sans-serif", color: BLACK }}>
            A Few Permissions
          </h2>
          <p className="text-sm" style={{ color: MUTED_FG }}>
            Just Share needs these to discover devices and transfer your files securely — all locally, never to the cloud.
          </p>
        </div>

        <div className="w-full space-y-3">
          {perms.map((p, i) => (
            <div key={i} className="flex items-center gap-4 p-4 rounded-2xl border"
              style={{ backgroundColor: CARD_BG, borderColor: BORDER }}>
              <div className="w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0"
                style={{ backgroundColor: LIGHT_RED, color: RED }}>
                {p.icon}
              </div>
              <div>
                <p className="font-semibold text-sm" style={{ color: BLACK }}>{p.label}</p>
                <p className="text-xs" style={{ color: MUTED_FG }}>{p.desc}</p>
              </div>
              <Check size={16} className="ml-auto flex-shrink-0" style={{ color: RED }} />
            </div>
          ))}
        </div>
      </div>

      <div className="px-6 pb-10">
        <PillButton label="Grant Permissions" onClick={onContinue} size="lg" />
      </div>
    </div>
  );
}

// ─── Screen 3: Home / Send or Receive ─────────────────────────────────────────
function HomeScreen({ onNavigate }: { onNavigate: (s: Screen) => void }) {
  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: SURFACE }}>
      <div className="px-6 pt-10 pb-4">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm" style={{ color: MUTED_FG }}>Good morning</p>
            <h1 className="text-2xl font-black" style={{ fontFamily: "'Roboto', sans-serif", color: BLACK }}>
              Just Share
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-9 h-9 rounded-full flex items-center justify-center" style={{ backgroundColor: LIGHT_RED }}>
              <Zap size={18} style={{ color: RED }} />
            </div>
          </div>
        </div>
      </div>

      <div className="flex-1 px-4 space-y-4">
        {/* SEND Card */}
        <button onClick={() => onNavigate("select-files")}
          className="w-full rounded-3xl p-6 flex flex-col items-center justify-center gap-4 transition-transform active:scale-[0.98] shadow-lg"
          style={{ backgroundColor: RED, minHeight: 200 }}>
          <div className="w-16 h-16 rounded-full flex items-center justify-center"
            style={{ backgroundColor: "rgba(255,255,255,0.2)" }}>
            <ArrowUp size={32} color={WHITE} />
          </div>
          <div className="text-center">
            <p className="text-2xl font-black tracking-wide" style={{ color: WHITE }}>SEND</p>
            <p className="text-sm mt-1" style={{ color: "rgba(255,255,255,0.75)" }}>
              Share files with nearby devices
            </p>
          </div>
        </button>

        {/* RECEIVE Card */}
        <button onClick={() => onNavigate("discover-bt")}
          className="w-full rounded-3xl p-6 flex flex-col items-center justify-center gap-4 border-2 transition-transform active:scale-[0.98]"
          style={{ backgroundColor: CARD_BG, borderColor: RED, minHeight: 200 }}>
          <div className="w-16 h-16 rounded-full flex items-center justify-center"
            style={{ backgroundColor: LIGHT_RED }}>
            <ArrowDown size={32} style={{ color: RED }} />
          </div>
          <div className="text-center">
            <p className="text-2xl font-black tracking-wide" style={{ color: RED }}>RECEIVE</p>
            <p className="text-sm mt-1" style={{ color: MUTED_FG }}>
              Accept files from nearby devices
            </p>
          </div>
        </button>

        {/* Quick actions row */}
        <div className="flex gap-2">
          <button onClick={() => onNavigate("qr-scan")}
            className="flex-1 flex items-center gap-2 p-3 rounded-2xl border"
            style={{ backgroundColor: CARD_BG, borderColor: BORDER }}>
            <QrCode size={18} style={{ color: RED }} />
            <span className="text-sm font-medium" style={{ color: BLACK }}>QR Connect</span>
          </button>
          <div
            className="flex-1 flex items-center gap-2 p-3 rounded-2xl border cursor-pointer active:bg-gray-50"
            style={{ backgroundColor: CARD_BG, borderColor: BORDER }}
            onClick={() => onNavigate("secure-send")}
            role="button"
            tabIndex={0}
          >
            <Shield size={18} style={{ color: RED }} />
            <span className="text-sm font-medium" style={{ color: BLACK }}>Secure Send</span>
            <ProBadge onClick={() => onNavigate("pro-upgrade")} />
          </div>
        </div>
      </div>

      <div className="px-4 pt-2">
        <AdBanner />
      </div>
      <BottomNav active="home" onNavigate={onNavigate} />
    </div>
  );
}

// ─── Screen 4: File Selection ─────────────────────────────────────────────────
function FileSelectionScreen({ onBack, onNavigate }: { onBack: () => void; onNavigate: (s: Screen) => void }) {
  const [activeTab, setActiveTab] = useState<"images" | "videos" | "audio">("images");
  const [selected, setSelected] = useState<number[]>([]);

  const tabs = ["images", "videos", "audio"] as const;
  const tabLabels = { images: "Images", videos: "Videos", audio: "Audio" };

  const mockImages = Array.from({ length: 12 }, (_, i) => ({
    id: i,
    color: i % 3 === 0 ? "#E8D5B7" : i % 3 === 1 ? "#C8D8E8" : "#D8E8C8",
    label: `IMG_00${i + 1}`
  }));

  const toggle = (id: number) =>
    setSelected(s => s.includes(id) ? s.filter(x => x !== id) : [...s, id]);

  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: SURFACE }}>
      <BackBar title="Select Files" onBack={onBack} />

      {/* Tab Row */}
      <div className="flex px-4 pt-3 gap-1 border-b" style={{ borderColor: BORDER }}>
        {tabs.map(t => (
          <button key={t} onClick={() => setActiveTab(t)}
            className="flex-1 flex items-center justify-center gap-1.5 pb-3 text-sm font-semibold transition-colors"
            style={{ color: activeTab === t ? RED : MUTED_FG, borderBottom: activeTab === t ? `2px solid ${RED}` : "2px solid transparent" }}>
            {t === "images" && <Image size={14} />}
            {t === "videos" && <Video size={14} />}
            {t === "audio" && <Music size={14} />}
            {tabLabels[t]}
          </button>
        ))}
      </div>

      {/* Grid */}
      <div className="flex-1 overflow-y-auto p-3">
        <div className="grid grid-cols-3 gap-2">
          {mockImages.map(img => {
            const sel = selected.includes(img.id);
            return (
              <button key={img.id} onClick={() => toggle(img.id)}
                className="relative rounded-2xl overflow-hidden aspect-square transition-transform active:scale-95">
                <div className="w-full h-full" style={{ backgroundColor: img.color }} />
                {sel && (
                  <>
                    <div className="absolute inset-0 rounded-2xl" style={{ backgroundColor: "rgba(236,28,34,0.35)" }} />
                    <div className="absolute top-1.5 right-1.5 w-6 h-6 rounded-full flex items-center justify-center"
                      style={{ backgroundColor: RED }}>
                      <Check size={14} color={WHITE} />
                    </div>
                  </>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* FAB */}
      {selected.length > 0 && (
        <div className="absolute bottom-20 right-5">
          <button onClick={() => onNavigate("discover-bt")}
            className="flex items-center gap-2 py-3 px-5 rounded-full shadow-xl text-white font-bold text-sm transition-transform active:scale-95"
            style={{ backgroundColor: RED }}>
            <ArrowUp size={18} />
            Send {selected.length} File{selected.length > 1 ? "s" : ""}
          </button>
        </div>
      )}

      <div className="px-4 pb-4">
        <PillButton
          label={selected.length > 0 ? `Continue with ${selected.length} file${selected.length > 1 ? "s" : ""}` : "Select files above"}
          onClick={() => onNavigate("discover-bt")}
          disabled={selected.length === 0}
          size="lg"
        />
      </div>
    </div>
  );
}

// ─── Screen 5: Device Discovery (Bluetooth) ───────────────────────────────────
function DiscoverBTScreen({ onBack, onNavigate }: { onBack: () => void; onNavigate: (s: Screen) => void }) {
  const [scanning, setScanning] = useState(true);

  const discovered = [
    { name: "Marcus's Galaxy S24", mac: "AC:DE:48:00:11:22", rssi: -45 },
    { name: "Sarah's Pixel 8", mac: "B4:F6:1C:33:44:55", rssi: -62 },
    { name: "OnePlus 12", mac: "78:A2:40:66:77:88", rssi: -71 },
  ];
  const paired = [
    { name: "Home Desktop", mac: "00:1A:2B:3C:4D:5E", rssi: -80 },
  ];

  useEffect(() => {
    const t = setTimeout(() => setScanning(false), 3000);
    return () => clearTimeout(t);
  }, []);

  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: SURFACE }}>
      <BackBar title="Bluetooth Devices" onBack={onBack}
        rightEl={
          <button onClick={() => setScanning(true)} className="p-1">
            <RefreshCw size={18} style={{ color: scanning ? RED : MUTED_FG }}
              className={scanning ? "animate-spin" : ""} />
          </button>
        }
      />

      <div className="flex-1 overflow-y-auto">
        <div className="flex flex-col items-center py-6 gap-3">
          <RadarAnim />
          <p className="text-sm font-medium" style={{ color: scanning ? RED : MUTED_FG }}>
            {scanning ? "Scanning for nearby devices…" : "Scan complete"}
          </p>
        </div>

        <div className="px-4 space-y-4">
          <div>
            <h3 className="text-xs font-bold uppercase tracking-widest mb-2" style={{ color: MUTED_FG }}>
              Available Devices ({discovered.length})
            </h3>
            <div className="rounded-3xl overflow-hidden border divide-y" style={{ borderColor: BORDER }}>
              {discovered.map((d, i) => (
                <button key={i} onClick={() => onNavigate("transfer-progress")}
                  className="w-full flex items-center gap-3 px-4 py-4 bg-white active:bg-gray-50 transition-colors">
                  <div className="w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0"
                    style={{ backgroundColor: LIGHT_RED }}>
                    <Smartphone size={18} style={{ color: RED }} />
                  </div>
                  <div className="flex-1 text-left min-w-0">
                    <p className="text-sm font-semibold truncate" style={{ color: BLACK }}>{d.name}</p>
                    <p className="text-xs font-mono" style={{ color: MUTED_FG }}>{d.mac}</p>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    <StatusDot status="online" />
                    <span className="text-xs" style={{ color: MUTED_FG }}>{d.rssi} dBm</span>
                  </div>
                </button>
              ))}
            </div>
          </div>

          <div>
            <h3 className="text-xs font-bold uppercase tracking-widest mb-2" style={{ color: MUTED_FG }}>
              Paired Devices
            </h3>
            <div className="rounded-3xl overflow-hidden border divide-y" style={{ borderColor: BORDER }}>
              {paired.map((d, i) => (
                <button key={i} onClick={() => onNavigate("transfer-progress")}
                  className="w-full flex items-center gap-3 px-4 py-4 bg-white active:bg-gray-50 transition-colors">
                  <div className="w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0"
                    style={{ backgroundColor: "#E8F5E9" }}>
                    <Monitor size={18} style={{ color: GREEN }} />
                  </div>
                  <div className="flex-1 text-left min-w-0">
                    <p className="text-sm font-semibold truncate" style={{ color: BLACK }}>{d.name}</p>
                    <p className="text-xs font-mono" style={{ color: MUTED_FG }}>{d.mac}</p>
                  </div>
                  <StatusDot status="offline" />
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="px-4 pb-6 pt-3">
        <button onClick={() => onNavigate("discover-wifi")}
          className="w-full flex items-center justify-center gap-2 py-3 rounded-full border-2 font-semibold text-sm"
          style={{ borderColor: RED, color: RED }}>
          <Wifi size={16} />
          Switch to Wi-Fi Direct
        </button>
      </div>
    </div>
  );
}

// ─── Screen 6: Device Discovery (Wi-Fi Direct) ────────────────────────────────
function DiscoverWifiScreen({ onBack, onNavigate }: { onBack: () => void; onNavigate: (s: Screen) => void }) {
  const [enabled, setEnabled] = useState(true);
  const peers = [
    { name: "Lena's iPhone 15", status: "Available" },
    { name: "Work MacBook Pro", status: "Available" },
    { name: "Javi's Android", status: "Connecting…" },
  ];

  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: SURFACE }}>
      <BackBar title="Wi-Fi Direct Peers" onBack={onBack} />

      <div className="px-4 py-4">
        <div className="flex items-center justify-between p-4 rounded-2xl border"
          style={{ backgroundColor: CARD_BG, borderColor: BORDER }}>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-full flex items-center justify-center"
              style={{ backgroundColor: enabled ? LIGHT_RED : "#F0F0F0" }}>
              <Wifi size={18} style={{ color: enabled ? RED : MUTED_FG }} />
            </div>
            <div>
              <p className="text-sm font-semibold" style={{ color: BLACK }}>Wi-Fi Direct</p>
              <p className="text-xs" style={{ color: enabled ? GREEN : MUTED_FG }}>
                {enabled ? "● Enabled" : "○ Disabled"}
              </p>
            </div>
          </div>
          <button onClick={() => setEnabled(e => !e)}
            className="w-12 h-6 rounded-full relative transition-colors"
            style={{ backgroundColor: enabled ? RED : "#CCC" }}>
            <div className="absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-all"
              style={{ left: enabled ? "calc(100% - 1.375rem)" : "2px" }} />
          </button>
        </div>
      </div>

      <div className="flex flex-col items-center py-4 gap-3">
        <AnimPlaceholder icon={<Wifi size={40} />} label="Scanning for Wi-Fi Direct peers…" size={100} />
      </div>

      <div className="flex-1 px-4 space-y-2">
        <h3 className="text-xs font-bold uppercase tracking-widest mb-2" style={{ color: MUTED_FG }}>
          Available P2P Devices
        </h3>
        <div className="rounded-3xl overflow-hidden border divide-y" style={{ borderColor: BORDER, backgroundColor: CARD_BG }}>
          {peers.map((p, i) => (
            <button key={i} onClick={() => onNavigate("transfer-progress")}
              className="w-full flex items-center gap-3 px-4 py-4 active:bg-gray-50 transition-colors">
              <div className="w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0"
                style={{ backgroundColor: LIGHT_RED }}>
                <Wifi size={18} style={{ color: RED }} />
              </div>
              <div className="flex-1 text-left">
                <p className="text-sm font-semibold" style={{ color: BLACK }}>{p.name}</p>
                <p className="text-xs" style={{ color: p.status === "Connecting…" ? "#FF9800" : GREEN }}>{p.status}</p>
              </div>
              <ChevronRight size={16} style={{ color: MUTED_FG }} />
            </button>
          ))}
        </div>
      </div>

      <div className="px-4 pb-6 pt-3">
        <button onClick={() => onNavigate("discover-bt")}
          className="w-full flex items-center justify-center gap-2 py-3 rounded-full border-2 font-semibold text-sm"
          style={{ borderColor: MUTED_FG, color: MUTED_FG }}>
          <Bluetooth size={16} />
          Switch to Bluetooth
        </button>
      </div>
    </div>
  );
}

// ─── Screen 7: Transfer Progress ─────────────────────────────────────────────
function TransferProgressScreen({ onBack, onNavigate }: { onBack: () => void; onNavigate: (s: Screen) => void }) {
  const [progress, setProgress] = useState(42);
  const [msgText, setMsgText] = useState("");

  useEffect(() => {
    const t = setInterval(() => {
      setProgress(p => {
        if (p >= 100) {
          clearInterval(t);
          return 100;
        }
        return p + 2;
      });
    }, 200);
    return () => clearInterval(t);
  }, []);

  const files = [
    { name: "vacation_beach.jpg", size: "4.2 MB", icon: <Image size={16} />, done: true },
    { name: "family_video.mp4", size: "128 MB", icon: <Video size={16} />, done: false, active: true },
    { name: "report_q4.pdf", size: "2.8 MB", icon: <FileText size={16} />, done: false },
    { name: "podcast_ep12.mp3", size: "34 MB", icon: <Music size={16} />, done: false },
  ];

  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: SURFACE }}>
      <BackBar title="Transfer Progress" onBack={onBack} />

      <div className="flex flex-col items-center py-6 gap-2 px-4">
        <AnimPlaceholder icon={<Send size={40} />} label="Transferring files…" size={100} />
        <div className="flex items-center gap-2">
          <span className="text-sm font-bold" style={{ color: BLACK }}>Marcus's Galaxy S24</span>
          <StatusDot status="online" />
        </div>
      </div>

      <div className="flex-1 px-4 overflow-y-auto">
        <div className="rounded-3xl border overflow-hidden" style={{ borderColor: BORDER, backgroundColor: CARD_BG }}>
          {files.map((f, i) => (
            <div key={i} className="px-4 py-4 border-b last:border-0" style={{ borderColor: BORDER }}>
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
                  style={{ backgroundColor: f.done ? "#E8F5E9" : f.active ? LIGHT_RED : "#F0F0F0" }}>
                  <span style={{ color: f.done ? GREEN : f.active ? RED : MUTED_FG }}>{f.icon}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-semibold truncate" style={{ color: BLACK }}>{f.name}</p>
                    {f.active && <EncryptedTag />}
                  </div>
                  <p className="text-xs" style={{ color: MUTED_FG }}>{f.size}</p>
                </div>
                {f.done ? (
                  <CheckCircle size={18} style={{ color: GREEN }} />
                ) : f.active ? (
                  <span className="text-xs font-bold" style={{ color: RED }}>{progress}%</span>
                ) : (
                  <Circle size={18} style={{ color: MUTED_FG }} />
                )}
              </div>
              {f.active && (
                <div className="mt-3">
                  <div className="h-2 rounded-full overflow-hidden" style={{ backgroundColor: LIGHT_RED }}>
                    <div className="h-full rounded-full transition-all duration-300"
                      style={{ width: `${progress}%`, backgroundColor: RED }} />
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      <div className="px-4 pt-3 pb-4 space-y-3">
        {/* Message row */}
        <div className="flex items-center gap-2 p-2 rounded-2xl border" style={{ backgroundColor: CARD_BG, borderColor: BORDER }}>
          <input
            value={msgText}
            onChange={e => setMsgText(e.target.value)}
            placeholder="Send a message…"
            className="flex-1 text-sm bg-transparent outline-none px-2"
            style={{ color: BLACK }}
          />
          <button className="w-8 h-8 rounded-full flex items-center justify-center"
            style={{ backgroundColor: RED }}>
            <Send size={14} color={WHITE} />
          </button>
        </div>
        <button onClick={() => onNavigate("interstitial-ad")}
          className="w-full py-3 rounded-full border-2 font-semibold text-sm transition-colors"
          style={{ borderColor: DARK_RED, color: DARK_RED }}>
          Disconnect
        </button>
      </div>
    </div>
  );
}

// ─── Screen 8: Transfer History ───────────────────────────────────────────────
function HistoryScreen({ onBack, onNavigate }: { onBack: () => void; onNavigate: (s: Screen) => void }) {
  const [items, setItems] = useState([
    { id: 1, name: "vacation_beach.jpg", peer: "Marcus's Galaxy S24", dir: "Sent", size: "4.2 MB", date: "Today 14:32", method: "Wi-Fi", icon: <Image size={16} /> },
    { id: 2, name: "family_video.mp4", peer: "Sarah's Pixel 8", dir: "Received", size: "128 MB", date: "Today 11:05", method: "Bluetooth", icon: <Video size={16} /> },
    { id: 3, name: "report_q4.pdf", peer: "Work MacBook Pro", dir: "Sent", size: "2.8 MB", date: "Yesterday", method: "Wi-Fi", icon: <FileText size={16} /> },
    { id: 4, name: "podcast_ep12.mp3", peer: "Lena's iPhone 15", dir: "Received", size: "34 MB", date: "Mon, Jul 21", method: "Bluetooth", icon: <Music size={16} /> },
  ]);

  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: SURFACE }}>
      <div className="flex items-center justify-between px-4 py-3 border-b" style={{ borderColor: BORDER, backgroundColor: CARD_BG }}>
        <span className="font-black text-lg" style={{ fontFamily: "'Roboto', sans-serif", color: BLACK }}>Transfer History</span>
        <button onClick={() => setItems([])} className="flex items-center gap-1 p-2 rounded-lg active:bg-gray-100">
          <Trash2 size={18} style={{ color: RED }} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-3">
        {items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 gap-4">
            <AnimPlaceholder icon={<History size={40} />} label="No transfer history yet" size={120} />
            <p className="text-sm" style={{ color: MUTED_FG }}>Your transfers will appear here</p>
          </div>
        ) : (
          <div className="rounded-3xl border overflow-hidden" style={{ borderColor: BORDER, backgroundColor: CARD_BG }}>
            {items.map((it, i) => (
              <div key={it.id} className="flex items-center gap-3 px-4 py-4 border-b last:border-0" style={{ borderColor: BORDER }}>
                <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
                  style={{ backgroundColor: it.dir === "Sent" ? LIGHT_RED : "#E8F5E9" }}>
                  <span style={{ color: it.dir === "Sent" ? RED : GREEN }}>{it.icon}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold truncate" style={{ color: BLACK }}>{it.name}</p>
                  <p className="text-xs" style={{ color: MUTED_FG }}>
                    {it.dir === "Sent" ? "↑ Sent to" : "↓ Received from"} {it.peer}
                  </p>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-xs" style={{ color: MUTED_FG }}>{it.size}</span>
                    <span className="text-xs" style={{ color: MUTED_FG }}>·</span>
                    <span className="text-xs" style={{ color: MUTED_FG }}>{it.date}</span>
                    <span className="text-xs px-1.5 py-0.5 rounded-full"
                      style={{ backgroundColor: it.method === "Wi-Fi" ? "#E3F2FD" : LIGHT_RED, color: it.method === "Wi-Fi" ? "#1565C0" : DARK_RED }}>
                      {it.method === "Wi-Fi" ? <Wifi size={8} className="inline mr-0.5" /> : <Bluetooth size={8} className="inline mr-0.5" />}
                      {it.method}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="px-4 pt-1">
        <AdBanner />
      </div>
      <BottomNav active="history" onNavigate={onNavigate} />
    </div>
  );
}

// ─── Screen 9: Settings ───────────────────────────────────────────────────────
function SettingsScreen({ onNavigate }: { onNavigate: (s: Screen) => void }) {
  const [darkMode, setDarkMode] = useState(false);
  const [method, setMethod] = useState<"bluetooth" | "wifi">("bluetooth");
  const [encRequired, setEncRequired] = useState(false);

  const Section = ({ title, children }: { title: string; children: React.ReactNode }) => (
    <div className="space-y-2">
      <p className="text-xs font-bold uppercase tracking-widest px-1" style={{ color: MUTED_FG }}>{title}</p>
      <div className="rounded-3xl border overflow-hidden" style={{ borderColor: BORDER, backgroundColor: CARD_BG }}>
        {children}
      </div>
    </div>
  );

  const Row = ({ icon, label, right, onClick }: { icon: React.ReactNode; label: string; right?: React.ReactNode; onClick?: () => void }) => (
    <div
      className={`w-full flex items-center gap-3 px-4 py-4 border-b last:border-0 transition-colors ${onClick ? "cursor-pointer active:bg-gray-50" : ""}`}
      style={{ borderColor: BORDER }}
      onClick={onClick}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={onClick ? e => e.key === "Enter" && onClick() : undefined}
    >
      <span style={{ color: RED }}>{icon}</span>
      <span className="flex-1 text-sm font-medium text-left" style={{ color: BLACK }}>{label}</span>
      {right}
    </div>
  );

  const Toggle = ({ on, onChange }: { on: boolean; onChange: () => void }) => (
    <button onClick={onChange}
      className="w-12 h-6 rounded-full relative transition-colors flex-shrink-0"
      style={{ backgroundColor: on ? RED : "#CCC" }}>
      <div className="absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-all"
        style={{ left: on ? "calc(100% - 1.375rem)" : "2px" }} />
    </button>
  );

  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: SURFACE }}>
      <div className="px-4 py-3 border-b" style={{ borderColor: BORDER, backgroundColor: CARD_BG }}>
        <span className="font-black text-lg" style={{ fontFamily: "'Roboto', sans-serif", color: BLACK }}>Settings</span>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-5">
        <Section title="Appearance">
          <Row icon={<Eye size={18} />} label="Dark Mode" right={<Toggle on={darkMode} onChange={() => setDarkMode(e => !e)} />} />
        </Section>

        <Section title="Transfer">
          <div className="px-4 py-4">
            <p className="text-sm font-semibold mb-3" style={{ color: BLACK }}>Default Transfer Method</p>
            <div className="space-y-2">
              {[{ v: "bluetooth", label: "Bluetooth", icon: <Bluetooth size={16} /> }, { v: "wifi", label: "Wi-Fi Direct", icon: <Wifi size={16} /> }].map(opt => (
                <button key={opt.v} onClick={() => setMethod(opt.v as "bluetooth" | "wifi")}
                  className="w-full flex items-center gap-3 p-3 rounded-2xl border transition-colors"
                  style={{ borderColor: method === opt.v ? RED : BORDER, backgroundColor: method === opt.v ? LIGHT_RED : "#F8F8F8" }}>
                  <span style={{ color: method === opt.v ? RED : MUTED_FG }}>{opt.icon}</span>
                  <span className="text-sm font-medium" style={{ color: method === opt.v ? RED : BLACK }}>{opt.label}</span>
                  {method === opt.v && <Check size={16} className="ml-auto" style={{ color: RED }} />}
                </button>
              ))}
            </div>
          </div>
        </Section>

        <Section title="Privacy & Security">
          <Row icon={<Shield size={18} />} label="Always require encryption" right={<Toggle on={encRequired} onChange={() => setEncRequired(e => !e)} />} />
          <Row icon={<UserCheck size={18} />} label="Trusted Devices" right={<><ProBadge onClick={() => onNavigate("pro-upgrade")} /><ChevronRight size={16} style={{ color: MUTED_FG }} /></>} onClick={() => onNavigate("trusted-devices")} />
        </Section>

        <Section title="Premium">
          <Row icon={<Crown size={18} />} label="Upgrade to Pro" right={
            <span className="text-xs font-bold px-2 py-1 rounded-full" style={{ backgroundColor: GOLD, color: WHITE }}>$2.99/mo</span>
          } onClick={() => onNavigate("pro-upgrade")} />
        </Section>

        <div className="text-center py-4 space-y-1">
          <p className="text-xs" style={{ color: MUTED_FG }}>Just Share v2.4.1</p>
          <p className="text-xs" style={{ color: MUTED_FG }}>© 2026 Just Share Inc.</p>
        </div>
      </div>

      <BottomNav active="settings" onNavigate={onNavigate} />
    </div>
  );
}

// ─── Screen 10: Secure Direct Send ───────────────────────────────────────────
function SecureSendScreen({ onBack, onNavigate }: { onBack: () => void; onNavigate: (s: Screen) => void }) {
  const [step, setStep] = useState<"connect" | "verify">("connect");
  const code = ["🔑", "7", "4", "🛡️", "2", "9"];

  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: SURFACE }}>
      <BackBar title="Secure Direct Send" onBack={onBack} />

      {step === "connect" ? (
        <div className="flex-1 flex flex-col items-center justify-center px-6 gap-8">
          <div className="relative">
            <div className="w-28 h-28 rounded-full flex items-center justify-center shadow-2xl"
              style={{ background: `linear-gradient(135deg, ${DARK_RED}, ${RED})` }}>
              <Shield size={52} color={WHITE} />
            </div>
            <div className="absolute -bottom-2 -right-2 w-10 h-10 rounded-full flex items-center justify-center"
              style={{ backgroundColor: GREEN }}>
              <Lock size={18} color={WHITE} />
            </div>
          </div>

          <div className="text-center space-y-3">
            <div className="flex items-center justify-center gap-2">
              <h2 className="text-2xl font-black" style={{ color: BLACK }}>End-to-End Encrypted</h2>
            </div>
            <p className="text-sm" style={{ color: MUTED_FG }}>
              Your files are encrypted on your device before transmission. Only the recipient can decrypt them. No keys ever leave your device.
            </p>
          </div>

          <div className="w-full p-4 rounded-2xl" style={{ backgroundColor: "#E8F5E9" }}>
            <div className="flex items-center gap-2">
              <CheckCircle size={16} style={{ color: GREEN }} />
              <span className="text-sm font-semibold" style={{ color: "#2E7D32" }}>AES-256 encryption active</span>
            </div>
          </div>

          <PillButton label="Connect & Verify Identity" size="lg" onClick={() => setStep("verify")} />
        </div>
      ) : (
        <div className="flex-1 flex flex-col items-center justify-center px-6 gap-8">
          <div className="text-center space-y-2">
            <h2 className="text-xl font-black" style={{ color: BLACK }}>Verify Connection</h2>
            <p className="text-sm" style={{ color: MUTED_FG }}>
              Compare this code with Marcus's device. If they match, your connection is authentic.
            </p>
          </div>

          <div className="flex gap-3 flex-wrap justify-center">
            {code.map((c, i) => (
              <div key={i} className="w-12 h-12 rounded-2xl flex items-center justify-center border-2 text-xl"
                style={{ borderColor: RED, backgroundColor: LIGHT_RED }}>
                {c}
              </div>
            ))}
          </div>

          <p className="text-xs text-center" style={{ color: MUTED_FG }}>
            This code expires in 2:00 minutes. Never share this code via message.
          </p>

          <div className="w-full space-y-3">
            <PillButton label="✓  Codes Match — Proceed" size="lg" onClick={() => onNavigate("select-files")} />
            <PillButton label="Codes Don't Match" variant="outline" size="md" onClick={onBack} />
          </div>
        </div>
      )}
    </div>
  );
}

// ─── Screen 11: QR Scan / Display ────────────────────────────────────────────
function QRScreen({ onBack }: { onBack: () => void }) {
  const [mode, setMode] = useState<"scan" | "show">("scan");

  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: "#0D0D0D" }}>
      <div className="flex items-center justify-between px-4 py-3">
        <button onClick={onBack}><ChevronLeft size={24} color={WHITE} /></button>
        <span className="font-bold text-base" style={{ color: WHITE }}>
          {mode === "scan" ? "Scan QR Code" : "My QR Code"}
        </span>
        <div className="w-8" />
      </div>

      {mode === "scan" ? (
        <div className="flex-1 relative flex items-center justify-center">
          {/* Camera view placeholder */}
          <div className="absolute inset-0 flex items-center justify-center"
            style={{ background: "linear-gradient(#111, #222)" }}>
            <Camera size={48} style={{ color: "#444" }} />
          </div>

          {/* Scan frame overlay */}
          <div className="relative z-10 w-64 h-64">
            <div className="absolute inset-0 rounded-3xl border-2" style={{ borderColor: RED }} />
            {/* Corner accents */}
            {[["top-0 left-0", "rounded-tl-3xl border-t-4 border-l-4"],
              ["top-0 right-0", "rounded-tr-3xl border-t-4 border-r-4"],
              ["bottom-0 left-0", "rounded-bl-3xl border-b-4 border-l-4"],
              ["bottom-0 right-0", "rounded-br-3xl border-b-4 border-r-4"]
            ].map(([pos, cls], i) => (
              <div key={i} className={`absolute ${pos} w-8 h-8 ${cls}`} style={{ borderColor: RED }} />
            ))}
            {/* Scan line */}
            <div className="absolute inset-x-0 animate-bounce" style={{ top: "50%", height: 2, backgroundColor: RED, opacity: 0.8 }} />
          </div>

          <div className="absolute bottom-12 text-center">
            <p className="text-sm" style={{ color: WHITE }}>Point camera at the other device's QR code</p>
          </div>
        </div>
      ) : (
        <div className="flex-1 flex flex-col items-center justify-center gap-6 px-8">
          <div className="p-6 rounded-3xl" style={{ backgroundColor: WHITE }}>
            <div className="w-48 h-48 rounded-2xl grid grid-cols-6 gap-0.5 overflow-hidden">
              {Array.from({ length: 36 }, (_, i) => (
                <div key={i} style={{ backgroundColor: Math.random() > 0.5 ? BLACK : WHITE, height: 32, borderRadius: 2 }} />
              ))}
            </div>
          </div>
          <div className="text-center space-y-1">
            <p className="text-base font-bold" style={{ color: WHITE }}>Marcus's Galaxy S24</p>
            <p className="text-xs" style={{ color: "#9A9A9A" }}>Scan this with another device to connect</p>
          </div>
        </div>
      )}

      <div className="px-6 pb-8 space-y-3">
        <div className="flex gap-2">
          <button onClick={() => setMode("scan")}
            className="flex-1 py-3 rounded-full font-semibold text-sm transition-colors"
            style={{ backgroundColor: mode === "scan" ? RED : "rgba(255,255,255,0.1)", color: WHITE }}>
            <ScanLine size={16} className="inline mr-2" />Scan
          </button>
          <button onClick={() => setMode("show")}
            className="flex-1 py-3 rounded-full font-semibold text-sm transition-colors"
            style={{ backgroundColor: mode === "show" ? RED : "rgba(255,255,255,0.1)", color: WHITE }}>
            <QrCode size={16} className="inline mr-2" />Show Mine
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Screen 12: Incoming Transfer Confirmation ────────────────────────────────
function IncomingConfirmScreen({ onClose }: { onClose: () => void }) {
  const files = [
    { name: "trip_photo_001.jpg", size: "3.1 MB", icon: <Image size={14} /> },
    { name: "trip_photo_002.jpg", size: "4.5 MB", icon: <Image size={14} /> },
    { name: "itinerary.pdf", size: "0.8 MB", icon: <FileText size={14} /> },
  ];
  const totalSize = "8.4 MB";

  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: SURFACE }}>
      <BackBar title="Incoming Transfer" onBack={onClose} />

      <div className="flex-1 flex flex-col justify-end">
        {/* Overlay bg */}
        <div className="flex-1 flex items-center justify-center px-4">
          <AnimPlaceholder icon={<Download size={40} />} label="Incoming transfer…" size={120} />
        </div>

        {/* Bottom sheet */}
        <div className="rounded-t-3xl shadow-2xl" style={{ backgroundColor: CARD_BG }}>
          <div className="w-10 h-1 rounded-full mx-auto mt-3 mb-4" style={{ backgroundColor: BORDER }} />

          <div className="px-6 pb-2 space-y-1">
            <p className="text-xs font-bold uppercase tracking-widest" style={{ color: MUTED_FG }}>From</p>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full flex items-center justify-center" style={{ backgroundColor: LIGHT_RED }}>
                <Smartphone size={18} style={{ color: RED }} />
              </div>
              <div>
                <p className="font-bold text-base" style={{ color: BLACK }}>Sarah's Pixel 8</p>
                <p className="text-xs" style={{ color: MUTED_FG }}>B4:F6:1C:33:44:55 · Bluetooth</p>
              </div>
            </div>
          </div>

          <div className="px-6 py-4">
            <div className="rounded-2xl overflow-hidden border" style={{ borderColor: BORDER }}>
              {files.map((f, i) => (
                <div key={i} className="flex items-center gap-3 px-4 py-3 border-b last:border-0" style={{ borderColor: BORDER }}>
                  <div className="w-8 h-8 rounded-xl flex items-center justify-center" style={{ backgroundColor: LIGHT_RED }}>
                    <span style={{ color: RED }}>{f.icon}</span>
                  </div>
                  <span className="flex-1 text-sm truncate" style={{ color: BLACK }}>{f.name}</span>
                  <span className="text-xs" style={{ color: MUTED_FG }}>{f.size}</span>
                </div>
              ))}
            </div>
            <p className="text-xs mt-2 text-right" style={{ color: MUTED_FG }}>Total: {totalSize}</p>
          </div>

          <div className="px-6 pb-8 flex gap-3">
            <button onClick={onClose}
              className="flex-1 py-4 rounded-full border-2 font-bold text-sm"
              style={{ borderColor: DARK_RED, color: DARK_RED }}>
              Reject
            </button>
            <button onClick={onClose}
              className="flex-1 py-4 rounded-full font-bold text-sm"
              style={{ backgroundColor: RED, color: WHITE }}>
              Accept
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Screen 13: Trusted Devices ───────────────────────────────────────────────
function TrustedDevicesScreen({ onBack, onNavigate }: { onBack: () => void; onNavigate: (s: Screen) => void }) {
  const trusted = [
    { name: "Marcus's Galaxy S24", mac: "AC:DE:48:00:11:22" },
    { name: "Home Desktop", mac: "00:1A:2B:3C:4D:5E" },
  ];
  const blocked = [
    { name: "Unknown Device #3", mac: "FF:FF:FF:AB:CD:EF" },
  ];

  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: SURFACE }}>
      <BackBar title="Trusted Devices" onBack={onBack}
        rightEl={
          <button className="p-1">
            <Plus size={20} style={{ color: RED }} />
          </button>
        }
      />

      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-5">
        <div className="p-3 rounded-2xl flex items-start gap-2" style={{ backgroundColor: LIGHT_RED }}>
          <Info size={16} style={{ color: RED, flexShrink: 0, marginTop: 1 }} />
          <p className="text-xs" style={{ color: DARK_RED }}>
            Trusted devices can always send to you without confirmation prompts. Blocked devices are automatically rejected.
          </p>
        </div>

        <div className="space-y-2">
          <p className="text-xs font-bold uppercase tracking-widest" style={{ color: MUTED_FG }}>Trusted ({trusted.length})</p>
          <div className="rounded-3xl border overflow-hidden divide-y" style={{ borderColor: BORDER, backgroundColor: CARD_BG }}>
            {trusted.map((d, i) => (
              <div key={i} className="flex items-center gap-3 px-4 py-4">
                <div className="w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0"
                  style={{ backgroundColor: "#E8F5E9" }}>
                  <UserCheck size={18} style={{ color: GREEN }} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold truncate" style={{ color: BLACK }}>{d.name}</p>
                  <p className="text-xs font-mono" style={{ color: MUTED_FG }}>{d.mac}</p>
                </div>
                <button className="p-1.5 rounded-lg active:bg-gray-100">
                  <X size={16} style={{ color: MUTED_FG }} />
                </button>
              </div>
            ))}
          </div>
        </div>

        <div className="space-y-2">
          <p className="text-xs font-bold uppercase tracking-widest" style={{ color: MUTED_FG }}>Blocked ({blocked.length})</p>
          <div className="rounded-3xl border overflow-hidden divide-y" style={{ borderColor: BORDER, backgroundColor: CARD_BG }}>
            {blocked.map((d, i) => (
              <div key={i} className="flex items-center gap-3 px-4 py-4">
                <div className="w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0"
                  style={{ backgroundColor: LIGHT_RED }}>
                  <Ban size={18} style={{ color: RED }} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold truncate" style={{ color: BLACK }}>{d.name}</p>
                  <p className="text-xs font-mono" style={{ color: MUTED_FG }}>{d.mac}</p>
                </div>
                <button className="text-xs font-semibold px-3 py-1.5 rounded-full border"
                  style={{ borderColor: BORDER, color: MUTED_FG }}>
                  Unblock
                </button>
              </div>
            ))}
          </div>
        </div>

        <button className="w-full flex items-center justify-center gap-2 py-4 rounded-2xl border-2 font-semibold text-sm"
          style={{ borderColor: RED, color: RED }}>
          <Plus size={16} />
          Add Trusted Device
        </button>
      </div>
    </div>
  );
}

// ─── Screen 14: Pro Upgrade ───────────────────────────────────────────────────
function ProUpgradeScreen({ onBack }: { onBack: () => void }) {
  const features = [
    { icon: <Shield size={20} />, label: "Secure Direct Send", desc: "End-to-end encrypted transfers" },
    { icon: <Users size={20} />, label: "Group Send", desc: "Broadcast to multiple devices at once" },
    { icon: <X size={20} />, label: "No Ads", desc: "Completely ad-free experience" },
    { icon: <Repeat size={20} />, label: "Resume Transfers", desc: "Pick up where interrupted transfers left off" },
    { icon: <UserCheck size={20} />, label: "Trusted Devices", desc: "Instant auto-accept from trusted peers" },
    { icon: <Infinity size={20} />, label: "Unlimited History", desc: "Store your full transfer log" },
  ];

  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: "#0D0D0D" }}>
      <div className="flex items-center justify-between px-4 py-3">
        <button onClick={onBack}><X size={24} color={WHITE} /></button>
        <span className="font-black text-base" style={{ color: WHITE }}>Upgrade to Pro</span>
        <div className="w-8" />
      </div>

      {/* Hero */}
      <div className="flex flex-col items-center py-8 px-6 gap-4">
        <div className="w-20 h-20 rounded-3xl flex items-center justify-center shadow-2xl"
          style={{ background: `linear-gradient(135deg, ${GOLD}, #B8860B)` }}>
          <Crown size={40} color={WHITE} />
        </div>
        <div className="text-center">
          <h2 className="text-3xl font-black" style={{ color: WHITE }}>Just Share <span style={{ color: GOLD }}>Pro</span></h2>
          <p className="text-sm mt-1" style={{ color: "#9A9A9A" }}>Everything you need, nothing you don't</p>
        </div>
      </div>

      {/* Features */}
      <div className="flex-1 px-4 overflow-y-auto">
        <div className="rounded-3xl overflow-hidden divide-y"
          style={{ borderColor: "rgba(255,255,255,0.1)", backgroundColor: "#1A1A1A" }}>
          {features.map((f, i) => (
            <div key={i} className="flex items-center gap-4 px-5 py-4">
              <div className="w-10 h-10 rounded-2xl flex items-center justify-center flex-shrink-0"
                style={{ background: `linear-gradient(135deg, ${GOLD}33, ${GOLD}66)` }}>
                <span style={{ color: GOLD }}>{f.icon}</span>
              </div>
              <div>
                <p className="text-sm font-bold" style={{ color: WHITE }}>{f.label}</p>
                <p className="text-xs" style={{ color: "#777" }}>{f.desc}</p>
              </div>
              <CheckCircle size={16} className="ml-auto flex-shrink-0" style={{ color: GOLD }} />
            </div>
          ))}
        </div>
      </div>

      <div className="px-6 py-6 space-y-3">
        <div className="text-center">
          <p className="text-4xl font-black" style={{ color: WHITE }}>$2.99<span className="text-lg font-normal" style={{ color: "#777" }}>/month</span></p>
          <p className="text-xs mt-1" style={{ color: "#555" }}>or $19.99/year — save 44%</p>
        </div>
        <button className="w-full py-4 rounded-full font-black text-base shadow-2xl transition-transform active:scale-95"
          style={{ background: `linear-gradient(135deg, ${GOLD}, #B8860B)`, color: WHITE }}>
          🔓  Unlock Pro
        </button>
        <button onClick={onBack} className="w-full py-2 text-sm" style={{ color: "#555" }}>
          Restore Purchase
        </button>
      </div>
    </div>
  );
}

// ─── Interstitial Ad ──────────────────────────────────────────────────────────
function InterstitialAdScreen({ onClose }: { onClose: () => void }) {
  const [countdown, setCountdown] = useState(5);

  useEffect(() => {
    if (countdown <= 0) return;
    const t = setTimeout(() => setCountdown(c => c - 1), 1000);
    return () => clearTimeout(t);
  }, [countdown]);

  return (
    <div className="flex flex-col min-h-full" style={{ backgroundColor: "#0D0D0D" }}>
      <div className="flex items-center justify-between px-4 py-3">
        <div className="px-2 py-1 rounded text-xs" style={{ backgroundColor: "#333", color: "#999" }}>AD</div>
        <button onClick={onClose} disabled={countdown > 0}
          className="flex items-center gap-1 px-3 py-1.5 rounded-full text-sm font-medium transition-colors"
          style={{ backgroundColor: countdown > 0 ? "#222" : "#333", color: countdown > 0 ? "#666" : WHITE }}>
          {countdown > 0 ? `${countdown}s` : <><X size={14} className="mr-1" />Close</>}
        </button>
      </div>

      <div className="flex-1 flex flex-col items-center justify-center px-8 gap-8">
        <div className="w-full aspect-video rounded-3xl flex items-center justify-center"
          style={{ backgroundColor: "#1A1A1A", border: "2px dashed #333" }}>
          <div className="text-center gap-2 flex flex-col items-center">
            <Play size={40} style={{ color: "#444" }} />
            <p className="text-sm" style={{ color: "#555" }}>Interstitial Ad Placeholder</p>
            <p className="text-xs" style={{ color: "#444" }}>Full-screen ad shown after completed transfer</p>
          </div>
        </div>
      </div>

      <div className="px-6 pb-10 space-y-4 text-center">
        <button className="w-full py-4 rounded-full font-bold text-base transition-transform active:scale-95"
          style={{ background: `linear-gradient(135deg, ${GOLD}, #B8860B)`, color: WHITE }}>
          <Crown size={18} className="inline mr-2" />
          Remove Ads — Go Pro
        </button>
        <button onClick={onClose} className="text-sm" style={{ color: "#555" }}>
          Continue to app
        </button>
      </div>
    </div>
  );
}

// ─── Screen Navigator ─────────────────────────────────────────────────────────

const SCREEN_LABELS: Record<Screen, string> = {
  splash: "1. Splash",
  permissions: "2. Permissions",
  home: "3. Home",
  "select-files": "4. File Selection",
  "discover-bt": "5. BT Discovery",
  "discover-wifi": "6. Wi-Fi Direct",
  "transfer-progress": "7. Transfer Progress",
  history: "8. History",
  settings: "9. Settings",
  "secure-send": "10. Secure Send",
  "qr-scan": "11. QR Scan",
  "incoming-confirm": "12. Incoming",
  "trusted-devices": "13. Trusted Devices",
  "pro-upgrade": "14. Pro Upgrade",
  "interstitial-ad": "Ad Interstitial",
};

const ALL_SCREENS: Screen[] = [
  "splash", "permissions", "home", "select-files",
  "discover-bt", "discover-wifi", "transfer-progress",
  "history", "settings", "secure-send", "qr-scan",
  "incoming-confirm", "trusted-devices", "pro-upgrade", "interstitial-ad"
];

export default function App() {
  const [activeScreen, setActiveScreen] = useState<Screen>("splash");
  const [showRewardedAd, setShowRewardedAd] = useState(false);
  const [navOpen, setNavOpen] = useState(false);

  const navigate = (s: Screen) => {
    setNavOpen(false);
    setActiveScreen(s);
  };

  const renderScreen = () => {
    switch (activeScreen) {
      case "splash": return <SplashScreen onContinue={() => navigate("permissions")} />;
      case "permissions": return <PermissionsScreen onContinue={() => navigate("home")} />;
      case "home": return <HomeScreen onNavigate={navigate} />;
      case "select-files": return <FileSelectionScreen onBack={() => navigate("home")} onNavigate={navigate} />;
      case "discover-bt": return <DiscoverBTScreen onBack={() => navigate("home")} onNavigate={navigate} />;
      case "discover-wifi": return <DiscoverWifiScreen onBack={() => navigate("discover-bt")} onNavigate={navigate} />;
      case "transfer-progress": return <TransferProgressScreen onBack={() => navigate("discover-bt")} onNavigate={navigate} />;
      case "history": return <HistoryScreen onBack={() => navigate("home")} onNavigate={navigate} />;
      case "settings": return <SettingsScreen onNavigate={navigate} />;
      case "secure-send": return <SecureSendScreen onBack={() => navigate("home")} onNavigate={navigate} />;
      case "qr-scan": return <QRScreen onBack={() => navigate("home")} />;
      case "incoming-confirm": return <IncomingConfirmScreen onClose={() => navigate("home")} />;
      case "trusted-devices": return <TrustedDevicesScreen onBack={() => navigate("settings")} onNavigate={navigate} />;
      case "pro-upgrade": return <ProUpgradeScreen onBack={() => navigate("home")} />;
      case "interstitial-ad": return <InterstitialAdScreen onClose={() => navigate("history")} />;
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4"
      style={{ background: "linear-gradient(135deg, #1a1a1a 0%, #2d0a0a 50%, #1a1a1a 100%)", fontFamily: "'Inter', 'Roboto', sans-serif" }}>

      {/* Desktop layout hint */}
      <div className="hidden lg:flex flex-col gap-3 w-64 mr-8">
        <div className="text-sm font-bold" style={{ color: WHITE }}>
          <span style={{ color: RED }}>Just Share</span> UI Kit
        </div>
        <p className="text-xs" style={{ color: "#666" }}>14 screens + ad & monetization components</p>
        <div className="space-y-1 max-h-[600px] overflow-y-auto pr-1">
          {ALL_SCREENS.map(s => (
            <button key={s} onClick={() => navigate(s)}
              className="w-full text-left px-3 py-2 rounded-xl text-xs font-medium transition-colors"
              style={{
                backgroundColor: activeScreen === s ? RED : "rgba(255,255,255,0.05)",
                color: activeScreen === s ? WHITE : "#888"
              }}>
              {SCREEN_LABELS[s]}
            </button>
          ))}
        </div>
      </div>

      {/* Phone frame */}
      <div className="relative"
        style={{ width: 375, minHeight: 700, flexShrink: 0 }}>

        {/* Status bar */}
        <div className="flex items-center justify-between px-5 py-2 text-xs font-semibold rounded-t-[2.5rem]"
          style={{
            backgroundColor: activeScreen === "splash" || activeScreen === "pro-upgrade" || activeScreen === "qr-scan" || activeScreen === "interstitial-ad" ? "#0D0D0D" : CARD_BG,
            color: activeScreen === "splash" || activeScreen === "pro-upgrade" || activeScreen === "qr-scan" || activeScreen === "interstitial-ad" ? WHITE : BLACK
          }}>
          <span>9:41</span>
          <div className="flex items-center gap-1">
            <div className="w-1 h-3 rounded-sm" style={{ backgroundColor: "currentColor", opacity: 0.4 }} />
            <div className="w-1 h-4 rounded-sm" style={{ backgroundColor: "currentColor", opacity: 0.7 }} />
            <div className="w-1 h-5 rounded-sm" style={{ backgroundColor: "currentColor" }} />
            <Wifi size={12} className="ml-1" />
          </div>
        </div>

        {/* Screen content */}
        <div className="overflow-y-auto relative"
          style={{ height: 720, borderLeft: `1px solid ${BORDER}`, borderRight: `1px solid ${BORDER}` }}>
          {renderScreen()}
          {showRewardedAd && <RewardedAdPrompt onClose={() => setShowRewardedAd(false)} featureName="Secure Direct Send" />}
        </div>

        {/* Home indicator */}
        <div className="flex justify-center py-2 rounded-b-[2.5rem]"
          style={{
            backgroundColor: activeScreen === "splash" || activeScreen === "pro-upgrade" || activeScreen === "qr-scan" || activeScreen === "interstitial-ad" ? "#0D0D0D" : CARD_BG,
          }}>
          <div className="w-24 h-1 rounded-full" style={{ backgroundColor: activeScreen === "splash" ? "#333" : BORDER }} />
        </div>

        {/* Screen nav overlay for mobile */}
        <div className="lg:hidden absolute -bottom-14 left-0 right-0 flex items-center justify-center gap-2">
          <button onClick={() => navigate(ALL_SCREENS[(ALL_SCREENS.indexOf(activeScreen) - 1 + ALL_SCREENS.length) % ALL_SCREENS.length])}
            className="px-4 py-2 rounded-full text-xs font-semibold"
            style={{ backgroundColor: "rgba(255,255,255,0.1)", color: WHITE }}>
            ‹ Prev
          </button>
          <button onClick={() => setNavOpen(n => !n)}
            className="px-4 py-2 rounded-full text-xs font-semibold"
            style={{ backgroundColor: RED, color: WHITE }}>
            {SCREEN_LABELS[activeScreen]}
          </button>
          <button onClick={() => navigate(ALL_SCREENS[(ALL_SCREENS.indexOf(activeScreen) + 1) % ALL_SCREENS.length])}
            className="px-4 py-2 rounded-full text-xs font-semibold"
            style={{ backgroundColor: "rgba(255,255,255,0.1)", color: WHITE }}>
            Next ›
          </button>
        </div>
      </div>

      {/* Mobile screen picker modal */}
      {navOpen && (
        <div className="lg:hidden fixed inset-0 z-50 flex items-end"
          style={{ backgroundColor: "rgba(0,0,0,0.7)" }}
          onClick={() => setNavOpen(false)}>
          <div className="w-full p-4 rounded-t-3xl grid grid-cols-2 gap-2"
            style={{ backgroundColor: "#1A1A1A" }}
            onClick={e => e.stopPropagation()}>
            {ALL_SCREENS.map(s => (
              <button key={s} onClick={() => navigate(s)}
                className="py-3 px-4 rounded-2xl text-sm font-medium text-left transition-colors"
                style={{
                  backgroundColor: activeScreen === s ? RED : "rgba(255,255,255,0.05)",
                  color: activeScreen === s ? WHITE : "#888"
                }}>
                {SCREEN_LABELS[s]}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
