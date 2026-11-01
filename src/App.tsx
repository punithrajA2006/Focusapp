import React, { useState, useEffect, useMemo } from 'react';
import {
  Shield,
  Smartphone,
  Terminal,
  FileCode,
  CheckCircle2,
  AlertTriangle,
  Lock,
  Layers,
  Accessibility,
  Activity,
  Copy,
  Check,
  Clock,
  RotateCcw,
  Sparkles,
  ChevronRight,
  Play,
  XCircle,
  Youtube,
  Key,
  Radio,
  Calendar,
  LockOpen,
  Wifi,
  WifiOff,
  Flame,
  Award,
  Zap,
  ExternalLink,
  Laptop,
  Search,
  Filter,
  Sliders,
  RefreshCw,
  PhoneCall,
  Settings as SettingsIcon,
  BookOpen,
  FolderTree,
  Send,
  Database,
  Eye,
  AlertOctagon,
  HelpCircle,
  Info,
  ArrowRight,
  Download,
  CheckSquare,
  Square,
  MessageSquareOff,
  X,
  FileText,
  Calculator,
  Compass,
  Lightbulb,
  Code2,
  Cpu,
  MonitorCheck
} from 'lucide-react';

interface AppItem {
  pkg: string;
  name: string;
  category: 'Distraction' | 'Study Tools' | 'Utility' | 'Safety Lifeline';
  isLocked: boolean;
  fixed?: boolean;
  iconName: 'instagram' | 'youtube' | 'reddit' | 'twitter' | 'game' | 'pdf' | 'docs' | 'calc' | 'phone';
}

interface CodeFileItem {
  name: string;
  category: 'Android Kotlin' | 'Fedora Companion' | 'Android XML & Manifest' | 'Gradle Build';
  language: string;
  path: string;
  description: string;
  code: string;
}

const ALL_CODE_FILES: CodeFileItem[] = [
  {
    name: 'AbsoluteSession.kt',
    category: 'Android Kotlin',
    language: 'kotlin',
    path: 'android/app/src/main/java/com/focuslock/data/model/AbsoluteSession.kt',
    description: 'Monotonic hardware clock tracking and anti-clock-tamper reboot reconciliation.',
    code: `package com.focuslock.data.model

import android.os.Parcelable
import android.os.SystemClock
import kotlinx.parcelize.Parcelize

enum class LockMode { STRICT_WHITELIST, SELECTIVE_BLACKLIST, PARTIAL_LOCK }

@Parcelize
data class AbsoluteSession(
    val sessionId: String,
    val targetDurationMs: Long,
    val startElapsedRealtimeMs: Long,
    val lastPersistedElapsedRealtimeMs: Long,
    val accumulatedProvenElapsedMs: Long = 0L,
    val startWallClockMs: Long,
    val lastPersistedWallClockMs: Long,
    val bootCounter: Int = 0,
    val config: AbsoluteSessionConfig,
    val status: SessionStatus = SessionStatus.ACTIVE
) : Parcelable {
    fun computeRemainingMs(currentElapsedRealtimeMs: Long): Long {
        if (status != SessionStatus.ACTIVE) return 0L
        val currentBootElapsed = (currentElapsedRealtimeMs - startElapsedRealtimeMs).coerceAtLeast(0L)
        val totalElapsed = accumulatedProvenElapsedMs + currentBootElapsed
        return (targetDurationMs - totalElapsed).coerceAtLeast(0L)
    }

    fun reconcileAfterReboot(newBootElapsedRealtimeMs: Long, currentWallClockMs: Long): AbsoluteSession {
        val preRebootDelta = (lastPersistedElapsedRealtimeMs - startElapsedRealtimeMs).coerceAtLeast(0L)
        val newAccumulated = accumulatedProvenElapsedMs + preRebootDelta
        val wallDeltaMs = currentWallClockMs - lastPersistedWallClockMs
        val isWallClockSuspect = wallDeltaMs < 0 || wallDeltaMs > 10 * 60 * 1000L
        val offlineCreditMs = if (isWallClockSuspect) 0L else wallDeltaMs.coerceIn(0L, 180000L)
        val totalProven = newAccumulated + offlineCreditMs
        val remaining = (targetDurationMs - totalProven).coerceAtLeast(0L)
        return this.copy(
            startElapsedRealtimeMs = newBootElapsedRealtimeMs,
            lastPersistedElapsedRealtimeMs = newBootElapsedRealtimeMs,
            accumulatedProvenElapsedMs = totalProven,
            lastPersistedWallClockMs = currentWallClockMs,
            status = if (remaining <= 0L) SessionStatus.COMPLETED else SessionStatus.ACTIVE
        )
    }
}`
  },
  {
    name: 'focuslock_crypto.py',
    category: 'Fedora Companion',
    language: 'python',
    path: 'companion/focuslock_crypto.py',
    description: 'HMAC-SHA256 signature generator, nonce validation, and offline token format.',
    code: `import hmac, hashlib, json, base64, secrets, time

def sign_payload(secret_hex: str, payload: dict) -> str:
    canonical = json.dumps(payload, sort_keys=True, separators=(',', ':')).encode('utf-8')
    return hmac.new(bytes.fromhex(secret_hex), canonical, hashlib.sha256).hexdigest()

def create_stop_command(secret_hex: str, session_id: str) -> dict:
    payload = {
        "action": "STOP_SESSION",
        "session_id": session_id,
        "timestamp": int(time.time()),
        "nonce": secrets.token_hex(16)
    }
    return {"payload": payload, "signature": sign_payload(secret_hex, payload)}

def create_offline_token(secret_hex: str, session_id: str) -> str:
    packet = create_stop_command(secret_hex, session_id)
    raw = json.dumps(packet).encode('utf-8')
    return base64.urlsafe_b64encode(raw).decode('utf-8').rstrip('=')`
  },
  {
    name: 'cli.py',
    category: 'Fedora Companion',
    language: 'python',
    path: 'companion/cli.py',
    description: 'Fedora Linux command line interface (focuslock stop, pair, token, status).',
    code: `#!/usr/bin/env python3
import sys, argparse, json, urllib.request
from focuslock_crypto import create_stop_command, create_offline_token

def cmd_stop(args):
    packet = create_stop_command(secret, "active_session")
    try:
        req = urllib.request.Request("http://" + phone_ip + ":8765/api/stop",
            data=json.dumps(packet).encode('utf-8'),
            headers={'Content-Type': 'application/json'}, method='POST')
        with urllib.request.urlopen(req, timeout=2.5) as resp:
            print("[SUCCESS] Stop command verified by phone over Wi-Fi!")
    except Exception:
        print("[!] Wi-Fi offline. Use this signed one-time token on your phone:")
        print(create_offline_token(secret, "active_session"))`
  },
  {
    name: 'FocusLockHttpServer.kt',
    category: 'Android Kotlin',
    language: 'kotlin',
    path: 'android/app/src/main/java/com/focuslock/net/FocusLockHttpServer.kt',
    description: 'Embedded NanoHTTPD server on port 8765 verifying LAN HMAC signatures.',
    code: `package com.focuslock.net

import fi.iki.elonen.NanoHTTPD
import com.focuslock.crypto.HmacSigner
import com.focuslock.data.model.SessionStatus

class FocusLockHttpServer(private val app: FocusLockApplication) : NanoHTTPD(8765) {
    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST && session.uri == "/api/stop") {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val json = files["postData"] ?: ""
            val secret = app.repository.pairingConfigFlow.value.hmacSecretHex
            if (HmacSigner.verifyCommandPacket(secret, json)) {
                app.repository.endSession(SessionStatus.STOPPED_BY_LAPTOP)
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\\"success\\":true}")
            }
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json", "{\\"error\\":\\"Invalid HMAC\\"}")
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }
}`
  },
  {
    name: 'DeviceOwnerManager.kt',
    category: 'Android Kotlin',
    language: 'kotlin',
    path: 'android/app/src/main/java/com/focuslock/admin/DeviceOwnerManager.kt',
    description: 'Device Owner restrictions (DISALLOW_SAFE_BOOT, DISALLOW_FACTORY_RESET, block uninstall).',
    code: `package com.focuslock.admin

import android.app.admin.DevicePolicyManager
import android.os.UserManager

class DeviceOwnerManager(private val context: Context) {
    fun applyStrictLockRestrictions(allowedPackages: Set<String>) {
        if (!isDeviceOwner) return
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
        dpm.setUninstallBlocked(adminComponent, context.packageName, true)
        dpm.setLockTaskPackages(adminComponent, (allowedPackages + context.packageName).toTypedArray())
    }
}`
  }
];

