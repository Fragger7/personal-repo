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
  Check
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
import { testXtreamConnection, type XtreamAuthResult } from './lib/xtream';

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [portals, setPortals] = useState<XtreamPortal[]>([]);
  
  // Auth Form State
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [authError, setAuthError] = useState<string | null>(null);
  const [isRegistering, setIsRegistering] = useState(false);

  // Add Portal Modal/Form State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [name, setName] = useState('');
  const [serverUrl, setServerUrl] = useState('');
  const [username, setUsername] = useState('');
  const [portalPassword, setPortalPassword] = useState('');
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<XtreamAuthResult | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

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

  const handleTestConnection = async () => {
    if (!serverUrl || !username || !portalPassword) {
      setTestResult({ success: false, message: 'Please enter URL, Username, and Password first.' });
      return;
    }
    setTesting(true);
    setTestResult(null);
    const res = await testXtreamConnection(serverUrl, username, portalPassword);
    setTesting(false);
    setTestResult(res);
  };

  const handleSavePortal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) return;
    if (!name || !serverUrl || !username || !portalPassword) return;

    setSaving(true);
    try {
      await addPortal({
        userId: user.uid,
        name: name.trim(),
        serverUrl: serverUrl.trim().replace(/\/+$/, ''),
        username: username.trim(),
        password: portalPassword.trim(),
        type: 'xtream',
        isActive: true,
        createdAt: Date.now(),
        status: testResult?.success ? 'online' : 'unknown',
        expiryDate: testResult?.expDate || undefined
      });

      // Reset form
      setName('');
      setServerUrl('');
      setUsername('');
      setPortalPassword('');
      setTestResult(null);
      setIsModalOpen(false);
    } catch (err) {
      console.error(err);
      alert('Failed to save portal to Firestore.');
    } finally {
      setSaving(false);
    }
  };

  const handleToggleActive = async (portal: XtreamPortal) => {
    if (!portal.id) return;
    await updatePortal(portal.id, { isActive: !portal.isActive });
  };

  const handleDelete = async (id: string, name: string) => {
    if (window.confirm(`Delete portal "${name}"?`)) {
      await deletePortal(id);
    }
  };

  const handleCopyCredentials = (portal: XtreamPortal) => {
    const text = `Server: ${portal.serverUrl}\nUser: ${portal.username}\nPass: ${portal.password}`;
    navigator.clipboard.writeText(text);
    if (portal.id) {
      setCopiedId(portal.id);
      setTimeout(() => setCopiedId(null), 2000);
    }
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
        {/* Background glow */}
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
              className="text-xs text-gray-400 hover:text-[#ff1e27] transition"
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
                <p className="text-xs text-gray-400 font-medium uppercase tracking-wider">Total Portals</p>
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
                <p className="text-xs text-gray-400 font-medium uppercase tracking-wider">Active for TV Sync</p>
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
                <p className="text-xs text-gray-400 font-medium uppercase tracking-wider">Cloud Sync State</p>
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
            <h2 className="text-xl font-bold text-white tracking-wide">Configured IPTV Portals</h2>
            <p className="text-xs text-gray-400 mt-0.5">
              These credentials are encrypted and stored in Firestore for your Android TV & Mobile devices.
            </p>
          </div>

          <button
            onClick={() => setIsModalOpen(true)}
            className="flex items-center justify-center gap-2 px-4 py-2.5 bg-[#e50914] hover:bg-[#ff1e27] text-white font-semibold rounded-xl text-sm transition shadow-lg shadow-[#e50914]/25 cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>Add New Xtream Portal</span>
          </button>
        </div>

        {/* Portal Cards Grid */}
        {portals.length === 0 ? (
          <div className="border border-dashed border-[#262632] rounded-2xl p-12 text-center bg-[#121217]/50">
            <Server className="w-12 h-12 text-gray-600 mx-auto mb-3" />
            <h3 className="text-base font-semibold text-gray-300">No Portals Added Yet</h3>
            <p className="text-xs text-gray-500 max-w-sm mx-auto mt-1 mb-6">
              Add your first Xtream Codes provider server URL, username, and password. It will sync directly to your TV app.
            </p>
            <button
              onClick={() => setIsModalOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-[#e50914] hover:bg-[#ff1e27] text-white text-xs font-semibold rounded-lg transition"
            >
              <Plus className="w-4 h-4" /> Add Portal
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {portals.map((p) => (
              <div 
                key={p.id} 
                className={`bg-[#121217] border ${p.isActive ? 'border-[#262632]' : 'border-gray-800 opacity-60'} rounded-2xl p-5 flex flex-col justify-between transition-all hover:border-[#383848]`}
              >
                <div>
                  <div className="flex items-start justify-between gap-3 mb-3">
                    <div className="flex items-center gap-2.5">
                      <div className="w-3 h-3 rounded-full bg-emerald-400"></div>
                      <h3 className="font-bold text-base text-white">{p.name}</h3>
                      <span className="text-[10px] uppercase font-mono px-2 py-0.5 rounded bg-[#181822] text-gray-400 border border-[#262632]">
                        {p.type}
                      </span>
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleToggleActive(p)}
                        title={p.isActive ? "Active (Synced to TV)" : "Inactive (Hidden from TV)"}
                        className={`text-xs px-2.5 py-1 rounded-full font-medium transition cursor-pointer ${
                          p.isActive 
                            ? 'bg-emerald-950/60 text-emerald-300 border border-emerald-800' 
                            : 'bg-gray-800 text-gray-400'
                        }`}
                      >
                        {p.isActive ? 'Sync Active' : 'Paused'}
                      </button>

                      <button
                        onClick={() => p.id && handleDelete(p.id, p.name)}
                        className="p-1.5 text-gray-500 hover:text-red-400 hover:bg-[#181822] rounded-lg transition cursor-pointer"
                        title="Delete Portal"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>

                  <div className="bg-[#181822] rounded-xl p-3 space-y-2 border border-[#262632]/80 text-xs font-mono">
                    <div className="flex items-center justify-between text-gray-400">
                      <span className="text-gray-500">Host:</span>
                      <span className="text-gray-200 truncate max-w-[280px]">{p.serverUrl}</span>
                    </div>
                    <div className="flex items-center justify-between text-gray-400">
                      <span className="text-gray-500">User:</span>
                      <span className="text-gray-200">{p.username}</span>
                    </div>
                    {p.expiryDate && (
                      <div className="flex items-center justify-between text-gray-400">
                        <span className="text-gray-500">Expiry:</span>
                        <span className="text-amber-400">{p.expiryDate}</span>
                      </div>
                    )}
                  </div>
                </div>

                <div className="mt-4 pt-3 border-t border-[#262632] flex items-center justify-between text-xs">
                  <span className="text-gray-500">
                    Added {new Date(p.createdAt).toLocaleDateString()}
                  </span>

                  <button
                    onClick={() => handleCopyCredentials(p)}
                    className="flex items-center gap-1.5 text-gray-400 hover:text-white transition cursor-pointer"
                  >
                    {copiedId === p.id ? (
                      <>
                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                        <span className="text-emerald-400">Copied</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5" />
                        <span>Copy Login</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Android TV Pairing Information Card */}
        <div className="bg-gradient-to-r from-[#121217] to-[#181822] border border-[#262632] rounded-2xl p-6">
          <div className="flex items-start gap-4">
            <div className="p-3 bg-[#e50914]/20 border border-[#e50914]/30 rounded-xl text-[#ff1e27] shrink-0">
              <Tv className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-bold text-white text-base">Instant Android TV Sync Architecture</h3>
              <p className="text-xs text-gray-400 mt-1 leading-relaxed">
                When you install TVMime on your Android TV or Fire TV stick, it automatically authenticates with your Firebase Project 
                (<span className="text-gray-200 font-mono">tvmime-65909</span>) and streams these portal credentials into its local SQLite database.
                You will never need to type usernames or passwords using a TV remote!
              </p>
            </div>
          </div>
        </div>
      </main>

      {/* Add Portal Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="relative w-full max-w-lg bg-[#121217] border border-[#262632] rounded-2xl p-6 shadow-2xl space-y-5">
            <div className="flex items-center justify-between border-b border-[#262632] pb-4">
              <div className="flex items-center gap-2.5">
                <div className="p-2 bg-[#e50914] rounded-lg text-white">
                  <Server className="w-4 h-4" />
                </div>
                <h3 className="font-bold text-base text-white">Add Xtream Codes Portal</h3>
              </div>
              <button 
                onClick={() => setIsModalOpen(false)}
                className="text-gray-400 hover:text-white p-1 rounded-lg transition"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSavePortal} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">
                  Portal Name / Nickname
                </label>
                <input 
                  type="text" 
                  value={name} 
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Apex 4K Streams"
                  required
                  className="w-full bg-[#181822] border border-[#262632] focus:border-[#e50914] focus:outline-none rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">
                  Server URL
                </label>
                <input 
                  type="text" 
                  value={serverUrl} 
                  onChange={(e) => setServerUrl(e.target.value)}
                  placeholder="http://provider-dns.me:8080"
                  required
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
                    required
                    className="w-full bg-[#181822] border border-[#262632] focus:border-[#e50914] focus:outline-none rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-500 font-mono"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">
                    Password
                  </label>
                  <input 
                    type="password" 
                    value={portalPassword} 
                    onChange={(e) => setPortalPassword(e.target.value)}
                    placeholder="••••••••"
                    required
                    className="w-full bg-[#181822] border border-[#262632] focus:border-[#e50914] focus:outline-none rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-500 font-mono"
                  />
                </div>
              </div>

              {/* Test Result Indicator */}
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
                    onClick={() => setIsModalOpen(false)}
                    className="px-4 py-2.5 text-gray-400 hover:text-white text-xs font-semibold rounded-xl transition"
                  >
                    Cancel
                  </button>

                  <button
                    type="submit"
                    disabled={saving}
                    className="px-5 py-2.5 bg-[#e50914] hover:bg-[#ff1e27] text-white text-xs font-semibold rounded-xl transition shadow-lg shadow-[#e50914]/25 cursor-pointer"
                  >
                    {saving ? 'Saving...' : 'Save Portal'}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
export default App;
