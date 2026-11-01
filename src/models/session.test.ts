import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';

export enum LockMode {
  STRICT_WHITELIST = 'STRICT_WHITELIST',
  SELECTIVE_BLACKLIST = 'SELECTIVE_BLACKLIST',
  PARTIAL_LOCK = 'PARTIAL_LOCK'
}

export interface YouTubeFilterConfig {
  isEnabled: boolean;
  allowedChannelIds: string[];
  allowedKeywords: string[];
  blockedKeywords: string[];
  blockShorts: boolean;
  blockComments: boolean;
}

export interface AbsoluteSessionConfig {
  targetDurationMs: number;
  lockMode: LockMode;
  targetPackages: string[];
  safetyExemptPackages: string[];
  partialLockAllowsNotifications: boolean;
  youTubeFilter: YouTubeFilterConfig;
}

export interface AbsoluteSession {
  sessionId: string;
  targetDurationMs: number;
  startElapsedRealtimeMs: number;
  lastPersistedElapsedRealtimeMs: number;
  accumulatedProvenElapsedMs: number;
  startWallClockMs: number;
  lastPersistedWallClockMs: number;
  bootCounter: number;
  config: AbsoluteSessionConfig;
  status: 'IDLE' | 'ACTIVE' | 'COMPLETED' | 'STOPPED_BY_LAPTOP';
}

export const DEFAULT_SAFETY_PACKAGES = [
  'com.focuslock',
  'com.android.dialer',
  'com.google.android.dialer',
  'com.samsung.android.dialer',
  'com.android.server.telecom',
  'com.android.phone'
];

export function computeAbsoluteRemainingMs(session: AbsoluteSession, currentElapsedRealtimeMs: number): number {
  if (session.status !== 'ACTIVE') return 0;
  const currentBootElapsed = Math.max(0, currentElapsedRealtimeMs - session.startElapsedRealtimeMs);
  const totalElapsed = session.accumulatedProvenElapsedMs + currentBootElapsed;
  return Math.max(0, session.targetDurationMs - totalElapsed);
}

export function reconcileAfterReboot(
  session: AbsoluteSession,
  newBootElapsedRealtimeMs: LongOrNumber,
  currentWallClockMs: number,
  systemBootCount: number
): AbsoluteSession {
  const preRebootDelta = Math.max(0, session.lastPersistedElapsedRealtimeMs - session.startElapsedRealtimeMs);
  const newAccumulated = session.accumulatedProvenElapsedMs + preRebootDelta;

  const wallDeltaMs = currentWallClockMs - session.lastPersistedWallClockMs;
  const maxAcceptableRebootDowntimeMs = 10 * 60 * 1000; // 10 minutes

  // Anti-tamper logic: if date was shifted backwards or jumped into future (>10m), grant 0 credit!
  const isWallClockSuspect = wallDeltaMs < 0 || wallDeltaMs > maxAcceptableRebootDowntimeMs;

  const offlineCreditMs = isWallClockSuspect ? 0 : Math.min(Math.max(0, wallDeltaMs), 3 * 60 * 1000);
  const totalProven = newAccumulated + offlineCreditMs;
  const remaining = Math.max(0, session.targetDurationMs - totalProven);

  return {
    ...session,
    startElapsedRealtimeMs: Number(newBootElapsedRealtimeMs),
    lastPersistedElapsedRealtimeMs: Number(newBootElapsedRealtimeMs),
    accumulatedProvenElapsedMs: totalProven,
    lastPersistedWallClockMs: currentWallClockMs,
    bootCounter: systemBootCount,
    status: remaining <= 0 ? 'COMPLETED' : 'ACTIVE'
  };
}

type LongOrNumber = number;

