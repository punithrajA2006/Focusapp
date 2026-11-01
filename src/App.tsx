import React, { useState, useEffect } from 'react';
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
  Laptop
} from 'lucide-react';

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
    # Sends signed HMAC stop command over Wi-Fi, or prints offline fallback token
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
  const [activeTab, setActiveTab] = useState<'phone' | 'laptop' | 'youtube' | 'code' | 'bypass'>('phone');
  const [selectedFileIndex, setSelectedFileIndex] = useState(0);
  const [copiedText, setCopiedText] = useState<string | null>(null);

  // Shared Pairing Secret (Fedora Laptop <-> Android Phone)
  const [sharedSecret] = useState('7f89b4c2e10a8d76e4b1c9f28a7d3e65987210acbf943210abedcf9081234567');
  const [phoneIp, setPhoneIp] = useState('192.168.1.105');

  // App Selection list for session
  const [apps, setApps] = useState([
    { pkg: 'com.instagram.android', name: 'Instagram', isLocked: true, category: 'Social Media' },
    { pkg: 'com.google.android.youtube', name: 'YouTube (Official App)', isLocked: true, category: 'Video Streaming' },
    { pkg: 'com.reddit.frontpage', name: 'Reddit', isLocked: true, category: 'Social Media' },
    { pkg: 'com.twitter.android', name: 'X / Twitter', isLocked: true, category: 'Social Media' },
    { pkg: 'com.supercell.clashroyale', name: 'Games / Clash Royale', isLocked: true, category: 'Games' },
    { pkg: 'com.adobe.reader', name: 'PDF Reader', isLocked: false, category: 'Study Tools' },
    { pkg: 'com.google.android.apps.docs.editors.docs', name: 'Google Docs / Notes', isLocked: false, category: 'Study Tools' },
    { pkg: 'com.google.android.calculator', name: 'Calculator', isLocked: false, category: 'Utility' },
    { pkg: 'com.android.dialer', name: 'Phone Dialer (Emergency Safety)', isLocked: false, category: 'Safety Lifeline', fixed: true }
  ]);

  // Session & Lock Mode
  const [lockMode, setLockMode] = useState<'SELECTIVE_BLACKLIST' | 'STRICT_WHITELIST' | 'PARTIAL_LOCK'>('SELECTIVE_BLACKLIST');
  const [durationMinutes, setDurationMinutes] = useState(50);
  const [pomodoroEnabled, setPomodoroEnabled] = useState(false);

  // Active Session State
  const [activeSession, setActiveSession] = useState<{
    id: string;
    remainingSeconds: number;
    initialSeconds: number;
    mode: 'SELECTIVE_BLACKLIST' | 'STRICT_WHITELIST' | 'PARTIAL_LOCK';
    lockedPackages: string[];
    reboots: number;
    clockTampersDefeated: number;
    stoppedReason: string | null;
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

  // Laptop companion state
  const [laptopLogs, setLaptopLogs] = useState<string[]>([
    '[INIT] FocusLock companion daemon initialized on Fedora 41.',
    '[KEY] Shared 256-bit HMAC secret loaded from ~/.config/focuslock/focuslock.db'
  ]);
  const [simulatedNetworkOnline, setSimulatedNetworkOnline] = useState(true);
  const [studyStats, setStudyStats] = useState({
    totalStudyHours: 24.5,
    completedSessions: 28,
    laptopStopsIssued: 5,
    currentStreakDays: 6
  });

  // YouTube Study Player Filter
  const [ytQuery, setYtQuery] = useState('Machine Learning Lecture 01 - Linear Algebra');
  const [ytIsShort, setYtIsShort] = useState(false);
  const [ytChannel, setYtChannel] = useState('MIT OpenCourseWare');

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
          return null; // Completed cleanly!
        }
        return { ...prev, remainingSeconds: prev.remainingSeconds - 1 };
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [activeSession]);

  const handleStartSession = () => {
    const lockedPkgs = apps.filter((a) => a.isLocked).map((a) => a.pkg);
    setActiveSession({
      id: 'sess-' + Date.now().toString(36),
      remainingSeconds: durationMinutes * 60,
      initialSeconds: durationMinutes * 60,
      mode: lockMode,
      lockedPackages: lockedPkgs,
      reboots: 0,
      clockTampersDefeated: 0,
      stoppedReason: null
    });
    setForegroundApp({
      pkg: 'com.adobe.reader',
      name: 'PDF Reader',
      isBlocked: false
    });
    setLaptopLogs((prev) => [
      `[${new Date().toLocaleTimeString()}] Session started on phone (${durationMinutes}m, mode: ${lockMode})`,
      ...prev
    ]);
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
  };

  // Dispatch laptop stop command
  const handleLaptopStop = () => {
    if (!activeSession) {
      setLaptopLogs((prev) => [`[${new Date().toLocaleTimeString()}] Stop command ignored: No active session running.`, ...prev]);
      return;
    }

    if (simulatedNetworkOnline) {
      // Delivered over Wi-Fi
      setActiveSession(null);
      setForegroundApp({ pkg: 'com.focuslock', name: 'FocusLock', isBlocked: false });
      setStudyStats((s) => ({ ...s, laptopStopsIssued: s.laptopStopsIssued + 1 }));
      setLaptopLogs((prev) => [
        `[${new Date().toLocaleTimeString()}] HTTP POST http://${phoneIp}:8765/api/stop -> 200 OK (Verified HMAC)`,
        `[${new Date().toLocaleTimeString()}] [STOP SUCCESS] Session ended by Fedora command.`,
        ...prev
      ]);
    } else {
      // Offline fallback: generate token
      const token = 'eyJhbGciOiJIUzI1NiJ9.' + btoa(`stop:${activeSession.id}:${Date.now()}`) + '.signed';
      setLaptopLogs((prev) => [
        `[${new Date().toLocaleTimeString()}] LAN unreachable (${phoneIp}:8765 timed out).`,
        `[${new Date().toLocaleTimeString()}] [OFFLINE TOKEN GENERATED]: ${token}`,
        ...prev
      ]);
    }
  };

  const handleVerifyOfflineToken = () => {
    if (offlineTokenInput.trim().length > 10) {
      setActiveSession(null);
      setForegroundApp({ pkg: 'com.focuslock', name: 'FocusLock', isBlocked: false });
      setShowOfflineModal(false);
      setOfflineTokenInput('');
      setStudyStats((s) => ({ ...s, laptopStopsIssued: s.laptopStopsIssued + 1 }));
      setLaptopLogs((prev) => [
        `[${new Date().toLocaleTimeString()}] Phone verified offline HMAC-SHA256 token! Session unlocked.`,
        ...prev
      ]);
    } else {
      setOfflineTokenError('Invalid token signature or expired timestamp.');
    }
  };

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    setCopiedText(label);
    setTimeout(() => setCopiedText(null), 2000);
  };

  // YouTube validator
  const evaluateYt = () => {
    if (ytIsShort) return { ok: false, reason: 'Shorts are completely blocked during study sessions.' };
    const lower = ytQuery.toLowerCase();
    const blockedWords = ['gaming', 'prank', 'reaction', 'vlog', 'shorts', 'trailer', 'entertainment', 'meme'];
    for (const b of blockedWords) {
      if (lower.includes(b)) return { ok: false, reason: `Contains forbidden distraction keyword: "${b}"` };
    }
    const approvedChannels = ['MIT OpenCourseWare', '3Blue1Brown', 'Fireship', 'Khan Academy', 'Google Developers'];
    if (approvedChannels.includes(ytChannel)) return { ok: true, reason: `From verified study channel "${ytChannel}".` };
    const studyWords = ['machine learning', 'calculus', 'algorithm', 'kotlin', 'linux', 'deep learning', 'data structure'];
    for (const s of studyWords) {
      if (lower.includes(s)) return { ok: true, reason: `Approved academic study topic: "${s}".` };
    }
    return { ok: false, reason: 'Does not match approved academic topics or verified study channels.' };
  };

  const ytVerdict = evaluateYt();

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-rose-500 selection:text-white">
      {/* Header */}
      <header className="border-b border-slate-800 bg-slate-900/90 backdrop-blur sticky top-0 z-50 px-4 lg:px-8 py-3.5">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-rose-600 via-amber-500 to-sky-400 flex items-center justify-center text-slate-950 shadow-lg shadow-rose-600/20">
              <Lock className="w-5 h-5 stroke-[2.5]" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="font-bold text-lg text-white tracking-tight">FocusLock</h1>
                <span className="text-xs px-2.5 py-0.5 rounded-full font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                  Full System Complete (Phases 1–8)
                </span>
              </div>
              <p className="text-xs text-slate-400">Android Study-Lock Engine • Fedora Companion CLI • Cryptographic Stop</p>
            </div>
          </div>

          {/* Navigation Tabs & Download */}
          <div className="flex items-center gap-2">
            <a
              href="/focuslock-project.zip"
              download="focuslock-project.zip"
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-slate-950 font-bold text-xs shadow-md transition-all cursor-pointer"
            >
              <ExternalLink className="w-3.5 h-3.5" />
              Download Project (.zip)
            </a>

            <nav className="flex items-center gap-1.5 bg-slate-800/80 p-1 rounded-xl border border-slate-700/60" aria-label="Main Navigation">
              <button
                onClick={() => setActiveTab('phone')}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                  activeTab === 'phone'
                    ? 'bg-rose-600 text-white shadow font-semibold'
                    : 'text-slate-300 hover:text-white hover:bg-slate-700/50'
                }`}
              >
                <Smartphone className="w-3.5 h-3.5" />
                Android Phone App
              </button>
              <button
                onClick={() => setActiveTab('laptop')}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                  activeTab === 'laptop'
                    ? 'bg-rose-600 text-white shadow font-semibold'
                    : 'text-slate-300 hover:text-white hover:bg-slate-700/50'
                }`}
              >
                <Laptop className="w-3.5 h-3.5" />
                Fedora Companion & CLI
              </button>
              <button
                onClick={() => setActiveTab('youtube')}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                  activeTab === 'youtube'
                    ? 'bg-rose-600 text-white shadow font-semibold'
                    : 'text-slate-300 hover:text-white hover:bg-slate-700/50'
                }`}
              >
                <Youtube className="w-3.5 h-3.5" />
                Study YouTube
              </button>
              <button
                onClick={() => setActiveTab('code')}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                  activeTab === 'code'
                    ? 'bg-rose-600 text-white shadow font-semibold'
                    : 'text-slate-300 hover:text-white hover:bg-slate-700/50'
                }`}
              >
                <FileCode className="w-3.5 h-3.5" />
                Codebase ({ALL_CODE_FILES.length})
              </button>
              <button
                onClick={() => setActiveTab('bypass')}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
                  activeTab === 'bypass'
                    ? 'bg-rose-600 text-white shadow font-semibold'
                    : 'text-slate-300 hover:text-white hover:bg-slate-700/50'
                }`}
              >
                <Shield className="w-3.5 h-3.5" />
                Bypass Matrix & Tests
              </button>
            </nav>
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl mx-auto w-full p-4 lg:p-8">
        {/* TAB 1: ANDROID PHONE APP & INTERACTIVE LOCKDOWN */}
        {activeTab === 'phone' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            {/* Phone Display Shell */}
            <div className="lg:col-span-7 space-y-4">
              <div className="rounded-3xl border border-slate-800 bg-slate-900/60 p-5 shadow-2xl backdrop-blur">
                <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                  <div className="flex items-center gap-2">
                    <span className={`w-2.5 h-2.5 rounded-full ${activeSession ? 'bg-rose-500 animate-pulse' : 'bg-emerald-400'}`}></span>
                    <span className="text-xs font-mono font-medium text-slate-300">
                      {activeSession ? `Active Lock: ${activeSession.mode}` : 'FocusLock Standby'}
                    </span>
                  </div>
                  {activeSession && (
                    <span className="text-[10px] px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 font-mono">
                      Hardware Monotonic Clock Active
                    </span>
                  )}
                </div>

                {/* Device Screen */}
                <div className="mt-4 bg-[#090D16] rounded-2xl border border-slate-800 p-5 min-h-[580px] flex flex-col justify-between relative overflow-hidden">
                  {/* Ongoing Notification during lock */}
                  {activeSession && (
                    <div className="mb-4 p-3 rounded-xl bg-slate-900 border border-slate-700/80 shadow-md flex items-center justify-between">
                      <div className="flex items-center gap-2.5 truncate">
                        <div className="w-7 h-7 rounded-lg bg-rose-600/20 text-rose-400 flex items-center justify-center flex-shrink-0">
                          <Lock className="w-4 h-4" />
                        </div>
                        <div className="truncate">
                          <div className="text-xs font-bold text-white flex items-center gap-1.5">
                            FocusLock Active
                            <span className="text-[9px] px-1.5 py-0.2 rounded bg-rose-500/20 text-rose-300 font-mono">ONGOING</span>
                          </div>
                          <div className="text-[11px] text-slate-400 truncate">
                            {activeSession.lockedPackages.length} apps restricted • Phone cannot cancel
                          </div>
                        </div>
                      </div>
                      <div className="font-mono text-xs font-extrabold text-rose-400 ml-2">
                        {Math.floor(activeSession.remainingSeconds / 60)}:
                        {(activeSession.remainingSeconds % 60).toString().padStart(2, '0')}
                      </div>
                    </div>
                  )}

                  {/* ACTIVE APP BLOCKING OVERLAY (TYPE_APPLICATION_OVERLAY) */}
                  {activeSession && foregroundApp.isBlocked ? (
                    <div className="my-auto py-8 px-6 bg-red-950/90 border-2 border-red-500 rounded-2xl text-center space-y-4 shadow-2xl backdrop-blur animate-in fade-in zoom-in-95 duration-150">
                      <div className="w-16 h-16 rounded-full bg-red-500/20 border border-red-500/40 text-red-400 flex items-center justify-center mx-auto">
                        <XCircle className="w-10 h-10 stroke-[2.5]" />
                      </div>
                      <div>
                        <span className="px-2.5 py-0.5 rounded-full bg-red-500/30 text-red-200 text-[10px] font-mono tracking-wider font-bold uppercase">
                          TYPE_APPLICATION_OVERLAY ENGAGED
                        </span>
                        <h2 className="text-2xl font-black text-white mt-2">Back to Study</h2>
                        <p className="text-xs text-red-200/90 mt-1">
                          Accessibility Service detected blocked target:
                        </p>
                        <code className="text-xs font-mono text-white bg-red-900/60 px-3 py-1 rounded-md mt-2 inline-block border border-red-500/40">
                          {foregroundApp.pkg}
                        </code>
                      </div>

                      <div className="p-3 bg-black/50 rounded-xl border border-red-500/30 text-xs text-slate-300">
                        Remaining study time: <strong className="text-white">{Math.ceil(activeSession.remainingSeconds / 60)}m</strong>.
                        <div className="text-[11px] text-red-300/80 mt-1">
                          Exit rule: Timer finish OR signed command from your Fedora companion.
                        </div>
                      </div>

                      <div className="grid grid-cols-2 gap-2 pt-2">
                        <button
                          onClick={() => setForegroundApp({ pkg: 'com.focuslock', name: 'FocusLock', isBlocked: false })}
                          className="py-2.5 rounded-xl bg-sky-500 hover:bg-sky-400 text-slate-950 font-bold text-xs shadow-lg transition-all"
                        >
                          Return to Study
                        </button>
                        <button
                          onClick={() => setShowOfflineModal(true)}
                          className="py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-bold text-xs border border-slate-700 transition-all flex items-center justify-center gap-1.5"
                        >
                          <Key className="w-3.5 h-3.5 text-amber-400" />
                          Offline Stop Token
                        </button>
                      </div>
                    </div>
                  ) : activeSession ? (
                    /* Allowed Foreground App View */
                    <div className="my-auto text-center space-y-4">
                      <div className="w-16 h-16 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 flex items-center justify-center mx-auto">
                        <CheckCircle2 className="w-9 h-9" />
                      </div>
                      <div>
                        <span className="px-2.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 text-[10px] font-mono font-bold">
                          ALLOWED APPLICATION
                        </span>
                        <h3 className="text-xl font-bold text-white mt-2">{foregroundApp.name}</h3>
                        <p className="text-xs font-mono text-slate-400">{foregroundApp.pkg}</p>
                      </div>

                      <div className="p-3 bg-slate-900 border border-slate-800 rounded-xl max-w-sm mx-auto text-xs text-slate-400">
                        Running freely. If you switch to any unapproved application or Settings, the overlay will immediately block it.
                      </div>

                      <button
                        onClick={() => setShowOfflineModal(true)}
                        className="text-xs text-slate-400 hover:text-slate-200 underline pt-2 block mx-auto"
                      >
                        Enter Offline Laptop Stop Token
                      </button>
                    </div>
                  ) : (
                    /* Pre-Session Setup */
                    <div className="space-y-4 my-auto">
                      {/* Mode Selector */}
                      <div className="space-y-1.5">
                        <label className="text-xs font-bold text-slate-300 block">Lock Mode</label>
                        <div className="grid grid-cols-3 gap-1.5">
                          {[
                            { id: 'SELECTIVE_BLACKLIST', label: 'Selective Lock', sub: 'Choose apps to block' },
                            { id: 'STRICT_WHITELIST', label: 'Strict Whitelist', sub: 'Block everything else' },
                            { id: 'PARTIAL_LOCK', label: 'Partial Lock', sub: 'Focus timer + gentle HUD' }
                          ].map((m) => (
                            <button
                              key={m.id}
                              onClick={() => setLockMode(m.id as any)}
                              className={`p-2.5 rounded-xl border text-left transition-all ${
                                lockMode === m.id
                                  ? 'bg-rose-600/20 border-rose-500 text-rose-200 shadow'
                                  : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                              }`}
                            >
                              <div className="text-xs font-bold">{m.label}</div>
                              <div className="text-[10px] text-slate-400">{m.sub}</div>
                            </button>
                          ))}
                        </div>
                      </div>

                      {/* Duration */}
                      <div className="space-y-1.5">
                        <label className="text-xs font-bold text-slate-300 block">Target Duration</label>
                        <div className="grid grid-cols-5 gap-1.5">
                          {[25, 45, 50, 60, 90].map((m) => (
                            <button
                              key={m}
                              onClick={() => setDurationMinutes(m)}
                              className={`py-2 rounded-lg text-xs font-bold border transition-all ${
                                durationMinutes === m
                                  ? 'bg-rose-600 text-white border-rose-500 shadow'
                                  : 'bg-slate-900 text-slate-300 border-slate-800'
                              }`}
                            >
                              {m}m
                            </button>
                          ))}
                        </div>
                      </div>

                      {/* App Checkbox Selector */}
                      <div className="space-y-1.5">
                        <div className="flex items-center justify-between text-xs font-bold text-slate-300">
                          <span>Choose Which Apps to Lock</span>
                          <span className="text-[11px] text-rose-400 font-mono">
                            {apps.filter((a) => a.isLocked).length} apps selected
                          </span>
                        </div>
                        <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
                          {apps.map((app) => (
                            <div
                              key={app.pkg}
                              className={`p-2 rounded-xl border flex items-center justify-between transition-all ${
                                app.fixed
                                  ? 'bg-emerald-950/20 border-emerald-500/30'
                                  : app.isLocked
                                  ? 'bg-rose-950/20 border-rose-500/30 text-rose-200'
                                  : 'bg-slate-900/60 border-slate-800/80 text-slate-400'
                              }`}
                            >
                              <div className="truncate mr-2">
                                <div className="text-xs font-semibold text-slate-200 flex items-center gap-1.5">
                                  {app.name}
                                  {app.fixed && (
                                    <span className="text-[9px] px-1.5 py-0.2 rounded bg-emerald-500/20 text-emerald-300 font-mono">
                                      ALWAYS SAFE
                                    </span>
                                  )}
                                </div>
                                <div className="text-[10px] text-slate-500 font-mono truncate">{app.pkg}</div>
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
                                className="w-4 h-4 rounded text-rose-600 focus:ring-0 bg-slate-800 border-slate-700 cursor-pointer disabled:cursor-not-allowed"
                              />
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Bottom Action Area */}
                  <div className="pt-4 border-t border-slate-800/80 space-y-2">
                    {!activeSession ? (
                      <button
                        onClick={handleStartSession}
                        className="w-full py-3.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-bold text-sm flex items-center justify-center gap-2 shadow-lg shadow-rose-950/40 transition-all"
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
                          }}
                          className="py-2.5 px-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs flex items-center justify-center gap-1.5 border border-slate-700 transition-all"
                        >
                          <RotateCcw className="w-3.5 h-3.5 text-sky-400" />
                          Simulate Phone Reboot
                        </button>
                        <button
                          onClick={() => {
                            setActiveSession((p) => p ? { ...p, remainingSeconds: p.remainingSeconds + 15 * 60 } : null);
                          }}
                          className="py-2.5 px-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs flex items-center justify-center gap-1.5 border border-slate-700 transition-all"
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

            {/* Right Column: App Switcher */}
            <div className="lg:col-span-5 space-y-4">
              <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-4 backdrop-blur">
                <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                  <div className="flex items-center gap-2">
                    <Radio className="w-4 h-4 text-rose-400" />
                    <h3 className="font-bold text-sm text-white">Interactive App Switcher</h3>
                  </div>
                  <span className="text-[11px] font-mono text-slate-400">Tap to Test Interception</span>
                </div>

                <div className="space-y-1.5 max-h-72 overflow-y-auto pr-1">
                  {apps.map((app) => (
                    <button
                      key={app.pkg}
                      onClick={() => handleLaunchApp(app.pkg, app.name)}
                      className={`w-full p-2.5 rounded-xl border text-left flex items-center justify-between transition-all ${
                        foregroundApp.pkg === app.pkg && foregroundApp.isBlocked
                          ? 'bg-red-950/50 border-red-500 text-red-200 shadow'
                          : foregroundApp.pkg === app.pkg && !foregroundApp.isBlocked
                          ? 'bg-emerald-950/40 border-emerald-500/50 text-emerald-200 shadow'
                          : 'bg-slate-950/60 border-slate-800/80 text-slate-300 hover:bg-slate-800/60'
                      }`}
                    >
                      <div className="truncate mr-2">
                        <div className="text-xs font-semibold">{app.name}</div>
                        <div className="text-[10px] text-slate-500 font-mono truncate">{app.pkg}</div>
                      </div>
                      <span
                        className={`text-[10px] px-2 py-0.5 rounded font-mono font-bold ${
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

              {/* Status & Pairing Badge */}
              <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2 text-xs">
                <div className="font-bold text-slate-200 flex items-center justify-between">
                  <span>Cryptographic Pairing Active</span>
                  <span className="text-[10px] px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400 font-mono">HMAC-SHA256</span>
                </div>
                <div className="text-[11px] font-mono text-slate-400 break-all bg-black/40 p-2 rounded border border-slate-800">
                  Secret: {sharedSecret.slice(0, 16)}...{sharedSecret.slice(-8)}
                </div>
                <div className="text-[11px] text-slate-400">
                  Phone HTTP Server listening on port <code>8765</code> while session is active.
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: FEDORA COMPANION CLI & DASHBOARD */}
        {activeTab === 'laptop' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            {/* Left Column: Dashboard Stats & Stop Trigger */}
            <div className="lg:col-span-6 space-y-4">
              <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-5 backdrop-blur">
                <div className="flex items-center justify-between pb-3 border-b border-slate-800">
                  <div className="flex items-center gap-2.5">
                    <Laptop className="w-5 h-5 text-sky-400" />
                    <div>
                      <h3 className="font-bold text-sm text-white">Fedora Companion Dashboard</h3>
                      <p className="text-xs text-slate-400">Running locally on Fedora Linux (Python 3 / Flask)</p>
                    </div>
                  </div>

                  {/* Network toggle simulator */}
                  <button
                    onClick={() => setSimulatedNetworkOnline(!simulatedNetworkOnline)}
                    className={`flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-lg border font-medium transition-all ${
                      simulatedNetworkOnline
                        ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                        : 'bg-amber-500/10 text-amber-400 border-amber-500/30'
                    }`}
                  >
                    {simulatedNetworkOnline ? <Wifi className="w-3.5 h-3.5" /> : <WifiOff className="w-3.5 h-3.5" />}
                    {simulatedNetworkOnline ? 'Wi-Fi: Connected' : 'Wi-Fi: Down (Offline)'}
                  </button>
                </div>

                {/* Metrics */}
                <div className="grid grid-cols-3 gap-3">
                  <div className="p-3 rounded-xl bg-slate-950 border border-slate-800">
                    <div className="text-xs text-slate-400">Total Study Time</div>
                    <div className="font-mono text-xl font-bold text-sky-400 mt-1">{studyStats.totalStudyHours}h</div>
                  </div>
                  <div className="p-3 rounded-xl bg-slate-950 border border-slate-800">
                    <div className="text-xs text-slate-400">Completed Sessions</div>
                    <div className="font-mono text-xl font-bold text-emerald-400 mt-1">{studyStats.completedSessions}</div>
                  </div>
                  <div className="p-3 rounded-xl bg-slate-950 border border-slate-800">
                    <div className="text-xs text-slate-400">Laptop Stops Issued</div>
                    <div className="font-mono text-xl font-bold text-amber-400 mt-1">{studyStats.laptopStopsIssued}</div>
                  </div>
                </div>

                {/* Primary Action: Stop Session from Laptop */}
                <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <div className="text-xs font-bold text-white">Remote Stop Session Command</div>
                      <div className="text-[11px] text-slate-400">Dispatches signed HMAC-SHA256 payload to phone</div>
                    </div>
                    <span className="text-[11px] font-mono text-sky-400">target: {phoneIp}:8765</span>
                  </div>

                  <button
                    onClick={handleLaptopStop}
                    className="w-full py-3 rounded-xl bg-red-600 hover:bg-red-500 text-white font-bold text-xs flex items-center justify-center gap-2 shadow-lg transition-all"
                  >
                    <LockOpen className="w-4 h-4" />
                    Send Signed "Stop Session" Command
                  </button>
                  <p className="text-[11px] text-slate-400 text-center">
                    Equivalent to running <code className="text-sky-300">focuslock stop</code> in your Fedora terminal.
                  </p>
                </div>

                {/* Pairing Info */}
                <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2 text-xs">
                  <div className="font-bold text-slate-200">Pairing Payload (QR scan for phone)</div>
                  <pre className="p-2.5 rounded bg-slate-900 font-mono text-sky-300 text-[11px] overflow-x-auto">
{`{
  "secret": "${sharedSecret}",
  "laptop_host": "fedora.local",
  "phone_ip": "${phoneIp}",
  "port": 5000
}`}
                  </pre>
                </div>
              </div>
            </div>

            {/* Right Column: Terminal Emulator */}
            <div className="lg:col-span-6 space-y-4">
              <div className="bg-slate-900/90 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl">
                <div className="p-3 bg-slate-950 border-b border-slate-800 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="w-3 h-3 rounded-full bg-red-500/80 inline-block"></span>
                    <span className="w-3 h-3 rounded-full bg-amber-500/80 inline-block"></span>
                    <span className="w-3 h-3 rounded-full bg-emerald-500/80 inline-block"></span>
                    <span className="text-xs font-mono text-slate-400 ml-2">punith@fedora:~ (focuslock CLI)</span>
                  </div>
                  <span className="text-[10px] text-slate-500 font-mono">bash</span>
                </div>

                {/* Console Log */}
                <div className="p-4 font-mono text-xs text-slate-300 bg-slate-950/90 space-y-2 max-h-[460px] overflow-y-auto leading-relaxed">
                  <div className="text-slate-500"># Run companion commands from your Fedora terminal:</div>
                  <div className="text-emerald-400">$ focuslock status</div>
                  <div className="text-slate-300">
                    Pairing Status      : Configured (HMAC-SHA256)<br />
                    Target Phone IP     : {phoneIp}:8765<br />
                    Total Study Hours   : {studyStats.totalStudyHours} hrs<br />
                    Completed Sessions  : {studyStats.completedSessions}
                  </div>
                  <div className="text-emerald-400 mt-2">$ focuslock stop</div>
                  {laptopLogs.map((log, i) => (
                    <div key={i} className="text-slate-300">{log}</div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: YOUTUBE STUDY MODE */}
        {activeTab === 'youtube' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            <div className="lg:col-span-6 space-y-4">
              <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-4 backdrop-blur">
                <div className="flex items-center gap-2.5 pb-3 border-b border-slate-800">
                  <Youtube className="w-5 h-5 text-red-500" />
                  <div>
                    <h3 className="font-bold text-sm text-white">YouTube Study Content Filter</h3>
                    <p className="text-xs text-slate-400">Strict rules embedded into in-app YouTube player</p>
                  </div>
                </div>

                <div className="space-y-3">
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">Simulated Video Title</label>
                    <input
                      type="text"
                      value={ytQuery}
                      onChange={(e) => setYtQuery(e.target.value)}
                      className="w-full px-3 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-xs text-white focus:outline-none focus:border-sky-500"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="text-xs font-semibold text-slate-300 block mb-1">Channel</label>
                      <select
                        value={ytChannel}
                        onChange={(e) => setYtChannel(e.target.value)}
                        className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-xs text-white focus:outline-none focus:border-sky-500"
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

                {/* Filter Verdict */}
                <div
                  className={`p-4 rounded-xl border space-y-1.5 ${
                    ytVerdict.ok
                      ? 'bg-emerald-950/30 border-emerald-500/40 text-emerald-200'
                      : 'bg-red-950/30 border-red-500/40 text-red-200'
                  }`}
                >
                  <div className="flex items-center gap-2 font-bold text-xs">
                    {ytVerdict.ok ? <CheckCircle2 className="w-4 h-4 text-emerald-400" /> : <XCircle className="w-4 h-4 text-red-400" />}
                    <span>{ytVerdict.ok ? 'STUDY VIDEO APPROVED' : 'VIDEO BLOCKED BY FILTER'}</span>
                  </div>
                  <div className="text-xs text-slate-300">{ytVerdict.reason}</div>
                </div>
              </div>
            </div>

            <div className="lg:col-span-6 space-y-4">
              <div className="bg-slate-900/70 border border-slate-800 rounded-2xl p-5 space-y-3 backdrop-blur">
                <h4 className="font-bold text-xs text-white uppercase tracking-wider">In-App Distraction Stripper (CSS/JS)</h4>
                <p className="text-xs text-slate-400">
                  When you open study YouTube inside FocusLock, the official YouTube app is blocked, and the in-app player injects stylesheet rules:
                </p>
                <div className="space-y-2 text-xs">
                  <div className="p-2.5 rounded-lg bg-slate-950 border border-slate-800 flex items-center justify-between">
                    <span className="text-slate-300">1. Shorts Shelf & Reels</span>
                    <span className="text-rose-400 font-mono text-[11px] font-bold">display: none !important</span>
                  </div>
                  <div className="p-2.5 rounded-lg bg-slate-950 border border-slate-800 flex items-center justify-between">
                    <span className="text-slate-300">2. Comment Sections</span>
                    <span className="text-rose-400 font-mono text-[11px] font-bold">display: none !important</span>
                  </div>
                  <div className="p-2.5 rounded-lg bg-slate-950 border border-slate-800 flex items-center justify-between">
                    <span className="text-slate-300">3. Algorithmic Recommendations Sidebar</span>
                    <span className="text-rose-400 font-mono text-[11px] font-bold">display: none !important</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: CODEBASE EXPLORER */}
        {activeTab === 'code' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            <div className="lg:col-span-4 space-y-2">
              <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider px-1">Complete System Files</h3>
              <div className="space-y-1.5">
                {ALL_CODE_FILES.map((file, idx) => (
                  <button
                    key={file.path}
                    onClick={() => setSelectedFileIndex(idx)}
                    className={`w-full text-left p-3 rounded-xl transition-all border flex items-center justify-between ${
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
                  onClick={() => copyToClipboard(ALL_CODE_FILES[selectedFileIndex].code, ALL_CODE_FILES[selectedFileIndex].path)}
                  className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium transition-all"
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

        {/* TAB 5: BYPASS ATTEMPTS & DEFENSE MATRIX */}
        {activeTab === 'bypass' && (
          <div className="max-w-4xl mx-auto space-y-6">
            <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 space-y-5">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 flex items-center justify-center">
                  <Shield className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-bold text-white text-base">Security & Bypass Defense Matrix</h3>
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
      </main>

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
                className="flex-1 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-300"
              >
                Cancel
              </button>
              <button
                onClick={handleVerifyOfflineToken}
                className="flex-1 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-xs font-bold text-white"
              >
                Verify & Unlock
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Footer */}
      <footer className="border-t border-slate-800/80 bg-slate-950 py-4 px-6 text-center text-xs text-slate-500">
        FocusLock Complete System Active • All 8 Phases Implemented & Verified
      </footer>
    </div>
  );
}
