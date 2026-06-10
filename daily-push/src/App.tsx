/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from "react";
import { 
  motion, 
  AnimatePresence 
} from "motion/react";
import { 
  Cpu, 
  Download, 
  Upload, 
  AlertTriangle, 
  TrendingUp, 
  Zap,
  ShieldCheck,
  Calendar,
  Sparkles,
  Cloud,
  CloudLightning,
  Settings,
  RefreshCw,
  LogOut
} from "lucide-react";
import { 
  ResponsiveContainer, 
  LineChart, 
  Line, 
  XAxis, 
  YAxis, 
  Tooltip, 
  CartesianGrid 
} from "recharts";

import { WorkoutDay, LogEntry } from "./types";

// --- GOOGLE WORKSPACE CLIENT CONFIGURATION ---
// Paste your Google OAuth 2.0 Client ID here to lock it permanently into your tracker code.
// This ensures that even if you clear your browser cache or visit the app on a different device,
// you can connect your Google Drive with a single click without having to enter anything!
const DEFAULT_GOOGLE_CLIENT_ID = "363694431662-rmt5fjbvik4dogimij7papln804ec315.apps.googleusercontent.com"; // Placed holder / preset for user to utilize directly or modify.

export default function App() {
  // Core state metrics
  const [dataFrame, setDataFrame] = useState<WorkoutDay[]>([]);
  const [activeDate, setActiveDate] = useState<string>("");
  const [formBadgeStatus, setFormBadgeStatus] = useState<"NEW_ENTRY" | "MUTATE_RECORD">("NEW_ENTRY");
  const [storageType, setStorageType] = useState<"OFFLINE_OPFS" | "LOCAL_STORAGE">("OFFLINE_OPFS");
  const [rollingRangeWindow, setRollingRangeWindow] = useState<14 | 30>(30);

  // Google Drive Sync States
  const [gdAccessToken, setGdAccessToken] = useState<string | null>(null);
  const [gdUser, setGdUser] = useState<{ displayName: string; emailAddress: string; photoLink?: string } | null>(null);
  const [isSyncing, setIsSyncing] = useState<boolean>(false);
  const [lastGdSyncTime, setLastGdSyncTime] = useState<string | null>(() => localStorage.getItem("last_gd_sync_time"));
  const [customClientId, setCustomClientId] = useState<string>(() => localStorage.getItem("gd_custom_client_id") || "");
  const [isConfigOpen, setIsConfigOpen] = useState<boolean>(false);

  // Form states
  const [p1, setP1] = useState<string>("");
  const [p2, setP2] = useState<string>("");
  const [p3, setP3] = useState<string>("");
  const [c1, setC1] = useState<string>("");
  const [c2, setC2] = useState<string>("");
  const [c3, setC3] = useState<string>("");

  // Terminal Console logging buffer
  const [consoleLogs, setConsoleLogs] = useState<LogEntry[]>([]);
  const consoleBufferRef = useRef<HTMLDivElement>(null);

  // UI Toast State
  const [toast, setToast] = useState<{ message: string; success: boolean } | null>(null);

  // --- GOOGLE DRIVE SYNC PIPELINES ---
  const getGoogleClientId = () => {
    return customClientId.trim() || DEFAULT_GOOGLE_CLIENT_ID.trim() || (import.meta as any).env.VITE_GOOGLE_CLIENT_ID || "";
  };

  const getGoogleRedirectUri = () => {
    const origin = window.location.origin;
    const pathname = window.location.pathname;
    
    // Check if we are running in the personal-repo sub-directory (GitHub Pages)
    if (pathname.includes("/personal-repo/daily-push")) {
      return `${origin}/personal-repo/daily-push/oauth-callback.html`;
    }
    
    // Check for any general subfolder implementation
    if (pathname.includes("/daily-push")) {
      const idx = pathname.indexOf("/daily-push");
      const partBefore = pathname.substring(0, idx);
      return `${origin}${partBefore}/daily-push/oauth-callback.html`;
    }
    
    // Otherwise fallback to dynamic path detection
    let basePath = "/";
    const lastSlash = pathname.lastIndexOf("/");
    if (lastSlash > 0) {
      basePath = pathname.endsWith("/") ? pathname : pathname.substring(0, lastSlash + 1);
    }
    return `${origin}${basePath}oauth-callback.html`;
  };

  const handleGoogleAuth = () => {
    const cid = getGoogleClientId();
    if (!cid) {
      setIsConfigOpen(true);
      writeLog("Setup required: Google Drive Client ID is missing. Configure in settings.", true);
      triggerToast("Configure Client ID first", false);
      return;
    }

    const redirectUri = getGoogleRedirectUri();
    const scope = "https://www.googleapis.com/auth/drive.appdata";
    const authUrl = `https://accounts.google.com/o/oauth2/v2/auth` +
      `?client_id=${encodeURIComponent(cid)}` +
      `&redirect_uri=${encodeURIComponent(redirectUri)}` +
      `&response_type=token` +
      `&scope=${encodeURIComponent(scope)}`;

    writeLog("Initiating Google Drive authorization protocol (appdata sandbox)...");
    const width = 500;
    const height = 650;
    const left = window.screenX + (window.innerWidth - width) / 2;
    const top = window.screenY + (window.innerHeight - height) / 2;
    
    window.open(
      authUrl,
      "google_drive_oauth",
      `width=${width},height=${height},left=${left},top=${top},status=no,resizable=yes`
    );
  };

  const handleGoogleDisconnect = () => {
    setGdAccessToken(null);
    setGdUser(null);
    writeLog("Google Drive replication channel disconnected.");
    triggerToast("Disconnected from Drive");
  };

  const fetchDriveUserAndSync = async (token: string) => {
    setIsSyncing(true);
    try {
      writeLog("Requesting Google Drive authorization endpoint profile details...");
      const aboutRes = await fetch("https://www.googleapis.com/drive/v3/about?fields=user", {
        headers: { Authorization: `Bearer ${token}` }
      });
      
      if (!aboutRes.ok) {
        throw new Error(`User info fetch failed: ${aboutRes.status}`);
      }
      
      const aboutData = await aboutRes.json();
      if (aboutData.user) {
        setGdUser({
          displayName: aboutData.user.displayName,
          emailAddress: aboutData.user.emailAddress,
          photoLink: aboutData.user.photoLink
        });
        writeLog(`Secure Drive connection linked to address: ${aboutData.user.emailAddress}`);
      }

      await executeDriveSync(token);
    } catch (err: any) {
      writeLog(`Authentication Handshake failed: ${err.message}`, true);
      triggerToast("Drive sync failure", false);
    } finally {
      setIsSyncing(false);
    }
  };

  const executeDriveSync = async (token: string) => {
    try {
      writeLog("Querying Google Drive appDataFolder workspace...");
      const searchRes = await fetch(
        "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='workout_data.json'&fields=files(id,name,modifiedTime)",
        { headers: { Authorization: `Bearer ${token}` } }
      );
      
      if (!searchRes.ok) {
        throw new Error(`Drive index lookup failed code: ${searchRes.status}`);
      }
      
      const searchData = await searchRes.json();
      const files = searchData.files || [];
      let driveFileId = files.length > 0 ? files[0].id : null;
      let driveData: WorkoutDay[] = [];
      
      if (driveFileId) {
        writeLog(`Database replica found in cloud storage. Importing payload [ID: ${driveFileId}]...`);
        const downloadRes = await fetch(
          `https://www.googleapis.com/drive/v3/files/${driveFileId}?alt=media`,
          { headers: { Authorization: `Bearer ${token}` } }
        );
        
        if (downloadRes.ok) {
          try {
            const rawText = await downloadRes.text();
            if (rawText.trim().length > 0) {
              driveData = JSON.parse(rawText);
              writeLog(`Downloaded ${driveData.length} records from Google Drive.`);
            }
          } catch (parseError) {
            writeLog("Warning: Cloud backup file contains illegal JSON formats.", true);
          }
        }
      }

      // Read current local state
      const refreshedLocal = await loadVaultData();
      
      // Perform bidirectional chronological entry merging
      const mergedMap = new Map<string, WorkoutDay>();
      
      driveData.forEach((day) => {
        if (day.date) mergedMap.set(day.date, day);
      });
      
      const getSum = (day: WorkoutDay) => {
        const pSum = (day.p as number[] || [0, 0, 0]).reduce((acc: number, val: number) => acc + val, 0);
        const cSum = (day.c as number[] || [0, 0, 0]).reduce((acc: number, val: number) => acc + val, 0);
        return pSum + cSum;
      };

      refreshedLocal.forEach((day) => {
        const existing = mergedMap.get(day.date);
        if (existing) {
          const driveRepSum = getSum(existing);
          const localRepSum = getSum(day);
          if (localRepSum >= driveRepSum) {
            mergedMap.set(day.date, day);
          }
        } else {
          mergedMap.set(day.date, day);
        }
      });

      const finalMergedList = Array.from(mergedMap.values()).sort(
        (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime()
      );

      setDataFrame(finalMergedList);
      await commitToVault(finalMergedList);
      
      // Write / Update the files on Google Drive
      const fileContent = JSON.stringify(finalMergedList, null, 2);
      const fileBlob = new Blob([fileContent], { type: "application/json" });
      
      if (driveFileId) {
        writeLog("Uploading synchronized tracking history to cloud database...");
        const updateRes = await fetch(
          `https://www.googleapis.com/upload/drive/v3/files/${driveFileId}?uploadType=media`,
          {
            method: "PATCH",
            headers: {
              Authorization: `Bearer ${token}`,
              "Content-Type": "application/json"
            },
            body: fileContent
          }
        );
        
        if (!updateRes.ok) {
          throw new Error(`Sync upload transaction aborted code: ${updateRes.status}`);
        }
      } else {
        writeLog("Carving new application storage cell inside user Google Drive AppData folder...");
        const metadata = {
          name: "workout_data.json",
          parents: ["appDataFolder"]
        };
        
        const form = new FormData();
        form.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));
        form.append("file", fileBlob);

        const createRes = await fetch(
          "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
          {
            method: "POST",
            headers: { Authorization: `Bearer ${token}` },
            body: form
          }
        );
        
        if (!createRes.ok) {
          throw new Error(`Cloud document allocation aborted: ${createRes.status}`);
        }
        
        const createData = await createRes.json();
        writeLog(`Initial cloud allocation achieved (File ID: ${createData.id})`);
      }

      const now = new Date().toLocaleTimeString();
      setLastGdSyncTime(now);
      localStorage.setItem("last_gd_sync_time", now);
      writeLog(`Google Drive Sync complete. Successfully mirrored ${finalMergedList.length} unified work entries.`);
      triggerToast("Drive synced successfully!");
    } catch (e: any) {
      writeLog(`Cloud replication error: ${e.message}`, true);
      triggerToast("Drive synchronization failed", false);
    }
  };

  const handleManualDriveSync = async () => {
    if (!gdAccessToken) {
      handleGoogleAuth();
      return;
    }
    
    const confirmed = window.confirm(
      "Bidirectional Cloud Sync:\n\nProceed with matching your local tracker ledger with Google Drive? Any same-day sets will be automatically resolved by picking the workout with more reps."
    );
    if (!confirmed) {
      writeLog("Grid synchronization aborted by user.");
      return;
    }

    setIsSyncing(true);
    await executeDriveSync(gdAccessToken);
    setIsSyncing(false);
  };

  // Listen to secure postMessage login redirects
  useEffect(() => {
    const handleOAuthMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;
      
      if (event.data?.type === "GOOGLE_DRIVE_AUTH_SUCCESS") {
        const { accessToken } = event.data;
        setGdAccessToken(accessToken);
        writeLog("Secure Google authenticator credential block downloaded successfully.");
        triggerToast("Connected to Drive");
        fetchDriveUserAndSync(accessToken);
      } else if (event.data?.type === "GOOGLE_DRIVE_AUTH_FAILURE") {
        const error = event.data?.error || "User closed dialog";
        writeLog(`Google Drive connection aborted: ${error}`, true);
        triggerToast("Drive connection failed", false);
      }
    };

    window.addEventListener("message", handleOAuthMessage);
    return () => window.removeEventListener("message", handleOAuthMessage);
  }, [dataFrame]);

  // --- LOG WRITING ---
  const writeLog = (message: string, isError = false) => {
    const newEntry: LogEntry = {
      id: Math.random().toString(36).substring(2, 9),
      timestamp: new Date().toLocaleTimeString(),
      message,
      isError
    };
    setConsoleLogs((prev) => [...prev, newEntry]);
  };

  // Toast Trigger Helper
  const triggerToast = (message: string, success = true) => {
    setToast({ message, success });
    setTimeout(() => {
      setToast(null);
    }, 3000);
  };

  // --- STORAGE INTEGRATION (OPFS with LocalStorage Fallback) ---
  const loadVaultData = async () => {
    try {
      if (typeof navigator.storage?.getDirectory === "function") {
        const root = await navigator.storage.getDirectory();
        const handle = await root.getFileHandle("workout_data.json", { create: true });
        const file = await handle.getFile();
        const content = await file.text();

        if (content.trim().length > 0) {
          const parsed: WorkoutDay[] = JSON.parse(content);
          const sorted = parsed.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
          setDataFrame(sorted);
          writeLog(`Local disk storage synced. Detected ${sorted.length} records.`);
          setStorageType("OFFLINE_OPFS");
          return sorted;
        } else {
          writeLog("Local storage initialized. Ready to record reps.");
          setStorageType("OFFLINE_OPFS");
          return [];
        }
      } else {
        throw new Error("High-speed local disk storage not supported in browser.");
      }
    } catch (err: any) {
      writeLog(`Storage fallback: ${err.message}. Loading browser local database.`, false);
      setStorageType("LOCAL_STORAGE");
      
      const localData = localStorage.getItem("workout_data.json");
      if (localData) {
        try {
          const parsed: WorkoutDay[] = JSON.parse(localData);
          const sorted = parsed.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
          setDataFrame(sorted);
          writeLog(`Backup database synced. Loaded ${sorted.length} records.`);
          return sorted;
        } catch (parseErr) {
          writeLog("Failed to parse fallback database.", true);
        }
      } else {
        writeLog("Backup database initialized. Ready to record reps.");
      }
      return [];
    }
  };

  const commitToVault = async (data: WorkoutDay[]) => {
    try {
      if (storageType === "OFFLINE_OPFS" && typeof navigator.storage?.getDirectory === "function") {
        const root = await navigator.storage.getDirectory();
        const handle = await root.getFileHandle("workout_data.json", { create: true });
        const writable = await handle.createWritable();
        await writable.write(JSON.stringify(data, null, 2));
        await writable.close();
        writeLog("Workout reps successfully saved to local storage.");
      } else {
        localStorage.setItem("workout_data.json", JSON.stringify(data, null, 2));
        writeLog("Workout reps saved to local memory database.");
      }
    } catch (err: any) {
      writeLog(`Disk write error: ${err.message}. Fallback write triggered.`, true);
      localStorage.setItem("workout_data.json", JSON.stringify(data, null, 2));
    }
  };

  // --- INITIALIZATION ---
  useEffect(() => {
    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, "0");
    const dd = String(today.getDate()).padStart(2, "0");
    const formattedDate = `${yyyy}-${mm}-${dd}`;
    setActiveDate(formattedDate);
    
    loadVaultData();
  }, []);

  // Scroll logging buffer
  useEffect(() => {
    if (consoleBufferRef.current) {
      consoleBufferRef.current.scrollTop = consoleBufferRef.current.scrollHeight;
    }
  }, [consoleLogs]);

  // --- DYNAMIC FORM INJECTION ---
  useEffect(() => {
    if (!activeDate) return;

    const match = dataFrame.find((d) => d.date === activeDate);
    if (match) {
      setP1(match.p[0] === 0 ? "" : String(match.p[0]));
      setP2(match.p[1] === 0 ? "" : String(match.p[1]));
      setP3(match.p[2] === 0 ? "" : String(match.p[2]));
      setC1(match.c[0] === 0 ? "" : String(match.c[0]));
      setC2(match.c[1] === 0 ? "" : String(match.c[1]));
      setC3(match.c[2] === 0 ? "" : String(match.c[2]));
      setFormBadgeStatus("MUTATE_RECORD");
    } else {
      setP1(""); setP2(""); setP3("");
      setC1(""); setC2(""); setC3("");
      setFormBadgeStatus("NEW_ENTRY");
    }
  }, [activeDate, dataFrame]);

  // --- DATE STEPS ---
  const stepDate = (days: number) => {
    if (!activeDate) return;
    const [year, month, day] = activeDate.split("-").map(Number);
    const dateObj = new Date(year, month - 1, day);
    dateObj.setDate(dateObj.getDate() + days);

    const yyyy = dateObj.getFullYear();
    const mm = String(dateObj.getMonth() + 1).padStart(2, "0");
    const dd = String(dateObj.getDate()).padStart(2, "0");
    const nextDate = `${yyyy}-${mm}-${dd}`;

    setActiveDate(nextDate);
    writeLog(`Stepped active work-date to: [${nextDate}]`);
  };

  // --- SUBMIT WORKOUT COMMITS ---
  const handleLogSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeDate) return;

    const pushupsArray: [number, number, number] = [
      parseInt(p1) || 0,
      parseInt(p2) || 0,
      parseInt(p3) || 0
    ];

    const crunchesArray: [number, number, number] = [
      parseInt(c1) || 0,
      parseInt(c2) || 0,
      parseInt(c3) || 0
    ];

    const newPayload: WorkoutDay = {
      date: activeDate,
      p: pushupsArray,
      c: crunchesArray
    };

    let updatedDataFrame = [...dataFrame];
    const matchIdx = updatedDataFrame.findIndex((d) => d.date === activeDate);

    if (matchIdx > -1) {
      updatedDataFrame[matchIdx] = newPayload;
      writeLog(`Record updated at active index of [${activeDate}].`);
    } else {
      updatedDataFrame.push(newPayload);
      writeLog(`New session record appended under chronology [${activeDate}].`);
    }

    updatedDataFrame.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
    setDataFrame(updatedDataFrame);
    
    // Commit locally instantly
    await commitToVault(updatedDataFrame);
    triggerToast("Saved successfully!");

    // Auto-push copy to Google Drive dynamically if authorized
    if (gdAccessToken) {
      executeDriveSync(gdAccessToken);
    }
  };

  // --- EXPORT LOCAL LEDGER ---
  const triggerManualExport = () => {
    try {
      const stamp = new Date().toISOString().split("T")[0];
      const filename = `dailypush_backup_${stamp}.json`;
      const blob = new Blob([JSON.stringify(dataFrame, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      URL.revokeObjectURL(url);
      
      writeLog(`Offline JSON ledger successfully compiled and compiled: ${filename}`);
      triggerToast("File backup downloaded.");
    } catch (err: any) {
      writeLog(`Backup compilation aborted: ${err.message}`, true);
    }
  };

  // --- SEED LOCAL LEDGER ---
  const importBackupFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      const text = await file.text();
      const parsed = JSON.parse(text);

      if (Array.isArray(parsed)) {
        const validated = parsed.filter((item: any) => {
          return item && typeof item.date === "string" && Array.isArray(item.p) && Array.isArray(item.c);
        }) as WorkoutDay[];

        if (validated.length === 0) {
          throw new Error("Invalid file content schema.");
        }

        const sorted = validated.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        setDataFrame(sorted);
        await commitToVault(sorted);
        writeLog(`Ledger restoration successful. Loaded ${sorted.length} historical records.`);
        triggerToast("Seeded successfully!");
      } else {
        throw new Error("Target file is not a valid JSON tracking array.");
      }
    } catch (err: any) {
      writeLog(`Seeding failed: ${err.message}`, true);
      triggerToast("Failed to parse seed.", false);
    }
    e.target.value = "";
  };

  // --- DATA GRAPHING CALCULATOR ---
  const calculateKPIs = () => {
    let grossPushups = 0;
    let grossCrunches = 0;
    let activeDays = 0;

    dataFrame.forEach((day) => {
      const pSum = day.p.reduce((acc, val) => acc + val, 0);
      const cSum = day.c.reduce((acc, val) => acc + val, 0);
      
      grossPushups += pSum;
      grossCrunches += cSum;
      if (pSum + cSum > 0) {
        activeDays++;
      }
    });

    const avgPushups = activeDays ? Math.round(grossPushups / activeDays) : 0;
    const avgCrunches = activeDays ? Math.round(grossCrunches / activeDays) : 0;

    return {
      grossPushups: grossPushups.toLocaleString(),
      grossCrunches: grossCrunches.toLocaleString(),
      avgPushups,
      avgCrunches
    };
  };

  const kpis = calculateKPIs();

  const getChartData = () => {
    const subset = dataFrame.slice(-rollingRangeWindow);
    return subset.map((day) => {
      return {
        date: day.date.substring(5), // mm-dd formatting
        pushups: day.p.reduce((acc, val) => acc + val, 0),
        crunches: day.c.reduce((acc, val) => acc + val, 0),
        rawDate: day.date
      };
    });
  };

  const chartData = getChartData();

  const copyLogsToClipboard = () => {
    const rawLogs = consoleLogs.map((entry) => `[${entry.timestamp}] ${entry.message}`).join("\n");
    navigator.clipboard.writeText(rawLogs)
      .then(() => {
        triggerToast("Logs copied.");
        writeLog("Diagnostic console copied successfully.");
      })
      .catch((err) => {
        writeLog(`Failed to copy logs: ${err.message}`, true);
      });
  };

  return (
    <div className="min-h-screen bg-[#02040a] text-slate-100 selection:bg-emerald-500/35 selection:text-white glow-radial select-none pb-12 font-sans overflow-x-hidden">
      
      {/* Background radial ambient soft light */}
      <div className="fixed top-0 left-0 right-0 h-[500px] bg-gradient-to-b from-emerald-500/5 via-teal-550/1 to-transparent pointer-events-none z-0" />

      {/* Toast Alert Systems */}
      <AnimatePresence>
        {toast && (
          <motion.div
            initial={{ opacity: 0, y: 30, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            className={`fixed bottom-6 right-6 z-50 p-4 rounded-xl border font-mono text-[11px] text-white flex items-center gap-2.5 backdrop-blur-md shadow-2xl ${
              toast.success 
                ? "border-emerald-500/20 bg-emerald-950/50 text-emerald-300" 
                : "border-red-500/20 bg-red-950/50 text-red-300"
            }`}
          >
            <div className={`h-1.5 w-1.5 rounded-full animate-pulse ${toast.success ? "bg-emerald-400" : "bg-red-400"}`} />
            <span>{toast.message}</span>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Primary Navigation / Utility Bar */}
      <header className="sticky top-0 z-40 bg-[#02040a]/80 backdrop-blur-xl border-b border-white/5 px-4 sm:px-6 py-3.5 sm:py-5 flex items-center justify-between">
        <div className="flex items-center gap-2 sm:gap-3">
          <div className="h-7 w-7 sm:h-8 sm:w-8 rounded-lg bg-gradient-to-tr from-emerald-500 to-teal-400 flex items-center justify-center text-slate-950 font-black shadow-lg shadow-emerald-500/10">
            <Zap size={13} className="fill-slate-950 stroke-none" />
          </div>
          <div>
            <h1 className="text-[12px] sm:text-[13px] font-bold tracking-widest text-white uppercase font-sans">DAILY PUSH</h1>
            <p className="text-[8px] sm:text-[9px] font-mono text-emerald-400 tracking-wider uppercase font-medium flex items-center gap-1">
              <span className="h-1 w-1 bg-current rounded-full" />
              {storageType === "OFFLINE_OPFS" ? "DISK DATABASE" : "MEMORY DATABASE"}
            </p>
          </div>
        </div>
        
        <div className="flex items-center gap-1.5 sm:gap-2">
          <div className="flex items-center gap-1 border border-white/5 bg-slate-950/60 text-slate-400 text-[9px] sm:text-[10px] px-2 py-1.5 sm:px-3 sm:py-2 rounded-xl">
            <ShieldCheck size={11} className="text-emerald-400" />
            <span className="hidden xs:inline">OFFLINE</span>
          </div>

          <button 
            type="button"
            onClick={triggerManualExport} 
            className="bg-slate-950 border border-white/5 hover:bg-slate-900 text-slate-350 text-[9px] sm:text-[10px] px-2.5 py-1.5 sm:px-3 sm:py-2 rounded-xl flex items-center gap-1 cursor-pointer font-bold transition-all"
          >
            <Download size={10} />
            EXPORT
          </button>
          
          <label 
            htmlFor="import-file" 
            className="bg-slate-900 hover:bg-slate-800 border border-white/5 text-slate-350 text-[9px] sm:text-[10px] font-bold px-3 py-1.5 sm:px-4 sm:py-2 rounded-xl cursor-pointer transition-all flex items-center gap-1"
          >
            <Upload size={10} />
            SEED
          </label>
          <input 
            type="file" 
            id="import-file" 
            accept=".json" 
            className="hidden" 
            onChange={importBackupFile}
          />
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 mt-6 space-y-4 relative z-10">

        {/* Google Drive Balance / Cloud Synchronization System */}
        <div className="bg-slate-950/40 border border-white/5 p-4 sm:p-5 rounded-2xl backdrop-blur-md relative overflow-hidden">
          <div className="absolute top-0 left-0 right-0 h-[1.5px] bg-gradient-to-r from-transparent via-cyan-500/15 to-transparent" />
          
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-white/5 pb-3">
            <div className="flex items-center gap-2.5">
              <div className={`h-8 w-8 rounded-lg flex items-center justify-center transition-all ${
                gdAccessToken 
                  ? "bg-cyan-500/15 text-cyan-400 border border-cyan-500/25" 
                  : "bg-slate-900 text-slate-500 border border-white/5"
              }`}>
                <Cloud size={16} className={isSyncing ? "animate-pulse" : ""} />
              </div>
              <div>
                <h3 className="text-xs font-bold uppercase tracking-widest text-white flex items-center gap-1.5 font-sans">
                  Google Drive Cloud Sync
                </h3>
                <p className="text-[9px] font-mono text-slate-500 tracking-wide uppercase">
                  {gdAccessToken 
                    ? `Status: Connected as ${gdUser?.displayName || "User"}` 
                    : "Status: Local Tracking Mode (Cloud Synced Backlog #3)"}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2 self-end sm:self-auto">
              <button
                type="button"
                onClick={() => setIsConfigOpen((p) => !p)}
                className="p-2 rounded-xl bg-slate-900 hover:bg-slate-800 border border-white/5 text-slate-400 transition-all cursor-pointer"
                title="Configure OAuth Client ID"
              >
                <Settings size={14} className={isConfigOpen ? "text-cyan-400" : ""} />
              </button>

              {gdAccessToken ? (
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={handleManualDriveSync}
                    disabled={isSyncing}
                    className="bg-cyan-500/15 text-cyan-300 hover:bg-cyan-500/25 border border-cyan-500/20 text-[10px] sm:text-[11px] font-bold px-3 py-2 rounded-xl flex items-center gap-1.5 cursor-pointer transition-all disabled:opacity-50 font-mono"
                  >
                    <RefreshCw size={11} className={isSyncing ? "animate-spin" : ""} />
                    SYNC NOW
                  </button>
                  <button
                    type="button"
                    onClick={handleGoogleDisconnect}
                    className="bg-red-500/10 text-red-400 hover:bg-red-500/20 border border-red-500/15 p-2 rounded-xl cursor-pointer transition-all"
                    title="Disconnect Google Account"
                  >
                    <LogOut size={12} />
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={handleGoogleAuth}
                  disabled={isSyncing}
                  className="bg-slate-50 text-slate-950 hover:bg-slate-200 text-[10px] sm:text-[11px] font-extrabold px-3.5 py-2 rounded-xl flex items-center gap-1.5 cursor-pointer transition-all disabled:opacity-50"
                >
                  <CloudLightning size={11} className="fill-current" />
                  CONNECT DRIVE
                </button>
              )}
            </div>
          </div>

          {/* Config Setup Dropdown / Panel */}
          <AnimatePresence>
            {isConfigOpen && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: "auto", opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                className="overflow-hidden mt-3"
              >
                <div className="bg-black/40 border border-white/5 p-3 sm:p-4 rounded-xl space-y-3 font-sans text-xs">
                  <div className="flex items-center justify-between border-b border-white/5 pb-2 mb-1">
                    <span className="font-bold text-[10px] tracking-wider uppercase text-slate-400 font-mono flex items-center gap-1">
                      <Settings size={12} className="text-cyan-400" />
                      OAuth Credentials Configuration
                    </span>
                    <button 
                      onClick={() => setIsConfigOpen(false)}
                      className="text-[10px] font-mono text-slate-500 hover:text-slate-350"
                    >
                      [CLOSE]
                    </button>
                  </div>

                  <div className="space-y-1.5 font-mono">
                    <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-450">
                      Google OAuth Client ID
                    </label>
                    <input 
                      type="text"
                      placeholder={DEFAULT_GOOGLE_CLIENT_ID ? `Using preset ending in ...${DEFAULT_GOOGLE_CLIENT_ID.slice(-15)}` : "Paste your Google OAuth Client ID here..."}
                      value={customClientId}
                      onChange={(e) => {
                        setCustomClientId(e.target.value);
                        localStorage.setItem("gd_custom_client_id", e.target.value);
                      }}
                      className="w-full bg-slate-950 border border-white/5 focus:border-cyan-500/30 rounded-xl p-2.5 text-white font-mono text-[11px] focus:outline-none transition-all"
                    />
                    <p className="text-[9px] text-slate-500 leading-normal">
                      {customClientId.trim() ? (
                        <span className="text-amber-400 font-bold">⚠️ Custom Client ID override in effect. Clear this input to revert to the built-in workspace preset.</span>
                      ) : DEFAULT_GOOGLE_CLIENT_ID ? (
                        <span className="text-emerald-400 font-bold">✅ Workspace preset Client ID active ({DEFAULT_GOOGLE_CLIENT_ID.substring(0, 12)}...). Hands-free connect is ready!</span>
                      ) : (
                        <span>Paste an OAuth Client ID from your Google Cloud Credentials console.</span>
                      )}
                    </p>
                  </div>

                  {/* Connection Steps Helper */}
                  <div className="space-y-2 border-t border-white/5 pt-2.5">
                    <p className="font-bold text-[10px] tracking-wider uppercase text-slate-400 font-mono">
                      📋 Google Cloud Console Instructions
                    </p>
                    <ol className="list-decimal list-inside space-y-1.5 text-[10.5px] text-slate-400 leading-relaxed font-mono">
                      <li>
                        Go to the <a href="https://console.cloud.google.com/apis/credentials" target="_blank" rel="noopener noreferrer" className="text-cyan-400 underline hover:text-cyan-300">Google Cloud Credentials Console</a>.
                      </li>
                      <li>
                        Create an <strong>OAuth client ID</strong> of application type <strong>Web application</strong>.
                      </li>
                      <li>
                        Under <strong>Authorized JavaScript origins</strong>, add:
                        <div className="mt-1 bg-slate-950 p-1.5 rounded border border-white/5 select-text lowercase text-[9px] text-cyan-300 overflow-x-auto">
                          {window.location.origin}
                        </div>
                      </li>
                      <li>
                        Under <strong>Authorized redirect URIs</strong>, add:
                        <div className="mt-1 bg-slate-950 p-1.5 rounded border border-white/5 select-text lowercase text-[9px] text-cyan-300 overflow-x-auto">
                          {getGoogleRedirectUri()}
                        </div>
                      </li>
                      <li>
                        Copy the client ID, paste it into the field above, and click "CONNECT DRIVE" inside the dashboard to activate sync.
                      </li>
                    </ol>
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Sync Stats Info Banner */}
          {lastGdSyncTime && (
            <div className="mt-2.5 flex items-center justify-between text-[9px] text-slate-400 font-mono bg-black/15 border border-white/5 px-2.5 py-1.5 rounded-xl">
              <span className="flex items-center gap-1">
                <span className="h-1 w-1 bg-cyan-400 rounded-full animate-ping" />
                LAST CLOUD DATABASE REPLICATION:
              </span>
              <span className="text-cyan-400 font-bold uppercase">{lastGdSyncTime}</span>
            </div>
          )}
        </div>

        {/* Dynamic Cumulative Stat Panels */}
        <div className="grid grid-cols-2 gap-3">
          <div className="p-4 bg-slate-950/50 backdrop-blur-md rounded-2xl border border-white/5 shadow-md flex flex-col justify-between">
            <div>
              <p className="text-[9px] font-semibold tracking-widest text-slate-500 uppercase font-mono">PUSHUPS CUMULATIVE</p>
              <h3 className="text-3xl font-bold text-white mt-1 select-text tracking-tight h-[42px] flex items-center font-sans">
                {kpis.grossPushups}
              </h3>
            </div>
            <div className="text-[10px] text-slate-400 mt-2 font-mono flex items-center gap-1.5 border-t border-white/5 pt-2">
              <span className="text-slate-500 uppercase text-[9px]">SESSION AVG:</span>
              <span className="text-emerald-400 font-bold">{kpis.avgPushups} REPS</span>
            </div>
          </div>

          <div className="p-4 bg-slate-950/50 backdrop-blur-md rounded-2xl border border-white/5 shadow-md flex flex-col justify-between">
            <div>
              <p className="text-[9px] font-semibold tracking-widest text-slate-500 uppercase font-mono">CRUNCHES CUMULATIVE</p>
              <h3 className="text-3xl font-bold text-white mt-1 select-text tracking-tight h-[42px] flex items-center font-sans">
                {kpis.grossCrunches}
              </h3>
            </div>
            <div className="text-[10px] text-slate-400 mt-2 font-mono flex items-center gap-1.5 border-t border-white/5 pt-2">
              <span className="text-slate-500 uppercase text-[9px]">SESSION AVG:</span>
              <span className="text-indigo-400 font-bold">{kpis.avgCrunches} REPS</span>
            </div>
          </div>
        </div>

        {/* Rep Entry Form Section */}
        <div className="bg-slate-950/40 border border-white/5 p-5 rounded-2xl backdrop-blur-md relative overflow-hidden">
          <div className="absolute top-0 left-0 right-0 h-[1.5px] bg-gradient-to-r from-transparent via-emerald-500/15 to-transparent" />
          
          <form onSubmit={handleLogSubmit} className="space-y-4">
            <div className="flex items-center justify-between border-b border-white/5 pb-2.5">
              <h4 className="text-xs font-bold uppercase tracking-widest text-white flex items-center gap-1.5 font-sans">
                <Calendar size={13} className="text-emerald-400" />
                LOG REPS BY DATE
              </h4>
              <span 
                className={`text-[9px] font-mono px-2 py-0.5 rounded transition-all select-none font-bold ${
                  formBadgeStatus === "MUTATE_RECORD" 
                    ? "bg-amber-500/10 border border-amber-500/20 text-amber-400" 
                    : "bg-slate-900 border border-white/5 text-slate-500"
                }`}
              >
                {formBadgeStatus}
              </span>
            </div>

            {/* Stepper Calendar Selector Component */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-2 items-center">
              <label className="text-[10px] font-bold uppercase tracking-wider text-slate-500 font-mono">
                Log Date Position
              </label>
              <div className="col-span-1 md:col-span-2 flex items-center gap-1.5">
                <button 
                  type="button" 
                  onClick={() => stepDate(-1)} 
                  className="bg-slate-950 border border-white/5 text-slate-350 hover:bg-slate-900 active:bg-slate-950 px-3.5 py-3 rounded-xl transition-all cursor-pointer font-bold text-xs font-mono"
                >
                  ◀ PREV
                </button>
                <input 
                  type="date" 
                  required
                  value={activeDate}
                  onChange={(e) => setActiveDate(e.target.value)}
                  className="flex-1 bg-slate-950 border border-white/5 rounded-xl p-3 text-xs text-white text-center focus:outline-none focus:border-emerald-400 font-mono cursor-pointer"
                />
                <button 
                  type="button" 
                  onClick={() => stepDate(1)} 
                  className="bg-slate-950 border border-white/5 text-slate-350 hover:bg-slate-900 active:bg-slate-950 px-3.5 py-3 rounded-xl transition-all cursor-pointer font-bold text-xs font-mono"
                >
                  NEXT ▶
                </button>
              </div>
            </div>

            {/* Sets: Pushups */}
            <div className="space-y-1.5">
              <label className="block text-[10px] font-bold uppercase tracking-wider text-emerald-400 font-mono flex items-center gap-1">
                <Sparkles size={11} />
                Pushups Sets [1, 2, 3]
              </label>
              <div className="grid grid-cols-3 gap-2">
                <input 
                  type="number" 
                  inputMode="numeric"
                  pattern="[0-9]*"
                  placeholder="Set 1" 
                  value={p1}
                  onChange={(e) => setP1(e.target.value)}
                  className="w-full bg-slate-950 border border-white/5 focus:border-emerald-500/30 rounded-xl p-3 text-center text-white font-mono text-sm focus:outline-none transition-all"
                />
                <input 
                  type="number" 
                  inputMode="numeric"
                  pattern="[0-9]*"
                  placeholder="Set 2" 
                  value={p2}
                  onChange={(e) => setP2(e.target.value)}
                  className="w-full bg-slate-950 border border-white/5 focus:border-emerald-500/30 rounded-xl p-3 text-center text-white font-mono text-sm focus:outline-none transition-all"
                />
                <input 
                  type="number" 
                  inputMode="numeric"
                  pattern="[0-9]*"
                  placeholder="Set 3" 
                  value={p3}
                  onChange={(e) => setP3(e.target.value)}
                  className="w-full bg-slate-950 border border-white/5 focus:border-emerald-500/30 rounded-xl p-3 text-center text-white font-mono text-sm focus:outline-none transition-all"
                />
              </div>
            </div>

            {/* Sets: Crunches */}
            <div className="space-y-1.5">
              <label className="block text-[10px] font-bold uppercase tracking-wider text-indigo-400 font-mono flex items-center gap-1">
                <Sparkles size={11} />
                Crunches Sets [1, 2, 3]
              </label>
              <div className="grid grid-cols-3 gap-2">
                <input 
                  type="number" 
                  inputMode="numeric"
                  pattern="[0-9]*"
                  placeholder="Set 1" 
                  value={c1}
                  onChange={(e) => setC1(e.target.value)}
                  className="w-full bg-slate-950 border border-white/5 focus:border-indigo-500/30 rounded-xl p-3 text-center text-white font-mono text-sm focus:outline-none transition-all"
                />
                <input 
                  type="number" 
                  inputMode="numeric"
                  pattern="[0-9]*"
                  placeholder="Set 2" 
                  value={c2}
                  onChange={(e) => setC2(e.target.value)}
                  className="w-full bg-slate-950 border border-white/5 focus:border-indigo-500/30 rounded-xl p-3 text-center text-white font-mono text-sm focus:outline-none transition-all"
                />
                <input 
                  type="number" 
                  inputMode="numeric"
                  pattern="[0-9]*"
                  placeholder="Set 3" 
                  value={c3}
                  onChange={(e) => setC3(e.target.value)}
                  className="w-full bg-slate-950 border border-white/5 focus:border-indigo-500/30 rounded-xl p-3 text-center text-white font-mono text-sm focus:outline-none transition-all"
                />
              </div>
            </div>

            <button 
              type="submit" 
              className="w-full bg-slate-50 text-slate-950 font-extrabold py-4 rounded-xl text-xs uppercase tracking-widest font-mono hover:bg-slate-200 active:bg-slate-300 transition-all cursor-pointer shadow-md"
            >
              Commit Safe Session
            </button>
          </form>
        </div>

        {/* Dynamic Recharts Performance Graph */}
        <div className="p-4 bg-slate-950/40 border border-white/5 rounded-2xl min-h-[265px] flex flex-col justify-between backdrop-blur-md">
          <div className="flex items-center justify-between border-b border-white/5 pb-2 mb-2">
            <h4 className="text-xs font-bold uppercase tracking-widest text-white flex items-center gap-1.5 font-sans">
              <TrendingUp size={14} className="text-emerald-400" />
              PERFORMANCE TIMELINE
            </h4>
            <div className="flex bg-slate-950 p-0.5 rounded border border-white/5 text-[9px] font-mono select-none">
              <button 
                onClick={() => setRollingRangeWindow(14)}
                className={`px-2 py-1 rounded cursor-pointer transition-all ${
                  rollingRangeWindow === 14 ? "text-white bg-slate-800 font-bold" : "text-slate-400 hover:text-slate-200"
                }`}
              >
                14 DAYS
              </button>
              <button 
                onClick={() => setRollingRangeWindow(30)}
                className={`px-2 py-1 rounded cursor-pointer transition-all ${
                  rollingRangeWindow === 30 ? "text-white bg-slate-800 font-bold" : "text-slate-400 hover:text-slate-200"
                }`}
              >
                30 DAYS
              </button>
            </div>
          </div>
          
          <div className="relative w-full flex-1 h-[180px] mt-2">
            {chartData.length === 0 ? (
              <div className="h-full w-full min-h-[180px] flex flex-col items-center justify-center text-slate-500 font-mono text-[10px] gap-2 border border-dashed border-white/5 rounded-xl bg-black/20 px-4 text-center">
                <AlertTriangle size={15} className="text-slate-600" />
                <span>Reps will appear here dynamically to list your performance timeline.</span>
              </div>
            ) : (
              <ResponsiveContainer width="100%" height={180}>
                <LineChart data={chartData} margin={{ top: 10, right: 5, left: -25, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.01)" />
                  <XAxis 
                    dataKey="date" 
                    tick={{ fill: "#64748b", fontFamily: "JetBrains Mono", fontSize: 8 }} 
                    axisLine={{ stroke: "rgba(255,255,255,0.05)" }}
                    tickLine={false}
                  />
                  <YAxis 
                    tick={{ fill: "#64748b", fontFamily: "JetBrains Mono", fontSize: 8 }} 
                    axisLine={{ stroke: "rgba(255,255,255,0.05)" }}
                    tickLine={false}
                  />
                  <Tooltip 
                    contentStyle={{ 
                      backgroundColor: "#02040a", 
                      borderColor: "rgba(255,255,255,0.1)", 
                      fontFamily: "JetBrains Mono", 
                      fontSize: "10px", 
                      borderRadius: "12px" 
                    }}
                    labelStyle={{ color: "#94a3b8" }}
                  />
                  <Line 
                    type="monotone" 
                    name="Pushups"
                    dataKey="pushups" 
                    stroke="#10b981" 
                    strokeWidth={1.5} 
                    dot={{ r: 1 }} 
                    activeDot={{ r: 3 }}
                  />
                  <Line 
                    type="monotone" 
                    name="Crunches"
                    dataKey="crunches" 
                    stroke="#6366f1" 
                    strokeWidth={1} 
                    dot={{ r: 1 }} 
                    activeDot={{ r: 3 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </div>
        </div>

        {/* Offline Diagnostic Feed */}
        <div className="p-4 bg-slate-950/40 border border-white/5 rounded-2xl backdrop-blur-md">
          <div className="flex items-center justify-between border-b border-white/5 pb-2 mb-2 select-none">
            <h4 className="text-[10px] font-semibold tracking-widest text-slate-440 font-mono flex items-center gap-1.5">
              <Cpu size={12} className="text-emerald-400" />
              SYSTEM LOG EVENTS
            </h4>
            <button 
              onClick={copyLogsToClipboard} 
              className="text-[9px] font-mono bg-slate-950 border border-white/10 hover:bg-slate-900 px-2.5 py-1 rounded text-slate-400 cursor-pointer transition-all"
            >
              COPY OUTPUT
            </button>
          </div>

          <div 
            id="consoleBuffer" 
            ref={consoleBufferRef}
            className="font-mono text-[10px] text-slate-450 h-24 overflow-y-auto space-y-1.5 p-3.5 bg-black/40 border border-white/5 rounded-xl select-text scrollbar-thin scrollbar-thumb-white/5"
          >
            {consoleLogs.length === 0 ? (
              <div className="text-slate-600 italic">[Tracker initialized successfully...]</div>
            ) : (
              consoleLogs.map((entry) => (
                <div 
                  key={entry.id} 
                  className={entry.isError ? "text-red-400 font-semibold" : "text-emerald-400/95"}
                >
                  [{entry.timestamp}] {entry.message}
                </div>
              ))
            )}
          </div>
        </div>

      </main>
    </div>
  );
}