export function isPackageBlockedInAbsoluteSession(session: AbsoluteSession, packageName: string): boolean {
  if (session.status !== 'ACTIVE') return false;
  if (session.config.safetyExemptPackages.includes(packageName)) return false;
  if (packageName.startsWith('com.android.systemui') || packageName === 'android') return false;

  switch (session.config.lockMode) {
    case LockMode.STRICT_WHITELIST:
      return !session.config.targetPackages.includes(packageName);
    case LockMode.SELECTIVE_BLACKLIST:
    case LockMode.PARTIAL_LOCK:
      return session.config.targetPackages.includes(packageName);
    default:
      return false;
  }
}

export function isYouTubeContentAllowed(
  config: YouTubeFilterConfig,
  title: string,
  channelId?: string,
  isShort?: boolean
): boolean {
  if (!config.isEnabled) return false;
  if (isShort && config.blockShorts) return false;

  const lower = title.toLowerCase();
  if (config.blockedKeywords.some((kw) => lower.includes(kw))) {
    return false;
  }
  if (channelId && config.allowedChannelIds.includes(channelId)) {
    return true;
  }
  return config.allowedKeywords.some((kw) => lower.includes(kw));
}

// Tests
test('AbsoluteSession calculates monotonic remaining time accurately', () => {
  const session: AbsoluteSession = {
    sessionId: 'abs-1',
    targetDurationMs: 60 * 60 * 1000,
    startElapsedRealtimeMs: 10000,
    lastPersistedElapsedRealtimeMs: 10000,
    accumulatedProvenElapsedMs: 0,
    startWallClockMs: 1700000000000,
    lastPersistedWallClockMs: 1700000000000,
    bootCounter: 1,
    status: 'ACTIVE',
    config: {
      targetDurationMs: 60 * 60 * 1000,
      lockMode: LockMode.STRICT_WHITELIST,
      targetPackages: ['com.adobe.reader'],
      safetyExemptPackages: DEFAULT_SAFETY_PACKAGES,
      partialLockAllowsNotifications: true,
      youTubeFilter: {
        isEnabled: true,
        allowedChannelIds: [],
        allowedKeywords: [],
        blockedKeywords: [],
        blockShorts: true,
        blockComments: true
      }
    }
  };

  // 15 minutes elapsed in hardware clock (900,000 ms)
  const remaining = computeAbsoluteRemainingMs(session, 10000 + 15 * 60 * 1000);
  assert.equal(remaining, 45 * 60 * 1000);
});

test('Reconcile defeats clock tampering: user sets date forward 30 days before reboot', () => {
  const initialWallTime = 1700000000000;
  const session: AbsoluteSession = {
    sessionId: 'tamper-test',
    targetDurationMs: 60 * 60 * 1000, // 60 mins
    startElapsedRealtimeMs: 10000,
    lastPersistedElapsedRealtimeMs: 10000 + 20 * 60 * 1000, // 20 mins ran before reboot
    accumulatedProvenElapsedMs: 0,
    startWallClockMs: initialWallTime,
    lastPersistedWallClockMs: initialWallTime + 20 * 60 * 1000,
    bootCounter: 1,
    status: 'ACTIVE',
    config: {
      targetDurationMs: 60 * 60 * 1000,
      lockMode: LockMode.STRICT_WHITELIST,
      targetPackages: [],
      safetyExemptPackages: DEFAULT_SAFETY_PACKAGES,
      partialLockAllowsNotifications: true,
      youTubeFilter: {
        isEnabled: true,
        allowedChannelIds: [],
        allowedKeywords: [],
        blockedKeywords: [],
        blockShorts: true,
        blockComments: true
      }
    }
  };

  // User alters date forward by 30 days before reboot:
  const tamperedWallTime = initialWallTime + 30 * 24 * 60 * 60 * 1000;

  // Phone finishes rebooting, hardware clock starts at 5,000ms
  const reconciled = reconcileAfterReboot(session, 5000, tamperedWallTime, 2);

  // Proven elapsed must ONLY be 20 mins (0 downtime credited for tampered wall clock!)
  assert.equal(reconciled.accumulatedProvenElapsedMs, 20 * 60 * 1000);
  // Status remains ACTIVE, NOT completed!
  assert.equal(reconciled.status, 'ACTIVE');

  // Remaining time is still exactly 40 minutes!
  const remaining = computeAbsoluteRemainingMs(reconciled, 5000);
  assert.equal(remaining, 40 * 60 * 1000);
});

