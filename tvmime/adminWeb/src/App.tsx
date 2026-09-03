import React, { useState, useEffect } from 'react';
import { 
  Tv, 
  Plus, 
  Trash2, 
  CheckCircle2, 
  AlertCircle, 
  RefreshCw, 
  Server, 
  Shield, 
  Layers, 
  Power, 
  Copy, 
  Check, 
  Eye, 
  EyeOff, 
  Pencil, 
  Link as LinkIcon, 
  Film, 
  Video, 
  X,
  Sparkles
} from 'lucide-react';

import { 
  auth, 
  onAuthStateChanged, 
  signInAnonymously, 
  signInWithEmailAndPassword, 
  createUserWithEmailAndPassword, 
  signOut,
  subscribeToUserPortals, 
  addPortal, 
  updatePortal, 
  deletePortal, 
  type XtreamPortal, 
  type User 
} from './lib/firebase';
import { 
  testXtreamConnection, 
  parseM3uUrl, 
  generateMasterM3uUrl, 
  type XtreamAuthResult 
} from './lib/xtream';

function SlidingToggle({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: () => void;
  label?: string;
}) {
  return (
    <div 
      onClick={(e) => { e.preventDefault(); e.stopPropagation(); onChange(); }}
      className="inline-flex items-center gap-2 cursor-pointer select-none group"
      role="switch"
      aria-checked={checked}
    >
      <div 
        className={`relative inline-flex h-6 w-11 shrink-0 rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out ${
          checked ? 'bg-emerald-500 shadow-sm shadow-emerald-500/30' : 'bg-[#262632] group-hover:bg-[#343444]'
        }`}
      >
        <span
          className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-md ring-0 transition duration-200 ease-in-out ${
            checked ? 'translate-x-5' : 'translate-x-0'
          }`}
        />
      </div>
      {label && (
        <span className={`text-xs font-semibold transition-colors ${checked ? 'text-emerald-400' : 'text-gray-400'}`}>
          {label}
        </span>
      )}
    </div>
  );
}

export function App() {

  const [user, setUser] = useState<User | null>(null);
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [portals, setPortals] = useState<XtreamPortal[]>([]);
  
  // Auth Form State
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [authError, setAuthError] = useState<string | null>(null);
  const [isRegistering, setIsRegistering] = useState(false);

  // Add / Edit Modal State
  const [isFormModalOpen, setIsFormModalOpen] = useState(false);
  const [editingPortalId, setEditingPortalId] = useState<string | null>(null);
  const [inputMode, setInputMode] = useState<'xtream' | 'm3u'>('xtream');
  
  // Form fields
  const [name, setName] = useState('');
  const [serverUrl, setServerUrl] = useState('');
  const [username, setUsername] = useState('');
  const [portalPassword, setPortalPassword] = useState('');
  const [m3uUrl, setM3uUrl] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [syncLive, setSyncLive] = useState(true);
  const [syncMovies, setSyncMovies] = useState(true);
  const [syncSeries, setSyncSeries] = useState(true);
  const [isActive, setIsActive] = useState(true);
  const [m3uDetectedNote, setM3uDetectedNote] = useState<string | null>(null);

  // Verification & Action states
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<XtreamAuthResult | null>(null);
  const [saving, setSaving] = useState(false);

  // Credentials Modal State
  const [credsPortal, setCredsPortal] = useState<XtreamPortal | null>(null);
  const [credsShowPassword, setCredsShowPassword] = useState(false);
  const [copiedField, setCopiedField] = useState<string | null>(null);

  // Listen to Auth
  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
      setLoadingAuth(false);
    });
    return () => unsub();
  }, []);

  // Listen to Firestore Portals for current user
  useEffect(() => {
    if (!user) {
      setPortals([]);
      return;
    }
    const unsub = subscribeToUserPortals(user.uid, (fetched) => {
      setPortals(fetched);
    });
    return () => unsub();
  }, [user]);

  const handleEmailAuth = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);
    try {
      if (isRegistering) {
        await createUserWithEmailAndPassword(auth, email, password);
      } else {
        await signInWithEmailAndPassword(auth, email, password);
      }
    } catch (err: unknown) {
      setAuthError(err instanceof Error ? err.message : 'Authentication failed');
    }
  };

  const handleQuickAccess = async () => {
    setAuthError(null);
    try {
      await signInAnonymously(auth);
    } catch (err: unknown) {
      setAuthError(err instanceof Error ? err.message : 'Quick access failed');
    }
  };

  const openAddModal = () => {
    setEditingPortalId(null);
    setName('');
    setInputMode('xtream');
    setServerUrl('');
    setUsername('');
    setPortalPassword('');
    setM3uUrl('');
    setShowPassword(false);
    setSyncLive(true);
    setSyncMovies(true);
    setSyncSeries(true);
    setIsActive(true);
    setTestResult(null);
    setM3uDetectedNote(null);
    setIsFormModalOpen(true);
  };

  const openEditModal = (p: XtreamPortal) => {
    setEditingPortalId(p.id || null);
    setName(p.name);
    setInputMode(p.type === 'm3u' ? 'm3u' : 'xtream');
    setServerUrl(p.serverUrl || '');
    setUsername(p.username || '');
    setPortalPassword(p.password || '');
    setM3uUrl(p.m3uUrl || '');
    setShowPassword(false);
    setSyncLive(p.syncLive ?? true);
    setSyncMovies(p.syncMovies ?? true);
    setSyncSeries(p.syncSeries ?? true);
    setIsActive(p.isActive ?? true);
    setTestResult(null);
    setM3uDetectedNote(null);
    setIsFormModalOpen(true);
  };

  // M3U Link Change handler with automatic parameter extraction
  const handleM3uUrlChange = (value: string) => {
    setM3uUrl(value);
    setM3uDetectedNote(null);
    if (!value.trim()) return;

    const parsed = parseM3uUrl(value);
    if (parsed.isXtream && parsed.serverUrl && parsed.username && parsed.password) {
      setServerUrl(parsed.serverUrl);
      setUsername(parsed.username);
      setPortalPassword(parsed.password);
      setM3uDetectedNote(`Xtream credentials detected! Server: ${parsed.serverUrl}, User: ${parsed.username}`);
      if (!name) {
        setName(`${new URL(parsed.serverUrl).hostname} (Xtream)`);
      }
    }
  };

  const handleTestConnection = async () => {
    const urlToTest = serverUrl || m3uUrl;
    if (!urlToTest || !username || !portalPassword) {
      setTestResult({ success: false, message: 'Please provide Server URL, Username, and Password to test.' });
      return;
    }
    setTesting(true);
    setTestResult(null);
    const res = await testXtreamConnection(urlToTest, username, portalPassword);
    setTesting(false);
    setTestResult(res);
  };

  const handleSavePortal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    if (!name.trim()) return;

    setSaving(true);
    try {
      const isM3u = inputMode === 'm3u' && (!serverUrl || !username);
      const cleanServer = serverUrl ? serverUrl.trim().replace(/\/+$/, '') : '';
      
      const payload: Partial<XtreamPortal> = {
        userId: user.uid,
        name: name.trim(),
        serverUrl: cleanServer,
        username: username.trim(),
        password: portalPassword.trim(),
        type: isM3u ? 'm3u' : 'xtream',
        isActive: Boolean(isActive),
        syncLive: Boolean(syncLive),
        syncMovies: Boolean(syncMovies),
        syncSeries: Boolean(syncSeries),
      };

      if (m3uUrl && m3uUrl.trim()) {
        payload.m3uUrl = m3uUrl.trim();
      }

      if (testResult?.success) {
        payload.status = 'online';
      }
      if (testResult?.expDate) {
        payload.expiryDate = testResult.expDate;
      }

      if (editingPortalId) {
        await updatePortal(editingPortalId, payload);
      } else {
        await addPortal({
          ...payload,
          createdAt: Date.now(),
          status: testResult?.success ? 'online' : 'unknown',
        } as Omit<XtreamPortal, 'id'>);
      }

      setIsFormModalOpen(false);
    } catch (err: unknown) {
      console.error("Save portal error:", err);
      const msg = err instanceof Error ? err.message : String(err);
      alert(`Failed to save connection: ${msg}`);
    } finally {
      setSaving(false);
    }
  };


  const handleToggleActive = async (portal: XtreamPortal) => {
    if (!portal.id) return;
    await updatePortal(portal.id, { isActive: !portal.isActive });
  };

  const handleDelete = async (id: string, portalName: string) => {
    if (window.confirm(`Delete connection "${portalName}"?`)) {
      await deletePortal(id);
    }
  };

  const copyToClipboard = (text: string, fieldKey: string) => {
    navigator.clipboard.writeText(text);
    setCopiedField(fieldKey);
    setTimeout(() => setCopiedField(null), 2000);
  };

  const openCredentialsModal = (portal: XtreamPortal) => {
    setCredsPortal(portal);
    setCredsShowPassword(false);
    setCopiedField(null);
  };

  if (loadingAuth) {
    return (
      <div className="min-h-screen bg-[#070709] flex items-center justify-center text-gray-400">
        <RefreshCw className="w-8 h-8 animate-spin text-[#e50914]" />
      </div>
    );
  }

  // Not Logged In View
  if (!user) {
    return (
      <div className="min-h-screen bg-[#070709] text-white flex flex-col justify-center items-center p-4">
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-[#e50914]/10 rounded-full blur-3xl pointer-events-none" />

        <div className="relative w-full max-w-md bg-[#121217] border border-[#262632] rounded-2xl p-8 shadow-2xl">
          <div className="flex items-center justify-center gap-3 mb-6">
            <div className="p-3 bg-[#e50914] rounded-xl text-white shadow-lg shadow-[#e50914]/30">
              <Tv className="w-8 h-8" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-wider">TVMIME</h1>
              <p className="text-xs text-gray-400 font-mono tracking-widest uppercase">Admin Cloud Vault</p>
            </div>
          </div>

          <p className="text-sm text-gray-400 text-center mb-6">
            Manage your IPTV portals in the cloud. Credentials sync automatically to your Android TV & Mobile player.
          </p>

          {authError && (
            <div className="mb-4 p-3 bg-red-950/50 border border-red-800/80 rounded-xl text-xs text-red-200 flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 text-[#e50914]" />
              <span>{authError}</span>
            </div>
          )}

          <form onSubmit={handleEmailAuth} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">Email</label>
              <input 
                type="email" 
                value={email} 
                onChange={(e) => setEmail(e.target.value)}
                placeholder="admin@tvmime.com"
                required
                className="w-full bg-[#181822] border border-[#262632] focus:border-[#e50914] focus:outline-none rounded-xl px-4 py-3 text-sm text-white placeholder-gray-500 transition-colors"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">Password</label>
              <input 
                type="password" 
                value={password} 
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                required
                className="w-full bg-[#181822] border border-[#262632] focus:border-[#e50914] focus:outline-none rounded-xl px-4 py-3 text-sm text-white placeholder-gray-500 transition-colors"
              />
            </div>

            <button 
              type="submit"
              className="w-full py-3 bg-[#e50914] hover:bg-[#ff1e27] text-white font-semibold rounded-xl text-sm transition shadow-lg shadow-[#e50914]/25 cursor-pointer"
            >
              {isRegistering ? 'Create Admin Account' : 'Sign In'}
            </button>
          </form>

          <div className="relative my-6 text-center">
            <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-[#262632]"></div></div>
            <span className="relative bg-[#121217] px-3 text-xs text-gray-500 uppercase tracking-wider">Or</span>
          </div>

          <button 
            type="button"
            onClick={handleQuickAccess}
            className="w-full py-3 bg-[#181822] hover:bg-[#20202c] border border-[#262632] text-gray-300 hover:text-white font-medium rounded-xl text-sm transition flex items-center justify-center gap-2 cursor-pointer"
          >
            <Shield className="w-4 h-4 text-emerald-400" />
            <span>Instant Quick Access (Anonymous)</span>
          </button>

          <div className="mt-6 text-center">
            <button
              type="button"
              onClick={() => setIsRegistering(!isRegistering)}
              className="text-xs text-gray-400 hover:text-[#ff1e27] transition cursor-pointer"
            >
              {isRegistering ? 'Already have an account? Sign In' : 'Need an account? Register with Email'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Logged In Dashboard View
  const activeCount = portals.filter(p => p.isActive).length;

  return (
    <div className="min-h-screen bg-[#070709] text-gray-100 flex flex-col">
      {/* Top Header */}
      <header className="border-b border-[#262632] bg-[#0c0c10]/80 backdrop-blur-md sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-[#e50914] rounded-lg text-white shadow-md shadow-[#e50914]/30">
              <Tv className="w-5 h-5" />
            </div>
            <div>
              <span className="font-extrabold text-lg tracking-wider text-white">TVMIME</span>
              <span className="ml-2 text-xs bg-[#e50914]/20 text-[#ff1e27] border border-[#e50914]/40 px-2 py-0.5 rounded-full font-mono font-medium">ADMIN</span>
            </div>
          </div>

          <div className="flex items-center gap-4">
            <div className="text-right hidden sm:block">
              <p className="text-xs text-gray-400">Authenticated User</p>
              <p className="text-xs font-mono text-gray-200 truncate max-w-[200px]">
                {user.email || `Anon (${user.uid.slice(0, 8)})`}
              </p>
            </div>

            <button 
              onClick={() => signOut(auth)}
              title="Sign Out"
              className="p-2 hover:bg-[#181822] text-gray-400 hover:text-white rounded-lg transition border border-transparent hover:border-[#262632] cursor-pointer"
            >
              <Power className="w-5 h-5" />
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
        {/* Welcome & Stats Row */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="bg-[#121217] border border-[#262632] rounded-2xl p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs text-gray-400 font-medium uppercase tracking-wider">Total Connections</p>
                <p className="text-2xl font-bold text-white mt-1">{portals.length}</p>
              </div>
              <div className="p-3 bg-[#181822] rounded-xl text-gray-400">
                <Server className="w-6 h-6" />
              </div>
            </div>
          </div>

          <div className="bg-[#121217] border border-[#262632] rounded-2xl p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs text-gray-400 font-medium uppercase tracking-wider">Active for TV Player</p>
                <p className="text-2xl font-bold text-[#ff1e27] mt-1">{activeCount}</p>
              </div>
              <div className="p-3 bg-[#e50914]/10 rounded-xl text-[#e50914]">
                <Layers className="w-6 h-6" />
              </div>
            </div>
          </div>

          <div className="bg-[#121217] border border-[#262632] rounded-2xl p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs text-gray-400 font-medium uppercase tracking-wider">Firestore Cloud State</p>
                <p className="text-sm font-semibold text-emerald-400 mt-1 flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                  Connected & Live
                </p>
              </div>
              <div className="p-3 bg-emerald-950/40 rounded-xl text-emerald-400">
                <CheckCircle2 className="w-6 h-6" />
              </div>
            </div>
          </div>
        </div>

        {/* Action Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h2 className="text-xl font-bold text-white tracking-wide">IPTV Connections & Playlists</h2>
            <p className="text-xs text-gray-400 mt-0.5">
              These credentials sync automatically to your TV player. Toggle items on/off or filter content types.
            </p>
          </div>

          <button
            onClick={openAddModal}
            className="flex items-center justify-center gap-2 px-4 py-2.5 bg-[#e50914] hover:bg-[#ff1e27] text-white font-semibold rounded-xl text-sm transition shadow-lg shadow-[#e50914]/25 cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>Add Connection / M3U</span>
          </button>
        </div>

        {/* Portal Cards Grid */}
        {portals.length === 0 ? (
          <div className="border border-dashed border-[#262632] rounded-2xl p-12 text-center bg-[#121217]/50">
            <Server className="w-12 h-12 text-gray-600 mx-auto mb-3" />
            <h3 className="text-base font-semibold text-gray-300">No Connections Added Yet</h3>
            <p className="text-xs text-gray-500 max-w-sm mx-auto mt-1 mb-6">
              Add your first Xtream Codes provider or paste a full M3U playlist link to sync directly to your TV.
            </p>
            <button
              onClick={openAddModal}
              className="inline-flex items-center gap-2 px-4 py-2 bg-[#e50914] hover:bg-[#ff1e27] text-white text-xs font-semibold rounded-lg transition cursor-pointer"
            >
              <Plus className="w-4 h-4" /> Add Connection
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {portals.map((p) => {
              const isEnabled = p.isActive !== false;
              const liveOn = p.syncLive !== false;
              const moviesOn = p.syncMovies !== false;
              const seriesOn = p.syncSeries !== false;

              return (
                <div 
                  key={p.id} 
                  className={`bg-[#121217] border ${isEnabled ? 'border-[#262632]' : 'border-gray-800 opacity-60'} rounded-2xl p-5 flex flex-col justify-between transition-all hover:border-[#383848]`}
                >
                  <div>
                    {/* Card Top: Status & Name */}
                    <div className="flex items-start justify-between gap-3 mb-3">
                      <div className="flex items-center gap-2.5">
                        <div className={`w-3 h-3 rounded-full ${isEnabled ? 'bg-emerald-400' : 'bg-gray-600'}`}></div>
                        <h3 className="font-bold text-base text-white">{p.name}</h3>
                        <span className="text-[10px] uppercase font-mono px-2 py-0.5 rounded bg-[#181822] text-gray-400 border border-[#262632]">
                          {p.type || 'xtream'}
                        </span>
                      </div>

                      {/* Top Right: Enable/Disable Switch & Actions */}
                      <div className="flex items-center gap-3">
                        {/* Enable/Disable Sliding Toggle */}
                        <SlidingToggle
                          checked={isEnabled}
                          onChange={() => handleToggleActive(p)}
                          label={isEnabled ? "Active" : "Disabled"}
                        />


                        {/* Edit Button */}
                        <button
                          onClick={() => openEditModal(p)}
                          className="p-1.5 text-gray-400 hover:text-white hover:bg-[#181822] rounded-lg transition cursor-pointer"
                          title="Edit Connection Details"
                        >
                          <Pencil className="w-4 h-4" />
                        </button>

                        {/* Delete Button */}
                        <button
                          onClick={() => p.id && handleDelete(p.id, p.name)}
                          className="p-1.5 text-gray-500 hover:text-red-400 hover:bg-[#181822] rounded-lg transition cursor-pointer"
                          title="Delete Connection"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>

                    {/* Details Box */}
                    <div className="bg-[#181822] rounded-xl p-3 space-y-2 border border-[#262632]/80 text-xs font-mono">
                      {p.serverUrl ? (
                        <div className="flex items-center justify-between text-gray-400">
                          <span className="text-gray-500">Host:</span>
                          <span className="text-gray-200 truncate max-w-[280px]">{p.serverUrl}</span>
                        </div>
                      ) : p.m3uUrl ? (
                        <div className="flex items-center justify-between text-gray-400">
                          <span className="text-gray-500">M3U:</span>
                          <span className="text-gray-200 truncate max-w-[280px]">{p.m3uUrl}</span>
                        </div>
                      ) : null}

                      {p.username && (
                        <div className="flex items-center justify-between text-gray-400">
                          <span className="text-gray-500">User:</span>
                          <span className="text-gray-200">{p.username}</span>
                        </div>
                      )}

                      {p.expiryDate && (
                        <div className="flex items-center justify-between text-gray-400">
                          <span className="text-gray-500">Expiry:</span>
                          <span className="text-amber-400">{p.expiryDate}</span>
                        </div>
                      )}

                      {/* Content Sync Filter Badges */}
                      <div className="pt-2 mt-2 border-t border-[#262632] flex items-center justify-between">
                        <span className="text-gray-500 text-[11px] font-sans">Content Filter:</span>
                        <div className="flex items-center gap-1.5 font-sans">
                          <span className={`text-[10px] px-2 py-0.5 rounded font-semibold border ${
                            liveOn 
                              ? 'bg-red-950/60 text-red-300 border-red-800' 
                              : 'bg-[#121217] text-gray-600 border-gray-800 line-through'
                          }`}>
                            Live
                          </span>
                          <span className={`text-[10px] px-2 py-0.5 rounded font-semibold border ${
                            moviesOn 
                              ? 'bg-blue-950/60 text-blue-300 border-blue-800' 
                              : 'bg-[#121217] text-gray-600 border-gray-800 line-through'
                          }`}>
                            Movies
                          </span>
                          <span className={`text-[10px] px-2 py-0.5 rounded font-semibold border ${
                            seriesOn 
                              ? 'bg-purple-950/60 text-purple-300 border-purple-800' 
                              : 'bg-[#121217] text-gray-600 border-gray-800 line-through'
                          }`}>
                            Series
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Card Bottom: Timestamp & Credentials Modal Trigger */}
                  <div className="mt-4 pt-3 border-t border-[#262632] flex items-center justify-between text-xs">
                    <span className="text-gray-500">
                      Added {new Date(p.createdAt).toLocaleDateString()}
                    </span>

                    <button
                      onClick={() => openCredentialsModal(p)}
                      className="flex items-center gap-1.5 px-3 py-1.5 bg-[#181822] hover:bg-[#222230] border border-[#262632] rounded-lg text-gray-300 hover:text-white transition cursor-pointer"
                    >
                      <Copy className="w-3.5 h-3.5 text-[#e50914]" />
                      <span>View & Copy Credentials</span>
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Pairing Information Card */}
        <div className="bg-gradient-to-r from-[#121217] to-[#181822] border border-[#262632] rounded-2xl p-6">
          <div className="flex items-start gap-4">
            <div className="p-3 bg-[#e50914]/20 border border-[#e50914]/30 rounded-xl text-[#ff1e27] shrink-0">
              <Tv className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-bold text-white text-base">Android TV Sync Engine</h3>
              <p className="text-xs text-gray-400 mt-1 leading-relaxed">
                When TVMime launches on your TV or Firestick, it connects to your Firestore vault and streams these connections directly 
                into its fast local SQLite database. Playlists marked <span className="text-emerald-400 font-semibold">Enabled</span> will sync, 
                while <span className="text-gray-400 font-semibold">Disabled</span> ones are ignored. Content filters (Live/Movies/Series) prevent 
                memory bloat on low-RAM devices!
              </p>
            </div>
          </div>
        </div>
      </main>

      {/* ============================================================ */}
      {/* ADD / EDIT CONNECTION MODAL */}
      {/* ============================================================ */}
      {isFormModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm overflow-y-auto">
          <div className="relative w-full max-w-lg bg-[#121217] border border-[#262632] rounded-2xl p-6 shadow-2xl space-y-5 my-8">
            <div className="flex items-center justify-between border-b border-[#262632] pb-4">
              <div className="flex items-center gap-2.5">
                <div className="p-2 bg-[#e50914] rounded-lg text-white">
                  <Server className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="font-bold text-base text-white">
                    {editingPortalId ? 'Edit Connection' : 'Add New Connection'}
                  </h3>
                  <p className="text-xs text-gray-400">Configure parameters or paste an M3U link</p>
                </div>
              </div>
              <button 
                onClick={() => setIsFormModalOpen(false)}
                className="text-gray-400 hover:text-white p-1 rounded-lg transition cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Input Mode Switcher (Xtream Parameters vs Full M3U) */}
            <div className="grid grid-cols-2 gap-2 bg-[#181822] p-1 rounded-xl border border-[#262632]">
              <button
                type="button"
                onClick={() => setInputMode('xtream')}
                className={`py-2 text-xs font-semibold rounded-lg transition cursor-pointer ${
                  inputMode === 'xtream' 
                    ? 'bg-[#e50914] text-white shadow' 
                    : 'text-gray-400 hover:text-white'
                }`}
              >
                Xtream Parameters
              </button>
              <button
                type="button"
                onClick={() => setInputMode('m3u')}
                className={`py-2 text-xs font-semibold rounded-lg transition cursor-pointer flex items-center justify-center gap-1.5 ${
                  inputMode === 'm3u' 
                    ? 'bg-[#e50914] text-white shadow' 
                    : 'text-gray-400 hover:text-white'
                }`}
              >
                <LinkIcon className="w-3.5 h-3.5" />
                <span>Full M3U Link</span>
              </button>
            </div>

            <form onSubmit={handleSavePortal} className="space-y-4">
              {/* Connection Name */}
              <div>
                <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">
                  Connection Name / Nickname
                </label>
                <input 
                  type="text" 
                  value={name} 
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Primary 4K Workstation"
                  required
                  className="w-full bg-[#181822] border border-[#262632] focus:border-[#e50914] focus:outline-none rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-500"
                />
              </div>

              {/* Mode: Full M3U Link */}
              {inputMode === 'm3u' && (
                <div className="space-y-3">
                  <div>
                    <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">
                      M3U / M3U_PLUS Playlist URL
                    </label>
                    <textarea 
                      value={m3uUrl} 
                      onChange={(e) => handleM3uUrlChange(e.target.value)}
                      placeholder="http://provider-domain.com:8080/get.php?username=XXX&password=YYY&type=m3u_plus&output=ts"
                      rows={3}
                      className="w-full bg-[#181822] border border-[#262632] focus:border-[#e50914] focus:outline-none rounded-xl px-4 py-2.5 text-xs text-white placeholder-gray-500 font-mono"
                    />
                  </div>

                  {m3uDetectedNote && (
                    <div className="p-3 bg-emerald-950/40 border border-emerald-800/80 rounded-xl text-xs text-emerald-200 flex items-start gap-2">
                      <Sparkles className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                      <span>{m3uDetectedNote}</span>
                    </div>
                  )}
                </div>
              )}

              {/* Mode: Xtream Parameters */}
              {(inputMode === 'xtream' || (inputMode === 'm3u' && serverUrl)) && (
                <div className="space-y-3 pt-1">
                  <div>
                    <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">
                      Server URL
                    </label>
                    <input 
                      type="text" 
                      value={serverUrl} 
                      onChange={(e) => setServerUrl(e.target.value)}
                      placeholder="http://provider-dns.me:8080"
                      required={inputMode === 'xtream'}
                      className="w-full bg-[#181822] border border-[#262632] focus:border-[#e50914] focus:outline-none rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-500 font-mono"
                    />
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">
                        Username
                      </label>
                      <input 
                        type="text" 
                        value={username} 
                        onChange={(e) => setUsername(e.target.value)}
                        placeholder="xtream_user"
                        required={inputMode === 'xtream'}
                        className="w-full bg-[#181822] border border-[#262632] focus:border-[#e50914] focus:outline-none rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-500 font-mono"
                      />
                    </div>

                    {/* Password with Reveal/Hide Toggle */}
                    <div>
                      <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">
                        Password
                      </label>
                      <div className="relative">
                        <input 
                          type={showPassword ? "text" : "password"} 
                          value={portalPassword} 
                          onChange={(e) => setPortalPassword(e.target.value)}
                          placeholder="••••••••"
                          required={inputMode === 'xtream'}
                          className="w-full bg-[#181822] border border-[#262632] focus:border-[#e50914] focus:outline-none rounded-xl pl-4 pr-10 py-2.5 text-sm text-white placeholder-gray-500 font-mono"
                        />
                        <button
                          type="button"
                          onClick={() => setShowPassword(!showPassword)}
                          className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white transition cursor-pointer"
                          title={showPassword ? "Hide password" : "Show password"}
                        >
                          {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* Content Type Sync Toggles (Live / Movies / Series) */}
              <div className="pt-2 border-t border-[#262632]">
                <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-2">
                  Content Sync Toggles (Download Filter)
                </label>
                <div className="grid grid-cols-3 gap-2">
                  {/* Live Toggle */}
                  <button
                    type="button"
                    onClick={() => setSyncLive(!syncLive)}
                    className={`p-2.5 rounded-xl border text-xs font-medium flex flex-col items-center gap-1.5 transition cursor-pointer ${
                      syncLive 
                        ? 'bg-red-950/40 border-red-700/80 text-white shadow-sm' 
                        : 'bg-[#181822] border-[#262632] text-gray-500'
                    }`}
                  >
                    <Tv className={`w-4 h-4 ${syncLive ? 'text-[#ff1e27]' : 'text-gray-500'}`} />
                    <span>Live Channels</span>
                    <span className={`text-[10px] font-bold ${syncLive ? 'text-emerald-400' : 'text-gray-500'}`}>
                      {syncLive ? 'ON' : 'OFF'}
                    </span>
                  </button>

                  {/* Movies Toggle */}
                  <button
                    type="button"
                    onClick={() => setSyncMovies(!syncMovies)}
                    className={`p-2.5 rounded-xl border text-xs font-medium flex flex-col items-center gap-1.5 transition cursor-pointer ${
                      syncMovies 
                        ? 'bg-blue-950/40 border-blue-700/80 text-white shadow-sm' 
                        : 'bg-[#181822] border-[#262632] text-gray-500'
                    }`}
                  >
                    <Film className={`w-4 h-4 ${syncMovies ? 'text-blue-400' : 'text-gray-500'}`} />
                    <span>VOD Movies</span>
                    <span className={`text-[10px] font-bold ${syncMovies ? 'text-emerald-400' : 'text-gray-500'}`}>
                      {syncMovies ? 'ON' : 'OFF'}
                    </span>
                  </button>

                  {/* Series Toggle */}
                  <button
                    type="button"
                    onClick={() => setSyncSeries(!syncSeries)}
                    className={`p-2.5 rounded-xl border text-xs font-medium flex flex-col items-center gap-1.5 transition cursor-pointer ${
                      syncSeries 
                        ? 'bg-purple-950/40 border-purple-700/80 text-white shadow-sm' 
                        : 'bg-[#181822] border-[#262632] text-gray-500'
                    }`}
                  >
                    <Video className={`w-4 h-4 ${syncSeries ? 'text-purple-400' : 'text-gray-500'}`} />
                    <span>TV Series</span>
                    <span className={`text-[10px] font-bold ${syncSeries ? 'text-emerald-400' : 'text-gray-500'}`}>
                      {syncSeries ? 'ON' : 'OFF'}
                    </span>
                  </button>
                </div>
              </div>

              {/* Master Playlist Enabled Switch */}
              <div className="pt-2 border-t border-[#262632] flex items-center justify-between">
                <div>
                  <p className="text-xs font-semibold text-white">Enable Connection for Player</p>
                  <p className="text-[11px] text-gray-400">If disabled, player app will ignore and not download this playlist</p>
                </div>
                <SlidingToggle
                  checked={isActive}
                  onChange={() => setIsActive(!isActive)}
                  label={isActive ? "Active" : "Disabled"}
                />
              </div>


              {/* Test Result Feedback */}
              {testResult && (
                <div className={`p-3 rounded-xl border text-xs flex items-start gap-2.5 ${
                  testResult.success 
                    ? 'bg-emerald-950/40 border-emerald-800 text-emerald-200' 
                    : 'bg-amber-950/40 border-amber-800 text-amber-200'
                }`}>
                  {testResult.success ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                  ) : (
                    <AlertCircle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                  )}
                  <div>
                    <p className="font-semibold">{testResult.message}</p>
                    {testResult.expDate && (
                      <p className="text-[11px] text-gray-400 mt-1">
                        Expiry: <span className="text-white">{testResult.expDate}</span> | Connections: {testResult.activeCons}/{testResult.maxCons}
                      </p>
                    )}
                  </div>
                </div>
              )}

              {/* Modal Buttons */}
              <div className="flex items-center justify-between pt-3 border-t border-[#262632] gap-3">
                <button
                  type="button"
                  onClick={handleTestConnection}
                  disabled={testing}
                  className="px-4 py-2.5 bg-[#181822] hover:bg-[#20202c] border border-[#262632] text-gray-300 hover:text-white rounded-xl text-xs font-semibold flex items-center gap-2 transition cursor-pointer"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${testing ? 'animate-spin text-[#e50914]' : ''}`} />
                  <span>{testing ? 'Testing...' : 'Test Connection'}</span>
                </button>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setIsFormModalOpen(false)}
                    className="px-4 py-2.5 text-gray-400 hover:text-white text-xs font-semibold rounded-xl transition cursor-pointer"
                  >
                    Cancel
                  </button>

                  <button
                    type="submit"
                    disabled={saving}
                    className="px-5 py-2.5 bg-[#e50914] hover:bg-[#ff1e27] text-white text-xs font-semibold rounded-xl transition shadow-lg shadow-[#e50914]/25 cursor-pointer"
                  >
                    {saving ? 'Saving...' : editingPortalId ? 'Save Changes' : 'Add Connection'}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ============================================================ */}
      {/* CREDENTIALS & QUICK COPY MODAL */}
      {/* ============================================================ */}
      {credsPortal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="relative w-full max-w-lg bg-[#121217] border border-[#262632] rounded-2xl p-6 shadow-2xl space-y-5">
            <div className="flex items-center justify-between border-b border-[#262632] pb-4">
              <div className="flex items-center gap-2.5">
                <div className="p-2 bg-[#e50914] rounded-lg text-white">
                  <Copy className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="font-bold text-base text-white">{credsPortal.name}</h3>
                  <p className="text-xs text-gray-400">Connection Credentials & Master M3U Link</p>
                </div>
              </div>
              <button 
                onClick={() => setCredsPortal(null)}
                className="text-gray-400 hover:text-white p-1 rounded-lg transition cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3 text-xs font-mono">
              {/* Server URL */}
              {credsPortal.serverUrl && (
                <div>
                  <label className="block text-[11px] font-sans font-semibold text-gray-400 mb-1">Server URL</label>
                  <div className="flex items-center gap-2 bg-[#181822] border border-[#262632] rounded-xl px-3 py-2">
                    <span className="text-gray-200 truncate flex-1">{credsPortal.serverUrl}</span>
                    <button
                      onClick={() => copyToClipboard(credsPortal.serverUrl, 'server')}
                      className="p-1.5 bg-[#121217] hover:bg-[#222230] border border-[#262632] text-gray-300 hover:text-white rounded-lg transition cursor-pointer"
                      title="Copy Server URL"
                    >
                      {copiedField === 'server' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    </button>
                  </div>
                </div>
              )}

              {/* Username */}
              {credsPortal.username && (
                <div>
                  <label className="block text-[11px] font-sans font-semibold text-gray-400 mb-1">Username</label>
                  <div className="flex items-center gap-2 bg-[#181822] border border-[#262632] rounded-xl px-3 py-2">
                    <span className="text-gray-200 flex-1">{credsPortal.username}</span>
                    <button
                      onClick={() => copyToClipboard(credsPortal.username, 'user')}
                      className="p-1.5 bg-[#121217] hover:bg-[#222230] border border-[#262632] text-gray-300 hover:text-white rounded-lg transition cursor-pointer"
                      title="Copy Username"
                    >
                      {copiedField === 'user' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    </button>
                  </div>
                </div>
              )}

              {/* Password with Reveal and Copy */}
              {credsPortal.password && (
                <div>
                  <label className="block text-[11px] font-sans font-semibold text-gray-400 mb-1">Password</label>
                  <div className="flex items-center gap-2 bg-[#181822] border border-[#262632] rounded-xl px-3 py-2">
                    <span className="text-gray-200 flex-1">
                      {credsShowPassword ? credsPortal.password : '••••••••••••'}
                    </span>
                    <button
                      type="button"
                      onClick={() => setCredsShowPassword(!credsShowPassword)}
                      className="p-1.5 text-gray-400 hover:text-white transition cursor-pointer"
                      title={credsShowPassword ? "Hide password" : "Show password"}
                    >
                      {credsShowPassword ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                    </button>
                    <button
                      onClick={() => copyToClipboard(credsPortal.password, 'pass')}
                      className="p-1.5 bg-[#121217] hover:bg-[#222230] border border-[#262632] text-gray-300 hover:text-white rounded-lg transition cursor-pointer"
                      title="Copy Password"
                    >
                      {copiedField === 'pass' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    </button>
                  </div>
                </div>
              )}

              {/* Master M3U Plus Link */}
              {credsPortal.serverUrl && credsPortal.username && credsPortal.password && (
                <div>
                  <label className="block text-[11px] font-sans font-semibold text-gray-400 mb-1">
                    Master M3U Plus Link (Quick-Add for any IPTV app)
                  </label>
                  <div className="flex items-center gap-2 bg-[#181822] border border-[#262632] rounded-xl px-3 py-2">
                    <span className="text-gray-200 truncate flex-1 text-[11px]">
                      {generateMasterM3uUrl(credsPortal.serverUrl, credsPortal.username, credsPortal.password)}
                    </span>
                    <button
                      onClick={() => copyToClipboard(generateMasterM3uUrl(credsPortal.serverUrl, credsPortal.username, credsPortal.password), 'masterM3u')}
                      className="p-1.5 bg-[#121217] hover:bg-[#222230] border border-[#262632] text-gray-300 hover:text-white rounded-lg transition cursor-pointer"
                      title="Copy Master M3U Link"
                    >
                      {copiedField === 'masterM3u' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    </button>
                  </div>
                </div>
              )}

              {/* Raw M3U Link if set */}
              {credsPortal.m3uUrl && (
                <div>
                  <label className="block text-[11px] font-sans font-semibold text-gray-400 mb-1">Custom M3U URL</label>
                  <div className="flex items-center gap-2 bg-[#181822] border border-[#262632] rounded-xl px-3 py-2">
                    <span className="text-gray-200 truncate flex-1 text-[11px]">{credsPortal.m3uUrl}</span>
                    <button
                      onClick={() => copyToClipboard(credsPortal.m3uUrl || '', 'rawM3u')}
                      className="p-1.5 bg-[#121217] hover:bg-[#222230] border border-[#262632] text-gray-300 hover:text-white rounded-lg transition cursor-pointer"
                      title="Copy Custom M3U URL"
                    >
                      {copiedField === 'rawM3u' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* Quick Action Footer */}
            <div className="flex items-center justify-between pt-3 border-t border-[#262632] gap-3">
              <button
                onClick={() => {
                  const masterUrl = credsPortal.serverUrl && credsPortal.username && credsPortal.password
                    ? generateMasterM3uUrl(credsPortal.serverUrl, credsPortal.username, credsPortal.password)
                    : credsPortal.m3uUrl || '';
                  const allText = `Connection: ${credsPortal.name}\nServer: ${credsPortal.serverUrl}\nUser: ${credsPortal.username}\nPass: ${credsPortal.password}\nMaster M3U: ${masterUrl}`;
                  copyToClipboard(allText, 'all');
                }}
                className="px-4 py-2 bg-[#181822] hover:bg-[#20202c] border border-[#262632] text-gray-200 rounded-xl text-xs font-semibold flex items-center gap-2 transition cursor-pointer"
              >
                {copiedField === 'all' ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4 text-[#e50914]" />}
                <span>{copiedField === 'all' ? 'All Copied!' : 'Copy All Parameters'}</span>
              </button>

              <button
                onClick={() => setCredsPortal(null)}
                className="px-4 py-2 bg-[#e50914] hover:bg-[#ff1e27] text-white text-xs font-semibold rounded-xl transition cursor-pointer"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