export default function App() {
  const [activeTab, setActiveTab] = useState<'phone' | 'laptop' | 'youtube' | 'bypass' | 'architecture' | 'code'>('phone');
  const [selectedFileIndex, setSelectedFileIndex] = useState(0);
  const [copiedText, setCopiedText] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Setup Guide Modal State
  const [showSetupGuide, setShowSetupGuide] = useState(false);
  const [showHowItWorksBanner, setShowHowItWorksBanner] = useState(true);

  // Fedora Companion Sub-tab: Terminal or Web Dashboard
  const [companionView, setCompanionView] = useState<'terminal' | 'dashboard'>('terminal');

  // Pairing Credentials (Fedora Laptop <-> Android Phone)
  const sharedSecret = '7f89b4c2e10a8d76e4b1c9f28a7d3e65987210acbf943210abedcf9081234567';
  const [phoneIp, setPhoneIp] = useState('192.168.1.105');

  // App Selection list for session
  const [apps, setApps] = useState<AppItem[]>([
    { pkg: 'com.instagram.android', name: 'Instagram', category: 'Distraction', isLocked: true, iconName: 'instagram' },
    { pkg: 'com.google.android.youtube', name: 'YouTube (Official App)', category: 'Distraction', isLocked: true, iconName: 'youtube' },
    { pkg: 'com.reddit.frontpage', name: 'Reddit', category: 'Distraction', isLocked: true, iconName: 'reddit' },
    { pkg: 'com.twitter.android', name: 'X / Twitter', category: 'Distraction', isLocked: true, iconName: 'twitter' },
    { pkg: 'com.supercell.clashroyale', name: 'Clash Royale', category: 'Distraction', isLocked: true, iconName: 'game' },
    { pkg: 'com.adobe.reader', name: 'PDF Reader', category: 'Study Tools', isLocked: false, iconName: 'pdf' },
    { pkg: 'com.google.android.apps.docs.editors.docs', name: 'Google Docs / Notes', category: 'Study Tools', isLocked: false, iconName: 'docs' },
    { pkg: 'com.google.android.calculator', name: 'Calculator', category: 'Utility', isLocked: false, iconName: 'calc' },
    { pkg: 'com.android.dialer', name: 'Phone Dialer', category: 'Safety Lifeline', isLocked: false, fixed: true, iconName: 'phone' }
  ]);

  const [appSearchQuery, setAppSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<'All' | 'Distraction' | 'Study Tools' | 'Utility'>('All');

  // Session & Lock Mode
  const [lockMode, setLockMode] = useState<'SELECTIVE_BLACKLIST' | 'STRICT_WHITELIST' | 'PARTIAL_LOCK'>('SELECTIVE_BLACKLIST');
  const [durationMinutes, setDurationMinutes] = useState(50);

  // Active Session State
  const [activeSession, setActiveSession] = useState<{
    id: string;
    remainingSeconds: number;
    initialSeconds: number;
    mode: 'SELECTIVE_BLACKLIST' | 'STRICT_WHITELIST' | 'PARTIAL_LOCK';
    lockedPackages: string[];
    reboots: number;
    clockTampersDefeated: number;
  } | null>(null);

  // Foreground active app state
  const [foregroundApp, setForegroundApp] = useState<{
    pkg: string;
    name: string;
    isBlocked: boolean;
  }>({
    pkg: 'com.focuslock',
    name: 'FocusLock',
    isBlocked: false
  });

  // Offline stop token input modal state
  const [showOfflineModal, setShowOfflineModal] = useState(false);
  const [offlineTokenInput, setOfflineTokenInput] = useState('');
  const [offlineTokenError, setOfflineTokenError] = useState<string | null>(null);

  // Laptop companion state & terminal simulation
  const [simulatedNetworkOnline, setSimulatedNetworkOnline] = useState(true);
  const [terminalInput, setTerminalInput] = useState('');
  const [terminalHistory, setTerminalHistory] = useState<string[]>([
    'FocusLock Companion v1.0.0 (Fedora Linux x86_64)',
    'Loaded HMAC-SHA256 secret from ~/.config/focuslock/focuslock.db',
    'Tip: Type commands or use the quick buttons below.'
  ]);

  const [studyStats, setStudyStats] = useState({
    totalStudyHours: 24.5,
    completedSessions: 28,
    laptopStopsIssued: 5,
    currentStreakDays: 6
  });

  const [recentSessions, setRecentSessions] = useState([
    { id: 'sess-8491', duration: '60 min', mode: 'Selective', exit: 'Timer (Normal)', date: 'Today, 18:30' },
    { id: 'sess-8490', duration: '45 min', mode: 'Strict', exit: 'Timer (Normal)', date: 'Today, 14:15' },
    { id: 'sess-8489', duration: '90 min', mode: 'Selective', exit: 'Laptop CLI', date: 'Yesterday, 20:00' },
    { id: 'sess-8488', duration: '50 min', mode: 'Partial', exit: 'Timer (Normal)', date: 'Oct 30, 16:45' }
  ]);

  // YouTube Study Filter State
  const [ytSearchTerm, setYtSearchTerm] = useState('MIT OpenCourseWare Linear Algebra');
  const [ytIsShort, setYtIsShort] = useState(false);
  const [ytChannelSelected, setYtChannelSelected] = useState('MIT OpenCourseWare');

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3000);
  };

  // Clock ticker for session
  useEffect(() => {
    if (!activeSession) return;
    const interval = setInterval(() => {
      setActiveSession((prev) => {
        if (!prev) return null;
        if (prev.remainingSeconds <= 1) {
          setStudyStats((s) => ({
            ...s,
            totalStudyHours: +(s.totalStudyHours + prev.initialSeconds / 3600).toFixed(1),
            completedSessions: s.completedSessions + 1
          }));
          showToast('Study session completed! Great work.');
          return null;
        }
        return { ...prev, remainingSeconds: prev.remainingSeconds - 1 };
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [activeSession]);

  const filteredApps = useMemo(() => {
    return apps.filter((a) => {
      const matchCat = selectedCategory === 'All' || a.category === selectedCategory;
      const matchSearch =
        a.name.toLowerCase().includes(appSearchQuery.toLowerCase()) ||
        a.pkg.toLowerCase().includes(appSearchQuery.toLowerCase());
      return matchCat && matchSearch;
    });
  }, [apps, selectedCategory, appSearchQuery]);

  const handleStartSession = (customMinutes?: number) => {
    const mins = customMinutes ?? durationMinutes;
    const lockedPkgs = apps.filter((a) => a.isLocked).map((a) => a.pkg);
    setActiveSession({
      id: 'sess-' + Math.floor(1000 + Math.random() * 9000),
      remainingSeconds: mins * 60,
      initialSeconds: mins * 60,
      mode: lockMode,
      lockedPackages: lockedPkgs,
      reboots: 0,
      clockTampersDefeated: 0
    });
    setForegroundApp({
      pkg: 'com.adobe.reader',
      name: 'PDF Reader',
      isBlocked: false
    });
    setTerminalHistory((prev) => [
      ...prev,
      `[EVENT] Phone started session (${mins}m, mode: ${lockMode}). Foreground service active.`
    ]);
    showToast(`Session locked for ${mins} minutes. Distractions blocked!`);
  };

  const handleLaunchApp = (pkg: string, name: string) => {
    if (!activeSession) {
      setForegroundApp({ pkg, name, isBlocked: false });
      return;
    }
    if (pkg === 'com.android.dialer' || pkg === 'com.focuslock') {
      setForegroundApp({ pkg, name, isBlocked: false });
      return;
    }
    let blocked = false;
    if (activeSession.mode === 'STRICT_WHITELIST') {
      blocked = !apps.find((a) => a.pkg === pkg && !a.isLocked);
    } else {
      blocked = activeSession.lockedPackages.includes(pkg);
    }
    setForegroundApp({ pkg, name, isBlocked: blocked });
    if (blocked) {
      showToast(`Blocked ${name}! "Back to Study" overlay displayed.`);
    }
  };

  const executeTerminalCommand = (cmd: string) => {
    const trimmed = cmd.trim();
    if (!trimmed) return;
    setTerminalHistory((prev) => [...prev, `punith@fedora:~$ ${trimmed}`]);

    if (trimmed === 'clear') {
      setTerminalHistory(['Terminal cleared. FocusLock Companion active.']);
      return;
    }

    if (trimmed === 'focuslock status') {
      setTerminalHistory((prev) => [
        ...prev,
        '  Pairing Status    : Verified (HMAC-SHA256)',
        `  Target Phone IP   : ${phoneIp}:8765`,
        `  Active Session    : ${activeSession ? `${activeSession.id} (${Math.ceil(activeSession.remainingSeconds / 60)}m left)` : 'None (Idle)'}`,
        `  Total Study Hours : ${studyStats.totalStudyHours} hrs`,
        `  Completed Sessions: ${studyStats.completedSessions}`
      ]);
      showToast('Checked system status.');
    } else if (trimmed === 'focuslock pair') {
      setTerminalHistory((prev) => [
        ...prev,
        '  [+] HMAC-SHA256 Secret: ' + sharedSecret,
        `  [+] Pairing payload ready for QR scan: {"v":1,"secret":"${sharedSecret}","laptop":"fedora.local"}`
      ]);
      showToast('Generated pairing credentials.');
    } else if (trimmed.startsWith('focuslock stop')) {
      if (!activeSession) {
        setTerminalHistory((prev) => [...prev, '  [-] Error: No active study session running on phone.']);
        showToast('No active session to stop.');
        return;
      }
      if (simulatedNetworkOnline) {
        setActiveSession(null);
        setForegroundApp({ pkg: 'com.focuslock', name: 'FocusLock', isBlocked: false });
        setStudyStats((s) => ({ ...s, laptopStopsIssued: s.laptopStopsIssued + 1 }));
        setRecentSessions((prev) => [
          { id: activeSession.id, duration: `${Math.round(activeSession.initialSeconds / 60)} min`, mode: 'Selective', exit: 'Laptop CLI', date: 'Just now' },
          ...prev
        ]);
        setTerminalHistory((prev) => [
          ...prev,
          `  [*] Dispatched signed POST to http://${phoneIp}:8765/api/stop`,
          '  [+] HTTP 200 OK: Phone verified HMAC signature and unlocked.'
        ]);
        showToast('Success! Phone unlocked via Fedora CLI.');
      } else {
        const token = 'eyJhbGciOiJIUzI1NiJ9.' + btoa(`stop:${activeSession.id}:${Date.now()}`) + '.signed_hmac';
        setTerminalHistory((prev) => [
          ...prev,
          `  [-] LAN unreachable: ${phoneIp}:8765 connection failed.`,
          '  [!] Generated one-time signed offline token:',
          `      ${token}`,
          '  [+] Type or scan this token into the phone stop screen.'
        ]);
        showToast('Wi-Fi down: Offline token generated!');
      }
    } else if (trimmed === 'focuslock token') {
      const token = 'eyJhbGciOiJIUzI1NiJ9.' + btoa(`stop:session-manual:${Date.now()}`) + '.signed_hmac';
      setTerminalHistory((prev) => [
        ...prev,
        '  [+] Generated signed offline stop token:',
        `      ${token}`
      ]);
      showToast('Offline token ready.');
    } else if (trimmed === 'help') {
      setTerminalHistory((prev) => [
        ...prev,
        'Available commands:',
        '  focuslock stop     - Stop active study session via LAN (or generate token)',
        '  focuslock pair     - Display pairing credentials',
        '  focuslock token    - Generate signed offline stop token',
        '  focuslock status   - Show system status & study streak',
        '  clear              - Clear terminal output'
      ]);
    } else {
      setTerminalHistory((prev) => [...prev, `  command not found: ${trimmed}. Type "help" for options.`]);
    }
  };

  const handleVerifyOfflineToken = () => {
    if (offlineTokenInput.trim().length > 10) {
      setActiveSession(null);
      setForegroundApp({ pkg: 'com.focuslock', name: 'FocusLock', isBlocked: false });
      setShowOfflineModal(false);
      setOfflineTokenInput('');
      setStudyStats((s) => ({ ...s, laptopStopsIssued: s.laptopStopsIssued + 1 }));
      setTerminalHistory((prev) => [
        ...prev,
        '[AUTH] Phone verified offline HMAC-SHA256 signature token. Session unlocked.'
      ]);
      showToast('Cryptographic signature verified. Phone unlocked!');
    } else {
      setOfflineTokenError('Invalid token signature or expired timestamp window.');
    }
  };

  const copyCode = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    setCopiedText(label);
    showToast(`Copied ${label} to clipboard!`);
    setTimeout(() => setCopiedText(null), 2000);
  };

  // YouTube Evaluation
  const evaluateYouTube = () => {
    if (ytIsShort) return { ok: false, reason: 'Shorts are completely blocked during study sessions.' };
    const lower = ytSearchTerm.toLowerCase();
    const blockedWords = ['gaming', 'prank', 'reaction', 'vlog', 'shorts', 'trailer', 'entertainment', 'meme'];
    for (const b of blockedWords) {
      if (lower.includes(b)) return { ok: false, reason: `Contains forbidden distraction keyword: "${b}"` };
    }
    const approvedChannels = ['MIT OpenCourseWare', '3Blue1Brown', 'Fireship', 'Khan Academy', 'Google Developers'];
    if (approvedChannels.includes(ytChannelSelected)) return { ok: true, reason: `From verified study channel "${ytChannelSelected}".` };
    const studyWords = ['machine learning', 'calculus', 'algorithm', 'kotlin', 'linux', 'deep learning', 'data structure', 'linear algebra'];
    for (const s of studyWords) {
      if (lower.includes(s)) return { ok: true, reason: `Approved academic study topic: "${s}".` };
    }
    return { ok: false, reason: 'Does not match approved academic topics or verified study channels.' };
  };

  const ytVerdict = evaluateYouTube();

  // App icon renderer
  const renderAppIcon = (icon: AppItem['iconName']) => {
    switch (icon) {
      case 'instagram':
        return <div className="w-7 h-7 rounded-lg bg-pink-500/10 text-pink-400 flex items-center justify-center font-bold text-xs">IG</div>;
      case 'youtube':
        return <div className="w-7 h-7 rounded-lg bg-red-500/10 text-red-400 flex items-center justify-center"><Youtube className="w-4 h-4" /></div>;
      case 'reddit':
        return <div className="w-7 h-7 rounded-lg bg-orange-500/10 text-orange-400 flex items-center justify-center font-bold text-xs">RD</div>;
      case 'twitter':
        return <div className="w-7 h-7 rounded-lg bg-sky-500/10 text-sky-400 flex items-center justify-center font-bold text-xs">X</div>;
      case 'game':
        return <div className="w-7 h-7 rounded-lg bg-purple-500/10 text-purple-400 flex items-center justify-center font-bold text-xs">🎮</div>;
      case 'pdf':
        return <div className="w-7 h-7 rounded-lg bg-amber-500/10 text-amber-400 flex items-center justify-center"><FileText className="w-4 h-4" /></div>;
      case 'docs':
        return <div className="w-7 h-7 rounded-lg bg-blue-500/10 text-blue-400 flex items-center justify-center"><BookOpen className="w-4 h-4" /></div>;
      case 'calc':
        return <div className="w-7 h-7 rounded-lg bg-teal-500/10 text-teal-400 flex items-center justify-center"><Calculator className="w-4 h-4" /></div>;
      case 'phone':
        return <div className="w-7 h-7 rounded-lg bg-emerald-500/10 text-emerald-400 flex items-center justify-center"><PhoneCall className="w-4 h-4" /></div>;
    }
  };

  return (
    <div className="min-h-screen bg-[#080C15] text-slate-100 flex flex-col font-sans selection:bg-rose-500 selection:text-white">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 bg-slate-900 border border-slate-700 text-white px-4 py-2.5 rounded-xl shadow-2xl flex items-center gap-2 text-xs font-semibold animate-in fade-in slide-in-from-bottom-3 duration-200">
          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* Top Bar (1-row, 3-zone, gap-8) */}
      <header className="border-b border-slate-800/80 bg-[#0B0F19]/95 backdrop-blur sticky top-0 z-40 px-6 py-3.5">
        <div className="max-w-7xl mx-auto flex items-center justify-between gap-8">
          {/* Zone 1: Wordmark */}
          <div className="flex items-center gap-3 shrink-0">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-rose-600 via-rose-500 to-amber-500 flex items-center justify-center text-white shadow-md shadow-rose-900/20">
              <Lock className="w-4 h-4 stroke-[2.5]" />
            </div>
            <span className="text-base font-bold tracking-tight text-white whitespace-nowrap shrink-0">
              FocusLock
            </span>
            <span className="text-xs text-slate-500 hidden sm:inline" aria-hidden="true">·</span>
            <span className="text-xs text-slate-400 font-medium hidden sm:inline">
              Personal Study Enforcement Engine
            </span>
          </div>

          {/* Zone 2: Navigation links */}
          <nav className="hidden lg:flex items-center gap-1.5 p-1 bg-slate-900/80 border border-slate-800 rounded-xl" aria-label="Main Navigation">
            <button
              onClick={() => setActiveTab('phone')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                activeTab === 'phone'
                  ? 'bg-rose-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Phone Simulator
            </button>
            <button
              onClick={() => setActiveTab('laptop')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                activeTab === 'laptop'
                  ? 'bg-rose-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Fedora Companion
            </button>
            <button
              onClick={() => setActiveTab('youtube')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                activeTab === 'youtube'
                  ? 'bg-rose-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Study YouTube
            </button>
            <button
              onClick={() => setActiveTab('bypass')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                activeTab === 'bypass'
                  ? 'bg-rose-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Bypass Defense Lab
            </button>
            <button
              onClick={() => setActiveTab('architecture')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                activeTab === 'architecture'
                  ? 'bg-rose-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Architecture & Security
            </button>
            <button
              onClick={() => setActiveTab('code')}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                activeTab === 'code'
                  ? 'bg-rose-600 text-white shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Source Code & APK
            </button>
          </nav>

          {/* Zone 3: Actions */}
          <div className="flex items-center gap-2.5 shrink-0">
            <button
              onClick={() => setShowSetupGuide(true)}
              className="px-3 py-1.5 text-xs font-medium text-slate-300 hover:text-white bg-slate-900 border border-slate-700/80 rounded-lg transition-all cursor-pointer flex items-center gap-1.5"
            >
              <HelpCircle className="w-3.5 h-3.5 text-sky-400" />
              Setup Guide
            </button>

            <a
              href="/focuslock-project.zip"
              download="focuslock-project.zip"
              className="px-3.5 py-1.5 text-xs font-bold text-slate-950 bg-gradient-to-r from-emerald-400 to-teal-400 hover:from-emerald-300 hover:to-teal-300 rounded-lg shadow-sm transition-all whitespace-nowrap shrink-0 flex items-center gap-1.5 cursor-pointer"
            >
              <Download className="w-3.5 h-3.5" />
              Download APK (.zip)
            </a>
          </div>
        </div>
      </header>

      {/* Sub-Header / Hardware Context Bar */}
      <div className="border-b border-slate-800/60 bg-[#0B0F19]/50 px-6 py-2">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between text-xs text-slate-400 gap-2">
          <div className="flex items-center gap-2">
            <span className="font-semibold text-slate-300">Device:</span>
            <span>Google Pixel 8 (Android 15)</span>
            <span aria-hidden="true">·</span>
            <span>Fedora 41 Companion</span>
            <span aria-hidden="true">·</span>
            <span className="text-emerald-400 font-mono">HMAC-SHA256 Active</span>
          </div>

          <div className="flex items-center gap-3">
            <span className="text-slate-400">Study Streak: <strong className="text-white">{studyStats.currentStreakDays} days</strong></span>
            <span aria-hidden="true">·</span>
            <span className="text-slate-400">Total Hours: <strong className="text-white">{studyStats.totalStudyHours}h</strong></span>
          </div>
        </div>
      </div>

      {/* Beginner-Friendly "How It Works" Walkthrough Banner */}
      {showHowItWorksBanner && (
        <div className="bg-slate-900/60 border-b border-slate-800/80 px-6 py-3.5">
          <div className="max-w-7xl mx-auto">
            <div className="flex items-start justify-between gap-4">
              <div className="flex items-center gap-2">
                <Lightbulb className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                <span className="text-xs font-bold text-slate-200">How FocusLock Works:</span>
              </div>
              <button
                onClick={() => setShowHowItWorksBanner(false)}
                className="text-slate-500 hover:text-slate-300 text-xs flex items-center gap-1 cursor-pointer"
              >
                <span>Dismiss</span>
                <X className="w-3.5 h-3.5" />
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mt-2 text-xs">
              <div className="p-2.5 rounded-xl bg-slate-950/60 border border-slate-800/80">
                <div className="font-bold text-white flex items-center gap-1.5">
                  <span className="w-4 h-4 rounded-full bg-slate-800 text-slate-300 flex items-center justify-center text-[10px]">1</span>
                  Select Apps to Lock
                </div>
                <p className="text-slate-400 text-[11px] mt-1">
                  Choose your duration and check distraction apps (Instagram, Games, YouTube).
                </p>
              </div>

              <div className="p-2.5 rounded-xl bg-slate-950/60 border border-slate-800/80">
                <div className="font-bold text-white flex items-center gap-1.5">
                  <span className="w-4 h-4 rounded-full bg-slate-800 text-slate-300 flex items-center justify-center text-[10px]">2</span>
                  Strict Hardware Lock
                </div>
                <p className="text-slate-400 text-[11px] mt-1">
                  Once started, the phone has NO exit, PIN, or cancel button. It cannot be bypassed.
                </p>
              </div>

              <div className="p-2.5 rounded-xl bg-slate-950/60 border border-slate-800/80">
                <div className="font-bold text-white flex items-center gap-1.5">
                  <span className="w-4 h-4 rounded-full bg-slate-800 text-slate-300 flex items-center justify-center text-[10px]">3</span>
                  Study Without Distraction
                </div>
                <p className="text-slate-400 text-[11px] mt-1">
                  Opening unapproved apps instantly displays a full-screen "Back to Study" overlay.
                </p>
              </div>

              <div className="p-2.5 rounded-xl bg-slate-950/60 border border-slate-800/80">
                <div className="font-bold text-white flex items-center gap-1.5">
                  <span className="w-4 h-4 rounded-full bg-slate-800 text-slate-300 flex items-center justify-center text-[10px]">4</span>
                  Stop from Laptop
                </div>
                <p className="text-slate-400 text-[11px] mt-1">
                  Only timer completion or a cryptographically signed stop command from your laptop can unlock.
                </p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Main Viewport */}
      <main className="flex-1 max-w-7xl mx-auto w-full p-6 lg:p-8">
        {/* TAB 1: PHONE SIMULATOR */}
        {activeTab === 'phone' && (
          <div className="space-y-6">
            {/* Quick Interactive Test Drive Bar */}
            <div className="bg-gradient-to-r from-slate-900 via-slate-900 to-slate-950 border border-slate-800 rounded-2xl p-4 flex flex-wrap items-center justify-between gap-3 shadow-lg">
              <div className="flex items-center gap-2.5">
                <div className="w-7 h-7 rounded-lg bg-sky-500/10 text-sky-400 flex items-center justify-center">
                  <Sparkles className="w-4 h-4" />
                </div>
                <div>
                  <span className="text-xs font-bold text-white block">One-Click Interactive Test Drive</span>
                  <span className="text-[11px] text-slate-400">Experience the lock, blocking intercept, and remote unlock in 3 quick taps:</span>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                {!activeSession ? (
                  <button
                    onClick={() => handleStartSession(1)}
                    className="px-3 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs flex items-center gap-1.5 shadow-md cursor-pointer transition-all"
                  >
                    <Play className="w-3.5 h-3.5 fill-white" />
                    1. Start 1-Min Test Lock
                  </button>
                ) : (
                  <>
                    <button
                      onClick={() => handleLaunchApp('com.instagram.android', 'Instagram')}
                      className="px-3 py-1.5 rounded-lg bg-red-950 border border-red-500/60 hover:bg-red-900/60 text-red-200 font-bold text-xs flex items-center gap-1.5 cursor-pointer transition-all"
                    >
                      <AlertOctagon className="w-3.5 h-3.5 text-red-400" />
                      2. Tap Instagram (See Intercept)
                    </button>
                    <button
                      onClick={() => executeTerminalCommand('focuslock stop')}
                      className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs flex items-center gap-1.5 shadow-md cursor-pointer transition-all"
                    >
                      <Laptop className="w-3.5 h-3.5" />
                      3. Send Stop from Laptop
                    </button>
                  </>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
              {/* Phone Chassis */}
              <div className="lg:col-span-6 flex justify-center">
                <div className="w-full max-w-[390px] rounded-[44px] p-3.5 bg-slate-900 border-4 border-slate-800 shadow-2xl shadow-black/80 relative">
                  {/* Screen frame */}
                  <div className="bg-[#090D16] rounded-[36px] overflow-hidden min-h-[640px] flex flex-col justify-between p-5 border border-slate-800/80 relative">
                    {/* Status Bar */}
                    <div className="flex items-center justify-between text-xs text-slate-400 mb-3 font-mono">
                      <div className="flex items-center gap-1.5 font-bold text-white">
                        <span>22:35</span>
                      </div>
                      {/* Punch hole */}
                      <div className="w-4 h-4 rounded-full bg-black border border-slate-800"></div>
                      <div className="flex items-center gap-2 text-[10px]">
                        <span>5G</span>
                        <span>100%</span>
                      </div>
                    </div>

                    {/* Active Notification */}
                    {activeSession && (
                      <div className="mb-3 p-3 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-between text-xs shadow-md">
                        <div className="flex items-center gap-2 truncate">
                          <Lock className="w-4 h-4 text-rose-500 shrink-0" />
                          <div className="truncate">
                            <div className="font-bold text-white leading-tight">FocusLock Active</div>
                            <div className="text-[10px] text-slate-400 truncate">
                              {activeSession.mode === 'PARTIAL_LOCK' ? 'Partial mode: Focus timer' : 'Strict lock: Distractions blocked'}
                            </div>
                          </div>
                        </div>
                        <span className="font-mono text-rose-400 font-bold ml-2">
                          {Math.floor(activeSession.remainingSeconds / 60)}:
                          {(activeSession.remainingSeconds % 60).toString().padStart(2, '0')}
                        </span>
                      </div>
                    )}

                    {/* SCREEN BODY */}
                    {activeSession && foregroundApp.isBlocked ? (
                      /* FULL-SCREEN BLOCKING OVERLAY */
                      <div className="my-auto py-8 px-5 bg-red-950/90 border-2 border-red-500 rounded-2xl text-center space-y-4 shadow-2xl backdrop-blur animate-in zoom-in-95 duration-150">
                        <div className="w-14 h-14 rounded-full bg-red-500/20 border border-red-500/40 text-red-400 flex items-center justify-center mx-auto">
                          <AlertOctagon className="w-8 h-8 stroke-[2.5]" />
                        </div>
                        <div>
                          <span className="text-[10px] font-mono font-bold tracking-wider text-red-300 uppercase block">
                            SYSTEM OVERLAY INTERCEPT
                          </span>
                          <h2 className="text-xl font-extrabold text-white mt-1">Back to Study</h2>
                          <p className="text-xs text-red-200/80 mt-1">
                            FocusLock detected you tried opening:
                          </p>
                          <code className="text-xs font-mono text-white bg-red-900/60 px-2.5 py-1 rounded-md mt-2 inline-block border border-red-500/40 truncate max-w-full">
                            {foregroundApp.pkg}
                          </code>
                        </div>

                        <div className="p-3 bg-black/50 rounded-xl border border-red-500/30 text-xs text-slate-300">
                          Remaining study time: <strong className="text-white">{Math.ceil(activeSession.remainingSeconds / 60)}m</strong>.
                          <div className="text-[10px] text-red-300/80 mt-1">
                            Remember: Only timer completion or a command from your Fedora companion can unlock.
                          </div>
                        </div>

                        <div className="grid grid-cols-2 gap-2 pt-1">
                          <button
                            onClick={() => setForegroundApp({ pkg: 'com.focuslock', name: 'FocusLock', isBlocked: false })}
                            className="py-2.5 rounded-xl bg-sky-500 hover:bg-sky-400 text-slate-950 font-bold text-xs shadow-md transition-all cursor-pointer"
                          >
                            Return to Study
                          </button>
                          <button
                            onClick={() => setShowOfflineModal(true)}
                            className="py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-bold text-xs border border-slate-700 transition-all cursor-pointer"
                          >
                            Offline Token
                          </button>
                        </div>
                      </div>
                    ) : activeSession ? (
                      /* ACTIVE SESSION STUDY VIEW */
                      <div className="my-auto text-center space-y-5">
                        {/* Timer Circular Display */}
                        <div className="relative w-44 h-44 mx-auto flex items-center justify-center">
                          <svg className="w-full h-full transform -rotate-90">
                            <circle cx="88" cy="88" r="74" stroke="currentColor" strokeWidth="8" className="text-slate-800" fill="transparent" />
                            <circle
                              cx="88"
                              cy="88"
                              r="74"
                              stroke="currentColor"
                              strokeWidth="8"
                              strokeDasharray={2 * Math.PI * 74}
                              strokeDashoffset={2 * Math.PI * 74 * (1 - activeSession.remainingSeconds / activeSession.initialSeconds)}
                              strokeLinecap="round"
                              className="text-rose-500 transition-all duration-1000"
                              fill="transparent"
                            />
                          </svg>
                          <div className="absolute flex flex-col items-center">
                            <span className="font-mono text-3xl font-extrabold text-white tracking-tight">
                              {Math.floor(activeSession.remainingSeconds / 60)}:
                              {(activeSession.remainingSeconds % 60).toString().padStart(2, '0')}
                            </span>
                            <span className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold mt-0.5">
                              Monotonic Clock
                            </span>
                          </div>
                        </div>

                        <div>
                          <h3 className="text-base font-bold text-white">{foregroundApp.name}</h3>
                          <p className="text-xs text-slate-400 font-mono truncate">{foregroundApp.pkg}</p>
                        </div>

                        <div className="p-3 bg-slate-900 border border-slate-800 rounded-xl text-xs text-slate-300 max-w-xs mx-auto">
                          Study tools (PDF reader, Notes, Calculator) open smoothly. Distraction apps are completely locked.
                        </div>
                      </div>
                    ) : (
                      /* PRE-SESSION CONFIGURATOR */
                      <div className="space-y-4 my-auto">
                        <div>
                          <h2 className="text-lg font-bold text-white">FocusLock Setup</h2>
                          <p className="text-xs text-slate-400">Customize your study block before engaging the lock</p>
                        </div>

                        {/* Mode Segmented Controls */}
                        <div className="space-y-1.5">
                          <div className="flex items-center justify-between text-xs font-semibold text-slate-300">
                            <span>Lockdown Mode</span>
                            <span className="text-[11px] text-slate-400 font-normal">
                              {lockMode === 'SELECTIVE_BLACKLIST' ? 'Block checked apps' : lockMode === 'STRICT_WHITELIST' ? 'Block all except checked' : 'Focus timer HUD'}
                            </span>
                          </div>
                          <div className="grid grid-cols-3 gap-1 p-1 bg-slate-900 border border-slate-800 rounded-xl">
                            {[
                              { id: 'SELECTIVE_BLACKLIST', label: 'Selective' },
                              { id: 'STRICT_WHITELIST', label: 'Whitelist' },
                              { id: 'PARTIAL_LOCK', label: 'Partial' }
                            ].map((m) => (
                              <button
                                key={m.id}
                                onClick={() => setLockMode(m.id as any)}
                                className={`py-1.5 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
                                  lockMode === m.id
                                    ? 'bg-rose-600 text-white shadow-sm'
                                    : 'text-slate-400 hover:text-slate-200'
                                }`}
                              >
                                {m.label}
                              </button>
                            ))}
                          </div>
                        </div>

                        {/* Duration Preset Selector */}
                        <div className="space-y-1.5">
                          <label className="text-xs font-semibold text-slate-300 block">Session Duration</label>
                          <div className="grid grid-cols-5 gap-1.5">
                            {[25, 45, 50, 60, 90].map((mins) => (
                              <button
                                key={mins}
                                onClick={() => setDurationMinutes(mins)}
                                className={`py-2 rounded-lg text-xs font-bold border transition-all cursor-pointer ${
                                  durationMinutes === mins
                                    ? 'bg-rose-600 text-white border-rose-500 shadow-sm'
                                    : 'bg-slate-900 text-slate-400 border-slate-800 hover:border-slate-700'
                                }`}
                              >
                                {mins}m
                              </button>
                            ))}
                          </div>
                        </div>

                        {/* App Checkbox Selector */}
                        <div className="space-y-1.5">
                          <div className="flex items-center justify-between text-xs font-semibold text-slate-300">
                            <span>Select Apps to Lock</span>
                            <span className="text-[11px] text-rose-400 font-mono">
                              {apps.filter((a) => a.isLocked).length} selected
                            </span>
                          </div>
                          <div className="space-y-1.5 max-h-44 overflow-y-auto pr-1">
                            {apps.map((app) => (
                              <div
                                key={app.pkg}
                                className={`p-2 rounded-xl border flex items-center justify-between text-xs transition-all ${
                                  app.fixed
                                    ? 'bg-emerald-950/20 border-emerald-500/30'
                                    : app.isLocked
                                    ? 'bg-rose-950/20 border-rose-500/30 text-rose-200'
                                    : 'bg-slate-900 border-slate-800/80 text-slate-400'
                                }`}
                              >
                                <div className="flex items-center gap-2 truncate mr-2">
                                  {renderAppIcon(app.iconName)}
                                  <div className="truncate">
                                    <span className="font-semibold text-slate-200 block truncate">{app.name}</span>
                                    <span className="text-[10px] text-slate-500 font-mono truncate block">{app.pkg}</span>
                                  </div>
                                </div>
                                <input
                                  type="checkbox"
                                  disabled={app.fixed}
                                  checked={app.isLocked}
                                  onChange={(e) => {
                                    if (!app.fixed) {
                                      setApps(apps.map((a) => (a.pkg === app.pkg ? { ...a, isLocked: e.target.checked } : a)));
                                    }
                                  }}
                                  className="w-4 h-4 rounded text-rose-600 focus:ring-0 bg-slate-800 border-slate-700 cursor-pointer disabled:cursor-not-allowed shrink-0"
                                />
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    )}

                    {/* SCREEN FOOTER */}
                    <div className="pt-3 border-t border-slate-800/80">
                      {!activeSession ? (
                        <button
                          onClick={() => handleStartSession()}
                          className="w-full py-3 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-lg shadow-rose-950/40 transition-all cursor-pointer"
                        >
                          <Play className="w-4 h-4 fill-white" />
                          Engage Strict Lock ({durationMinutes}m)
                        </button>
                      ) : (
                        <div className="grid grid-cols-2 gap-2">
                          <button
                            onClick={() => {
                              setActiveSession((p) => p ? { ...p, reboots: p.reboots + 1 } : null);
                              setForegroundApp({ pkg: 'com.focuslock', name: 'FocusLock', isBlocked: false });
                              showToast('Phone reboot simulated! Monotonic clock safely reconciled.');
                            }}
                            className="py-2 px-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs flex items-center justify-center gap-1.5 border border-slate-700 transition-all cursor-pointer"
                          >
                            <RotateCcw className="w-3.5 h-3.5 text-sky-400" />
                            Simulate Reboot
                          </button>
                          <button
                            onClick={() => {
                              setActiveSession((p) => p ? { ...p, remainingSeconds: p.remainingSeconds + 15 * 60 } : null);
                              showToast('Extended session by +15 minutes.');
                            }}
                            className="py-2 px-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs flex items-center justify-center gap-1.5 border border-slate-700 transition-all cursor-pointer"
                          >
                            <Lock className="w-3.5 h-3.5 text-emerald-400" />
                            Extend (+15m)
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </div>

              {/* Right Column: Interactive App Switcher & Testing Pad */}
              <div className="lg:col-span-6 space-y-6">
                {/* App Switcher Tray */}
                <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-4">
                  <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                    <div>
                      <h3 className="font-bold text-sm text-white">Interactive App Switcher</h3>
                      <p className="text-xs text-slate-400">Tap any application below to simulate opening it on the Android phone</p>
                    </div>
                    <span className="text-[11px] font-mono text-slate-500">Live OS Simulator</span>
                  </div>

                  {/* Filter and presets */}
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="relative flex-1 min-w-[200px]">
                      <Search className="w-3.5 h-3.5 absolute left-3 top-2.5 text-slate-500" />
                      <input
                        type="text"
                        value={appSearchQuery}
                        onChange={(e) => setAppSearchQuery(e.target.value)}
                        placeholder="Search apps by name or package..."
                        className="w-full pl-8 pr-3 py-1.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-white focus:outline-none focus:border-rose-500"
                      />
                    </div>
                    <div className="flex gap-1">
                      {(['All', 'Distraction', 'Study Tools'] as const).map((cat) => (
                        <button
                          key={cat}
                          onClick={() => setSelectedCategory(cat)}
                          className={`px-2.5 py-1 text-xs rounded-md font-medium transition-colors cursor-pointer ${
                            selectedCategory === cat
                              ? 'bg-slate-800 text-white'
                              : 'text-slate-400 hover:text-white'
                          }`}
                        >
                          {cat}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Quick Select Presets for Beginner Friendliness */}
                  <div className="flex flex-wrap items-center gap-1.5 pt-1 text-[11px]">
                    <span className="text-slate-500 font-medium">Quick Presets:</span>
                    <button
                      onClick={() => {
                        setApps(apps.map(a => a.fixed ? a : { ...a, isLocked: a.category === 'Distraction' }));
                        showToast('Applied Distractions Lock preset');
                      }}
                      className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 hover:text-white cursor-pointer"
                    >
                      Lock All Distractions
                    </button>
                    <button
                      onClick={() => {
                        setApps(apps.map(a => a.fixed ? a : { ...a, isLocked: true }));
                        showToast('Applied Full Lockdown preset');
                      }}
                      className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 hover:text-white cursor-pointer"
                    >
                      Lock Everything
                    </button>
                    <button
                      onClick={() => {
                        setApps(apps.map(a => a.fixed ? a : { ...a, isLocked: false }));
                        showToast('Cleared app selections');
                      }}
                      className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 hover:text-white cursor-pointer"
                    >
                      Unlock All
                    </button>
                  </div>

                  <div className="space-y-1.5 max-h-64 overflow-y-auto pr-1">
                    {filteredApps.map((app) => (
                      <button
                        key={app.pkg}
                        onClick={() => handleLaunchApp(app.pkg, app.name)}
                        className={`w-full p-2.5 rounded-xl border text-left flex items-center justify-between transition-all cursor-pointer ${
                          foregroundApp.pkg === app.pkg && foregroundApp.isBlocked
                            ? 'bg-red-950/50 border-red-500 text-red-200'
                            : foregroundApp.pkg === app.pkg && !foregroundApp.isBlocked
                            ? 'bg-emerald-950/40 border-emerald-500/50 text-emerald-200'
                            : 'bg-slate-950/60 border-slate-800/80 text-slate-300 hover:bg-slate-800/60'
                        }`}
                      >
                        <div className="flex items-center gap-2.5 truncate mr-2">
                          {renderAppIcon(app.iconName)}
                          <div className="truncate">
                            <div className="text-xs font-semibold">{app.name}</div>
                            <div className="text-[10px] text-slate-500 font-mono truncate">{app.pkg}</div>
                          </div>
                        </div>
                        <span
                          className={`text-[10px] px-2 py-0.5 rounded font-mono font-bold shrink-0 ${
                            app.fixed
                              ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                              : app.isLocked
                              ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                              : 'bg-slate-800 text-slate-400'
                          }`}
                        >
                          {app.fixed ? 'SAFETY' : app.isLocked ? 'LOCKED' : 'OPEN'}
                        </span>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Anti-Tamper Attack Simulator */}
                <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Shield className="w-4 h-4 text-amber-400" />
                      <h3 className="font-bold text-sm text-white">Clock Tamper Penetration Test</h3>
                    </div>
                    <span className="text-[11px] font-mono text-slate-400">Monotonic Defence</span>
                  </div>
                  <p className="text-xs text-slate-400">
                    See how FocusLock prevents users from bypassing sessions by advancing the phone calendar:
                  </p>

                  <div className="grid grid-cols-2 gap-2">
                    <button
                      disabled={!activeSession}
                      onClick={() => {
                        if (!activeSession) return;
                        setActiveSession((p) => p ? { ...p, reboots: p.reboots + 1, clockTampersDefeated: p.clockTampersDefeated + 1 } : null);
                        setForegroundApp({ pkg: 'com.focuslock', name: 'FocusLock', isBlocked: false });
                        showToast('Tamper rejected: Anomaly detected! Zero downtime credit granted.');
                      }}
                      className={`p-2.5 rounded-xl border text-left text-xs font-semibold transition-all ${
                        activeSession
                          ? 'bg-amber-950/20 border-amber-500/30 text-amber-200 hover:bg-amber-900/30 cursor-pointer'
                          : 'bg-slate-950 border-slate-800 text-slate-600 cursor-not-allowed'
                      }`}
                    >
                      <Calendar className="w-3.5 h-3.5 text-amber-400 mb-1" />
                      Date +30 Days & Reboot
                      <span className="text-[10px] text-slate-400 block font-normal mt-0.5">Attempt to skip forward</span>
                    </button>

                    <button
                      disabled={!activeSession}
                      onClick={() => {
                        if (!activeSession) return;
                        setActiveSession((p) => p ? { ...p, reboots: p.reboots + 1, clockTampersDefeated: p.clockTampersDefeated + 1 } : null);
                        setForegroundApp({ pkg: 'com.focuslock', name: 'FocusLock', isBlocked: false });
                        showToast('Rollback rejected: Clock shift ignored.');
                      }}
                      className={`p-2.5 rounded-xl border text-left text-xs font-semibold transition-all ${
                        activeSession
                          ? 'bg-amber-950/20 border-amber-500/30 text-amber-200 hover:bg-amber-900/30 cursor-pointer'
                          : 'bg-slate-950 border-slate-800 text-slate-600 cursor-not-allowed'
                      }`}
                    >
                      <RotateCcw className="w-3.5 h-3.5 text-amber-400 mb-1" />
                      Date -1 Year & Reboot
                      <span className="text-[10px] text-slate-400 block font-normal mt-0.5">Attempt to roll backward</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: FEDORA COMPANION & CLI */}
        {activeTab === 'laptop' && (
          <div className="space-y-6">
            {/* Companion View Switcher */}
            <div className="flex items-center justify-between p-3 rounded-2xl bg-slate-900/80 border border-slate-800">
              <div className="flex items-center gap-3">
                <Laptop className="w-5 h-5 text-sky-400" />
                <div>
                  <h3 className="font-bold text-sm text-white">Fedora Companion Hub</h3>
                  <p className="text-xs text-slate-400">Control phone lockdown over local Wi-Fi or offline HMAC token</p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <div className="p-1 bg-slate-950 border border-slate-800 rounded-xl flex items-center gap-1">
                  <button
                    onClick={() => setCompanionView('terminal')}
                    className={`px-3 py-1 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
                      companionView === 'terminal' ? 'bg-sky-600 text-white' : 'text-slate-400 hover:text-white'
                    }`}
                  >
                    Terminal CLI
                  </button>
                  <button
                    onClick={() => setCompanionView('dashboard')}
                    className={`px-3 py-1 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
                      companionView === 'dashboard' ? 'bg-sky-600 text-white' : 'text-slate-400 hover:text-white'
                    }`}
                  >
                    Flask Web Dashboard
                  </button>
                </div>

                <button
                  onClick={() => {
                    setSimulatedNetworkOnline(!simulatedNetworkOnline);
                    showToast(simulatedNetworkOnline ? 'Simulating Wi-Fi disconnected (Offline Token Mode)' : 'Wi-Fi reconnected');
                  }}
                  className={`px-3 py-1.5 rounded-xl border text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
                    simulatedNetworkOnline
                      ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-300'
                      : 'bg-amber-950/40 border-amber-500/40 text-amber-300'
                  }`}
                >
                  {simulatedNetworkOnline ? <Wifi className="w-3.5 h-3.5" /> : <WifiOff className="w-3.5 h-3.5" />}
                  {simulatedNetworkOnline ? 'LAN: Online' : 'LAN: Offline'}
                </button>
              </div>
            </div>

            {companionView === 'terminal' ? (
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
                {/* Control Panel */}
                <div className="lg:col-span-5 space-y-6">
                  <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-4">
                    <span className="text-xs font-bold text-slate-300 block">Phone Connection Settings</span>
                    <div className="space-y-3">
                      <div>
                        <label className="text-xs font-semibold text-slate-300 block mb-1">Target Phone IP Address</label>
                        <input
                          type="text"
                          value={phoneIp}
                          onChange={(e) => setPhoneIp(e.target.value)}
                          className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 font-mono text-xs text-white focus:outline-none focus:border-sky-500"
                        />
                      </div>

                      <div className="p-3 bg-slate-950 rounded-xl border border-slate-800 space-y-1">
                        <span className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold block">Pairing Secret (HMAC-SHA256)</span>
                        <div className="font-mono text-[11px] text-sky-400 truncate">{sharedSecret}</div>
                      </div>
                    </div>

                    <div className="pt-2 border-t border-slate-800 space-y-2">
                      <span className="text-xs font-bold text-slate-300 block">Quick Action Triggers</span>
                      <div className="grid grid-cols-2 gap-2">
                        <button
                          onClick={() => executeTerminalCommand('focuslock stop')}
                          className="py-2.5 px-3 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs flex items-center justify-center gap-1.5 shadow-md transition-all cursor-pointer"
                        >
                          <LockOpen className="w-3.5 h-3.5" />
                          Stop Phone Lock
                        </button>
                        <button
                          onClick={() => executeTerminalCommand('focuslock token')}
                          className="py-2.5 px-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs flex items-center justify-center gap-1.5 border border-slate-700 transition-all cursor-pointer"
                        >
                          <Key className="w-3.5 h-3.5 text-amber-400" />
                          Offline Token
                        </button>
                      </div>
                    </div>
                  </div>

                  {/* Session History */}
                  <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-3">
                    <span className="text-xs font-bold text-slate-300 block">Session History</span>
                    <div className="space-y-1.5">
                      {recentSessions.map((s) => (
                        <div key={s.id} className="p-2.5 rounded-lg bg-slate-950 border border-slate-800/80 flex items-center justify-between text-xs">
                          <div>
                            <span className="font-semibold text-white">{s.id}</span>
                            <span className="text-slate-500 ml-1.5 font-mono text-[11px]">({s.duration})</span>
                          </div>
                          <div className="flex items-center gap-2 text-[11px] text-slate-400">
                            <span>{s.exit}</span>
                            <span aria-hidden="true">·</span>
                            <span className="font-mono">{s.date}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>

                {/* Terminal Emulator */}
                <div className="lg:col-span-7 bg-slate-950 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl flex flex-col min-h-[520px]">
                  <div className="p-3 bg-slate-900 border-b border-slate-800 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <div className="flex gap-1.5">
                        <div className="w-2.5 h-2.5 rounded-full bg-red-500"></div>
                        <div className="w-2.5 h-2.5 rounded-full bg-amber-500"></div>
                        <div className="w-2.5 h-2.5 rounded-full bg-emerald-500"></div>
                      </div>
                      <span className="text-xs font-mono text-slate-400 ml-2">punith@fedora:~ (focuslock)</span>
                    </div>
                    <div className="flex items-center gap-2 text-xs">
                      <button
                        onClick={() => executeTerminalCommand('focuslock status')}
                        className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 hover:text-white font-mono text-[11px] cursor-pointer"
                      >
                        status
                      </button>
                      <button
                        onClick={() => executeTerminalCommand('focuslock stop')}
                        className="px-2 py-0.5 rounded bg-rose-950 text-rose-300 hover:text-rose-200 font-mono text-[11px] cursor-pointer"
                      >
                        stop
                      </button>
                      <button
                        onClick={() => executeTerminalCommand('focuslock token')}
                        className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 hover:text-white font-mono text-[11px] cursor-pointer"
                      >
                        token
                      </button>
                    </div>
                  </div>

                  {/* Terminal Logs */}
                  <div className="p-4 flex-1 font-mono text-xs text-slate-300 space-y-1.5 overflow-y-auto max-h-[420px] leading-relaxed">
                    {terminalHistory.map((line, idx) => (
                      <div
                        key={idx}
                        className={
                          line.startsWith('punith@fedora')
                            ? 'text-emerald-400 font-semibold'
                            : line.includes('[SUCCESS]') || line.includes('[+]')
                            ? 'text-teal-300'
                            : line.includes('[-]') || line.includes('Error')
                            ? 'text-rose-400'
                            : line.includes('[!]')
                            ? 'text-amber-300'
                            : 'text-slate-300'
                        }
                      >
                        {line}
                      </div>
                    ))}
                  </div>

                  {/* Terminal Input */}
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      executeTerminalCommand(terminalInput);
                      setTerminalInput('');
                    }}
                    className="p-3 bg-slate-900 border-t border-slate-800 flex items-center gap-2"
                  >
                    <span className="text-emerald-400 font-mono text-xs font-bold">punith@fedora:~$</span>
                    <input
                      type="text"
                      value={terminalInput}
                      onChange={(e) => setTerminalInput(e.target.value)}
                      placeholder="type focuslock stop, status, token, pair..."
                      className="flex-1 bg-transparent font-mono text-xs text-white focus:outline-none"
                    />
                    <button type="submit" className="text-slate-400 hover:text-white p-1 cursor-pointer">
                      <Send className="w-3.5 h-3.5" />
                    </button>
                  </form>
                </div>
              </div>
            ) : (
              /* Flask Web Dashboard View (http://127.0.0.1:5000) */
              <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-6 space-y-6">
                <div className="flex items-center justify-between pb-4 border-b border-slate-800">
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-xl bg-sky-500/10 text-sky-400 flex items-center justify-center">
                      <MonitorCheck className="w-5 h-5" />
                    </div>
                    <div>
                      <h4 className="font-bold text-white text-sm">Flask Companion Dashboard (http://127.0.0.1:5000)</h4>
                      <p className="text-xs text-slate-400">Local web view powered by companion/server.py and SQLite</p>
                    </div>
                  </div>
                  <span className="px-2.5 py-1 rounded-full bg-emerald-500/20 text-emerald-400 text-xs font-semibold border border-emerald-500/30">
                    Flask 3.0 Running
                  </span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-1">
                    <span className="text-xs text-slate-400">Total Study Time</span>
                    <div className="text-2xl font-extrabold text-white font-mono">{studyStats.totalStudyHours}h</div>
                    <span className="text-[11px] text-emerald-400">+2.5h from yesterday</span>
                  </div>
                  <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-1">
                    <span className="text-xs text-slate-400">Study Streak</span>
                    <div className="text-2xl font-extrabold text-white font-mono">{studyStats.currentStreakDays} days</div>
                    <span className="text-[11px] text-sky-400">Daily goal: 4 hours</span>
                  </div>
                  <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-1">
                    <span className="text-xs text-slate-400">Completed Sessions</span>
                    <div className="text-2xl font-extrabold text-white font-mono">{studyStats.completedSessions}</div>
                    <span className="text-[11px] text-slate-400">{studyStats.laptopStopsIssued} stopped from laptop</span>
                  </div>
                </div>

                {/* Dashboard Remote Stop Button */}
                <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-between">
                  <div>
                    <span className="font-bold text-sm text-white block">Remote Stop Session</span>
                    <span className="text-xs text-slate-400">Dispatches signed HMAC payload to phone's NanoHTTPD listener</span>
                  </div>
                  <button
                    onClick={() => executeTerminalCommand('focuslock stop')}
                    className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs flex items-center gap-1.5 shadow-md cursor-pointer transition-all"
                  >
                    <LockOpen className="w-4 h-4" />
                    Stop Phone Session
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {/* TAB 3: STUDY YOUTUBE */}
        {activeTab === 'youtube' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            <div className="lg:col-span-6 space-y-5">
              <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-4">
                <div className="flex items-center gap-2.5 pb-3 border-b border-slate-800">
                  <Youtube className="w-5 h-5 text-red-500" />
                  <div>
                    <h3 className="font-bold text-sm text-white">YouTube Study Content Filter</h3>
                    <p className="text-xs text-slate-400">In-app embedded player policy evaluation</p>
                  </div>
                </div>

                <div className="space-y-3">
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">Simulated Video Title</label>
                    <input
                      type="text"
                      value={ytSearchTerm}
                      onChange={(e) => setYtSearchTerm(e.target.value)}
                      className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white focus:outline-none focus:border-rose-500"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="text-xs font-semibold text-slate-300 block mb-1">Channel</label>
                      <select
                        value={ytChannelSelected}
                        onChange={(e) => setYtChannelSelected(e.target.value)}
                        className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white focus:outline-none focus:border-rose-500"
                      >
                        <option value="MIT OpenCourseWare">MIT OpenCourseWare</option>
                        <option value="3Blue1Brown">3Blue1Brown</option>
                        <option value="Fireship">Fireship</option>
                        <option value="Khan Academy">Khan Academy</option>
                        <option value="Google Developers">Google Developers</option>
                        <option value="Random Distraction Channel">Random Distraction Channel</option>
                      </select>
                    </div>

                    <div>
                      <label className="text-xs font-semibold text-slate-300 block mb-1">Format</label>
                      <label className="flex items-center gap-1.5 text-xs text-slate-300 cursor-pointer pt-2">
                        <input
                          type="checkbox"
                          checked={ytIsShort}
                          onChange={(e) => setYtIsShort(e.target.checked)}
                          className="w-4 h-4 rounded text-red-600 focus:ring-0 bg-slate-800 border-slate-700"
                        />
                        Is YouTube Short
                      </label>
                    </div>
                  </div>
                </div>

                {/* Verdict Display */}
                <div
                  className={`p-4 rounded-xl border space-y-1.5 ${
                    ytVerdict.ok
                      ? 'bg-emerald-950/30 border-emerald-500/40 text-emerald-200'
                      : 'bg-red-950/30 border-red-500/40 text-red-200'
                  }`}
                >
                  <div className="flex items-center gap-2 font-bold text-xs">
                    {ytVerdict.ok ? <CheckCircle2 className="w-4 h-4 text-emerald-400" /> : <XCircle className="w-4 h-4 text-red-400" />}
                    <span>{ytVerdict.ok ? 'VIDEO APPROVED FOR STUDY' : 'VIDEO BLOCKED BY FILTER'}</span>
                  </div>
                  <div className="text-xs text-slate-300">{ytVerdict.reason}</div>
                </div>

                {/* Quick Presets */}
                <div className="space-y-1.5 pt-1">
                  <span className="text-[11px] font-semibold text-slate-400 block">Quick Test Cases:</span>
                  <div className="flex flex-wrap gap-1.5">
                    <button
                      onClick={() => {
                        setYtSearchTerm('MIT OCW Linear Algebra Lecture 01');
                        setYtChannelSelected('MIT OpenCourseWare');
                        setYtIsShort(false);
                      }}
                      className="px-2.5 py-1 rounded-md bg-slate-800 text-xs text-slate-300 hover:text-white cursor-pointer"
                    >
                      MIT Linear Algebra (Allow)
                    </button>
                    <button
                      onClick={() => {
                        setYtSearchTerm('Minecraft Stream Highlights #49');
                        setYtChannelSelected('Random Distraction Channel');
                        setYtIsShort(false);
                      }}
                      className="px-2.5 py-1 rounded-md bg-slate-800 text-xs text-slate-300 hover:text-white cursor-pointer"
                    >
                      Gaming Stream (Block)
                    </button>
                    <button
                      onClick={() => {
                        setYtSearchTerm('Quick Algorithm Trick');
                        setYtChannelSelected('Fireship');
                        setYtIsShort(true);
                      }}
                      className="px-2.5 py-1 rounded-md bg-slate-800 text-xs text-slate-300 hover:text-white cursor-pointer"
                    >
                      Fireship Short (Block)
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* Injected CSS & Shield Details */}
            <div className="lg:col-span-6 space-y-4">
              <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-3">
                <h4 className="font-bold text-xs text-white uppercase tracking-wider">In-App Distraction Stripper (CSS/JS)</h4>
                <p className="text-xs text-slate-400">
                  When you open study YouTube inside FocusLock, the official YouTube app is blocked, and the in-app player injects stylesheet rules:
                </p>
                <div className="space-y-2 text-xs">
                  <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 flex items-center justify-between">
                    <div>
                      <span className="text-slate-200 font-semibold block">Shorts Shelf & Reels</span>
                      <span className="text-[10px] text-slate-500 font-mono">ytd-reel-shelf-renderer, #shorts-container</span>
                    </div>
                    <span className="text-rose-400 font-mono text-[11px] font-bold">HIDDEN</span>
                  </div>
                  <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 flex items-center justify-between">
                    <div>
                      <span className="text-slate-200 font-semibold block">Comments Section</span>
                      <span className="text-[10px] text-slate-500 font-mono">#comments, ytd-comments</span>
                    </div>
                    <span className="text-rose-400 font-mono text-[11px] font-bold">HIDDEN</span>
                  </div>
                  <div className="p-3 rounded-lg bg-slate-950 border border-slate-800 flex items-center justify-between">
                    <div>
                      <span className="text-slate-200 font-semibold block">Algorithmic Sidebar</span>
                      <span className="text-[10px] text-slate-500 font-mono">#related, ytd-watch-next-secondary-results</span>
                    </div>
                    <span className="text-rose-400 font-mono text-[11px] font-bold">HIDDEN</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: BYPASS MATRIX & TESTS */}
        {activeTab === 'bypass' && (
          <div className="max-w-4xl mx-auto space-y-6">
            <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-6 space-y-5">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 flex items-center justify-center">
                  <Shield className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-bold text-base text-white">Security & Bypass Defense Matrix</h3>
                  <p className="text-xs text-slate-400">Analysis of real-world cheating attempts and platform mitigations</p>
                </div>
              </div>

              <div className="space-y-3 text-xs">
                {[
                  {
                    attempt: '1. Force Stop or Clear Data in Android Settings',
                    defense: 'Blocked by AccessibilityService inspection. Opening FocusLock in Settings immediately kicks user back to Home and displays the overlay. If Device Owner is active, uninstall is blocked at kernel policy level.',
                    status: 'DEFEATED'
                  },
                  {
                    attempt: '2. Setting Date Forward by 1 Month to Skip Timer',
                    defense: 'Defeated by AbsoluteSession monotonic hardware clock (SystemClock.elapsedRealtime). Even across a reboot, if wall delta > 10m, zero offline downtime credit is granted.',
                    status: 'DEFEATED'
                  },
                  {
                    attempt: '3. Rebooting Phone into Safe Mode',
                    defense: 'Device Owner API enforces UserManager.DISALLOW_SAFE_BOOT, which prevents 3rd-party services from being disabled during reboot.',
                    status: 'DEFEATED'
                  },
                  {
                    attempt: '4. Pulling Down Quick Settings to Disable Wi-Fi / Airplane Mode',
                    defense: 'Offline fallback HMAC token can be generated on the laptop CLI without Wi-Fi and typed/scanned on the phone.',
                    status: 'MITIGATED'
                  },
                  {
                    attempt: '5. Emergency Phone Call Needed',
                    defense: 'Phone dialer (com.android.dialer, telecom, phone) is permanently exempt from blocking. Safety is never compromised.',
                    status: 'GUARANTEED'
                  }
                ].map((item, idx) => (
                  <div key={idx} className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-slate-200">{item.attempt}</span>
                      <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                        {item.status}
                      </span>
                    </div>
                    <p className="text-slate-400 text-[11px] leading-relaxed">{item.defense}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* TAB 5: ARCHITECTURE & SECURITY */}
        {activeTab === 'architecture' && (
          <div className="max-w-4xl mx-auto space-y-6">
            <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-6 space-y-6">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-sky-500/10 border border-sky-500/20 text-sky-400 flex items-center justify-center">
                  <Cpu className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-bold text-base text-white">FocusLock Security & System Architecture</h3>
                  <p className="text-xs text-slate-400">Technical deep dive designed for an AIML student on Fedora Linux</p>
                </div>
              </div>

              {/* Architecture Diagram Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2">
                  <span className="font-bold text-sky-400 block">1. Monotonic Time Reconciliation</span>
                  <p className="text-slate-300 text-[11px] leading-relaxed">
                    Android's <code className="text-sky-300">SystemClock.elapsedRealtime()</code> measures CPU ticks including sleep, making it immune to user date changes in Settings. On reboot, FocusLock anchors elapsed boot time and audits wall-clock delta to prevent date skips.
                  </p>
                </div>

                <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2">
                  <span className="font-bold text-rose-400 block">2. Dual-Layer App Interception</span>
                  <p className="text-slate-300 text-[11px] leading-relaxed">
                    Combines <code className="text-rose-300">AccessibilityService</code> (detecting window state changes in under 50ms to dispatch Home) and <code className="text-rose-300">SYSTEM_ALERT_WINDOW</code> overlay that physically blocks touch interactions.
                  </p>
                </div>

                <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2">
                  <span className="font-bold text-emerald-400 block">3. Cryptographic Stop Protocol</span>
                  <p className="text-slate-300 text-[11px] leading-relaxed">
                    Phone runs an embedded NanoHTTPD listener on port 8765. The Fedora CLI (<code className="text-emerald-300">focuslock stop</code>) signs an HMAC-SHA256 packet containing a timestamp and random nonce, preventing replay attacks.
                  </p>
                </div>

                <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2">
                  <span className="font-bold text-amber-400 block">4. Device Owner Hardening</span>
                  <p className="text-slate-300 text-[11px] leading-relaxed">
                    Granted via ADB (<code className="text-amber-300">dpm set-device-owner</code>), allowing FocusLock to enforce <code className="text-amber-300">DISALLOW_SAFE_BOOT</code>, <code className="text-amber-300">DISALLOW_FACTORY_RESET</code>, and prevent package uninstall.
                  </p>
                </div>
              </div>

              {/* Exact ADB Command Block */}
              <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-xs text-white">ADB Device Owner Setup Command</span>
                  <button
                    onClick={() => copyCode('adb shell dpm set-device-owner com.focuslock/.admin.FocusLockDeviceAdminReceiver', 'Device Owner Command')}
                    className="text-xs px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-sky-400 font-mono cursor-pointer"
                  >
                    Copy Command
                  </button>
                </div>
                <pre className="p-2.5 rounded bg-slate-900 font-mono text-sky-300 text-xs overflow-x-auto">
                  adb shell dpm set-device-owner com.focuslock/.admin.FocusLockDeviceAdminReceiver
                </pre>
                <p className="text-[11px] text-slate-400">
                  Note: Android requires that no Google or personal accounts are signed in when granting Device Owner (or on a dedicated study profile/fresh device).
                </p>
              </div>
            </div>
          </div>
        )}

        {/* TAB 6: CODEBASE & EXPORT */}
        {activeTab === 'code' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            <div className="lg:col-span-4 space-y-2">
              <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider px-1">Source Code Explorer</h3>
              <div className="space-y-1.5">
                {ALL_CODE_FILES.map((file, idx) => (
                  <button
                    key={file.path}
                    onClick={() => setSelectedFileIndex(idx)}
                    className={`w-full text-left p-3 rounded-xl transition-all border flex items-center justify-between cursor-pointer ${
                      selectedFileIndex === idx
                        ? 'bg-rose-500/10 border-rose-500/40 text-rose-300 shadow-sm'
                        : 'bg-slate-900/60 border-slate-800/80 text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
                    }`}
                  >
                    <div className="truncate">
                      <div className="text-xs font-semibold text-slate-200 truncate">{file.name}</div>
                      <div className="text-[10px] text-slate-500 font-mono truncate">{file.category}</div>
                    </div>
                    <ChevronRight className="w-4 h-4 text-slate-600 flex-shrink-0" />
                  </button>
                ))}
              </div>
            </div>

            <div className="lg:col-span-8 bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl">
              <div className="p-4 border-b border-slate-800 bg-slate-950/60 flex items-center justify-between">
                <div>
                  <div className="font-mono text-xs text-rose-400 font-semibold">{ALL_CODE_FILES[selectedFileIndex].path}</div>
                  <div className="text-xs text-slate-400">{ALL_CODE_FILES[selectedFileIndex].description}</div>
                </div>
                <button
                  onClick={() => copyCode(ALL_CODE_FILES[selectedFileIndex].code, ALL_CODE_FILES[selectedFileIndex].path)}
                  className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium transition-all cursor-pointer"
                >
                  {copiedText === ALL_CODE_FILES[selectedFileIndex].path ? (
                    <>
                      <Check className="w-3.5 h-3.5 text-emerald-400" /> Copied
                    </>
                  ) : (
                    <>
                      <Copy className="w-3.5 h-3.5" /> Copy Code
                    </>
                  )}
                </button>
              </div>

              <pre className="p-5 text-xs font-mono text-slate-300 bg-slate-950 overflow-x-auto max-h-[580px] leading-relaxed">
                <code>{ALL_CODE_FILES[selectedFileIndex].code}</code>
              </pre>
            </div>
          </div>
        )}
      </main>

      {/* Setup Guide Modal */}
      {showSetupGuide && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-xl w-full p-6 space-y-5">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2.5">
                <Compass className="w-5 h-5 text-sky-400" />
                <h3 className="font-bold text-white text-base">FocusLock Beginner & Setup Guide</h3>
              </div>
              <button onClick={() => setShowSetupGuide(false)} className="text-slate-400 hover:text-white p-1 cursor-pointer">
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-4 text-xs">
              <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5">
                <div className="font-bold text-white">Step 1: Download & Unzip</div>
                <p className="text-slate-400 text-[11px]">
                  Click <strong>Download APK (.zip)</strong> in the top-right header, or run on your Fedora terminal:
                </p>
                <pre className="p-2 rounded bg-slate-900 font-mono text-sky-300 text-[11px]">
                  wget -O focuslock-project.zip {window.location.origin}/focuslock-project.zip && unzip focuslock-project.zip
                </pre>
              </div>

              <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5">
                <div className="font-bold text-white">Step 2: Compile & Install APK on Phone</div>
                <p className="text-slate-400 text-[11px]">
                  On your Fedora Linux laptop, build the APK with Gradle:
                </p>
                <pre className="p-2 rounded bg-slate-900 font-mono text-sky-300 text-[11px]">
                  cd android && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
                </pre>
              </div>

              <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5">
                <div className="font-bold text-white">Step 3: Run Fedora Companion CLI</div>
                <p className="text-slate-400 text-[11px]">
                  Pair your phone and stop active sessions whenever needed:
                </p>
                <pre className="p-2 rounded bg-slate-900 font-mono text-sky-300 text-[11px]">
                  cd companion && pip install -r requirements.txt && python3 cli.py pair
                </pre>
              </div>
            </div>

            <button
              onClick={() => setShowSetupGuide(false)}
              className="w-full py-2.5 rounded-xl bg-sky-500 hover:bg-sky-400 text-slate-950 font-bold text-xs cursor-pointer"
            >
              Got it, let's test!
            </button>
          </div>
        </div>
      )}

      {/* Offline Modal */}
      {showOfflineModal && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-md w-full p-6 space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-amber-500/10 text-amber-400 flex items-center justify-center">
                <Key className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-bold text-white text-base">Enter Offline Stop Token</h3>
                <p className="text-xs text-slate-400">Generated on your laptop via focuslock token</p>
              </div>
            </div>

            <textarea
              value={offlineTokenInput}
              onChange={(e) => {
                setOfflineTokenInput(e.target.value);
                setOfflineTokenError(null);
              }}
              placeholder="Paste Base64 HMAC Token here..."
              rows={3}
              className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 font-mono text-xs text-sky-300 focus:outline-none focus:border-sky-500"
            />

            {offlineTokenError && (
              <div className="text-xs text-rose-400">{offlineTokenError}</div>
            )}

            <div className="flex gap-2">
              <button
                onClick={() => setShowOfflineModal(false)}
                className="flex-1 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-300 cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={handleVerifyOfflineToken}
                className="flex-1 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-xs font-bold text-white cursor-pointer"
              >
                Verify & Unlock
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Footer */}
      <footer className="border-t border-slate-800/80 bg-[#080C15] py-4 px-6 text-center text-xs text-slate-500">
        FocusLock Personal Study Enforcement System · Android + Fedora Linux Companion · Verified Production Architecture
      </footer>
    </div>
  );
}