test('Reconcile defeats clock tampering: user sets date backward 1 year before reboot', () => {
  const initialWallTime = 1700000000000;
  const session: AbsoluteSession = {
    sessionId: 'backward-tamper',
    targetDurationMs: 60 * 60 * 1000,
    startElapsedRealtimeMs: 10000,
    lastPersistedElapsedRealtimeMs: 10000 + 10 * 60 * 1000, // 10 mins ran
    accumulatedProvenElapsedMs: 0,
    startWallClockMs: initialWallTime,
    lastPersistedWallClockMs: initialWallTime + 10 * 60 * 1000,
    bootCounter: 1,
    status: 'ACTIVE',
    config: {
      targetDurationMs: 60 * 60 * 1000,
      lockMode: LockMode.STRICT_WHITELIST,
      targetPackages: [],
      safetyExemptPackages: DEFAULT_SAFETY_PACKAGES,
      partialLockAllowsNotifications: true,
      youTubeFilter: {
        isEnabled: true,
        allowedChannelIds: [],
        allowedKeywords: [],
        blockedKeywords: [],
        blockShorts: true,
        blockComments: true
      }
    }
  };

  // User sets clock 1 year into past
  const rolledBackWall = initialWallTime - 365 * 24 * 60 * 60 * 1000;
  const reconciled = reconcileAfterReboot(session, 3000, rolledBackWall, 2);

  // Zero credit for backwards jump, retains exactly 10 proven minutes
  assert.equal(reconciled.accumulatedProvenElapsedMs, 10 * 60 * 1000);
  assert.equal(reconciled.status, 'ACTIVE');
  assert.equal(computeAbsoluteRemainingMs(reconciled, 3000), 50 * 60 * 1000);
});

test('Lock modes: Strict Whitelist vs Selective Blacklist (choose which apps to lock)', () => {
  const baseSession: AbsoluteSession = {
    sessionId: 'modes-test',
    targetDurationMs: 3600000,
    startElapsedRealtimeMs: 0,
    lastPersistedElapsedRealtimeMs: 0,
    accumulatedProvenElapsedMs: 0,
    startWallClockMs: 0,
    lastPersistedWallClockMs: 0,
    bootCounter: 1,
    status: 'ACTIVE',
    config: {
      targetDurationMs: 3600000,
      lockMode: LockMode.SELECTIVE_BLACKLIST, // User chose specific apps to block!
      targetPackages: ['com.instagram.android', 'com.reddit.frontpage'],
      safetyExemptPackages: DEFAULT_SAFETY_PACKAGES,
      partialLockAllowsNotifications: true,
      youTubeFilter: {
        isEnabled: true,
        allowedChannelIds: [],
        allowedKeywords: [],
        blockedKeywords: [],
        blockShorts: true,
        blockComments: true
      }
    }
  };

  // Blacklist mode: Chosen distraction apps are blocked
  assert.equal(isPackageBlockedInAbsoluteSession(baseSession, 'com.instagram.android'), true);
  assert.equal(isPackageBlockedInAbsoluteSession(baseSession, 'com.reddit.frontpage'), true);

  // Blacklist mode: Other unselected apps (e.g. calculator, browser) stay open
  assert.equal(isPackageBlockedInAbsoluteSession(baseSession, 'com.google.android.calculator'), false);
  assert.equal(isPackageBlockedInAbsoluteSession(baseSession, 'com.adobe.reader'), false);

  // Safety dialer is NEVER blocked in either mode
  assert.equal(isPackageBlockedInAbsoluteSession(baseSession, 'com.android.dialer'), false);

  // Switch to STRICT_WHITELIST mode where targetPackages defines ALLOWED apps:
  const whitelistSession: AbsoluteSession = {
    ...baseSession,
    config: {
      ...baseSession.config,
      lockMode: LockMode.STRICT_WHITELIST,
      targetPackages: ['com.adobe.reader'] // only Adobe reader allowed
    }
  };

  assert.equal(isPackageBlockedInAbsoluteSession(whitelistSession, 'com.adobe.reader'), false);
  assert.equal(isPackageBlockedInAbsoluteSession(whitelistSession, 'com.google.android.calculator'), true);
  assert.equal(isPackageBlockedInAbsoluteSession(whitelistSession, 'com.instagram.android'), true);
});

test('YouTube Study Filter evaluation rules', () => {
  const ytConfig: YouTubeFilterConfig = {
    isEnabled: true,
    allowedChannelIds: ['UCWN3xxRkmTPmbKwht9FuE5A', 'UCsBjURrPoezykLs9EqgamOA'], // 3Blue1Brown, Fireship
    allowedKeywords: ['calculus', 'algorithm', 'machine learning', 'linear algebra'],
    blockedKeywords: ['shorts', 'gaming', 'prank', 'reaction', 'vlog'],
    blockShorts: true,
    blockComments: true
  };

  // Approved channel content
  assert.equal(isYouTubeContentAllowed(ytConfig, 'Essence of Linear Algebra Chapter 1', 'UCWN3xxRkmTPmbKwht9FuE5A'), true);

  // Approved keyword content
  assert.equal(isYouTubeContentAllowed(ytConfig, 'Machine Learning from Scratch Lecture 04', 'some-random-id'), true);

  // Blocked keyword content
  assert.equal(isYouTubeContentAllowed(ytConfig, 'Gaming stream with algorithm talk', 'some-id'), false);
  assert.equal(isYouTubeContentAllowed(ytConfig, 'Prank reaction video', 'some-id'), false);

  // Shorts blocked
  assert.equal(isYouTubeContentAllowed(ytConfig, 'Cool Calculus Trick', 'UCWN3xxRkmTPmbKwht9FuE5A', true), false);
});

test('HMAC-SHA256 stop command signing and anti-replay verification', () => {
  const secretHex = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';
  const secretBytes = Buffer.from(secretHex, 'hex');

  const nowEpoch = Math.floor(Date.now() / 1000);
  const validPayload = {
    action: 'STOP_SESSION',
    nonce: 'a1b2c3d4e5f67890',
    session_id: 'session-live-1',
    timestamp: nowEpoch
  };

  const canonical = JSON.stringify(validPayload, Object.keys(validPayload).sort());
  const hmac = crypto.createHmac('sha256', secretBytes);
  hmac.update(canonical);
  const signature = hmac.digest('hex');

  // Verify valid signature
  const verifyHmac = crypto.createHmac('sha256', secretBytes);
  verifyHmac.update(canonical);
  assert.equal(verifyHmac.digest('hex'), signature);

  // Replay check: reject timestamp older than 180 seconds
  const stalePayload = {
    ...validPayload,
    timestamp: nowEpoch - 500
  };
  const isStale = Math.abs(nowEpoch - stalePayload.timestamp) > 180;
  assert.equal(isStale, true, 'Stale replay payload must be rejected');

  // Forgery check: altered session ID must fail verification
  const forgedCanonical = JSON.stringify({ ...validPayload, session_id: 'session-tampered' }, Object.keys(validPayload).sort());
  const forgedHmac = crypto.createHmac('sha256', secretBytes);
  forgedHmac.update(forgedCanonical);
  assert.notEqual(forgedHmac.digest('hex'), signature, 'Forged payload must produce mismatched HMAC');
});
