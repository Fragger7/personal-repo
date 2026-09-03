export interface XtreamAuthResult {
  success: boolean;
  message: string;
  status?: string;
  expDate?: string;
  activeCons?: string;
  maxCons?: string;
  serverTime?: string;
}

export async function testXtreamConnection(
  serverUrl: string, 
  user: string, 
  pass: string
): Promise<XtreamAuthResult> {
  // 1. Try serverless proxy first (bypasses browser CORS & mixed-content on Vercel)
  try {
    const proxyUrl = `/api/test-portal?serverUrl=${encodeURIComponent(serverUrl)}&username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}`;
    const proxyRes = await fetch(proxyUrl, { method: 'GET' });
    if (proxyRes.ok) {
      const proxyData = await proxyRes.json();
      return proxyData;
    }
  } catch {
    // Fall back to direct browser fetch (e.g. in local Vite dev mode)
  }

  // 2. Direct browser fetch fallback
  try {
    let cleanUrl = serverUrl.trim();
    if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://')) {
      cleanUrl = `http://${cleanUrl}`;
    }
    cleanUrl = cleanUrl.replace(/\/+$/, '');

    const apiUrl = `${cleanUrl}/player_api.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}`;

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);

    const response = await fetch(apiUrl, {
      method: 'GET',
      signal: controller.signal,
      headers: {
        'Accept': 'application/json',
      }
    });

    clearTimeout(timeoutId);


    if (!response.ok) {
      return {
        success: false,
        message: `Server returned HTTP ${response.status} ${response.statusText}`
      };
    }

    const data = await response.json();
    if (data && data.user_info) {
      const userInfo = data.user_info;
      if (userInfo.auth === 1 && userInfo.status === 'Active') {
        const expDate = userInfo.exp_date ? new Date(parseInt(userInfo.exp_date, 10) * 1000).toLocaleDateString() : 'Unlimited';
        return {
          success: true,
          message: 'Connection successful & credentials active!',
          status: userInfo.status,
          expDate,
          activeCons: String(userInfo.active_cons || 0),
          maxCons: String(userInfo.max_connections || 1),
        };
      } else {
        return {
          success: false,
          message: `Authentication failed: ${userInfo.message || userInfo.status || 'Invalid credentials'}`
        };
      }
    }

    return {
      success: false,
      message: 'Unrecognized server response format.'
    };
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    if (errorMsg.includes('abort')) {
      return {
        success: false,
        message: 'Connection timed out after 6 seconds.'
      };
    }
    if (errorMsg.includes('Failed to fetch') || errorMsg.includes('NetworkError')) {
      return {
        success: false,
        message: 'Network error or browser CORS restriction. (Credential can still be saved for native TV/Mobile app).'
      };
    }
    return {
      success: false,
      message: `Verification error: ${errorMsg}`
    };
  }
}

export function parseM3uUrl(url: string): {
  isXtream: boolean;
  serverUrl?: string;
  username?: string;
  password?: string;
} {
  try {
    const trimmed = url.trim();
    const parsed = new URL(trimmed.startsWith('http://') || trimmed.startsWith('https://') ? trimmed : `http://${trimmed}`);
    const username = parsed.searchParams.get('username') || parsed.searchParams.get('user');
    const password = parsed.searchParams.get('password') || parsed.searchParams.get('pass');
    
    if (username && password) {
      const serverUrl = `${parsed.protocol}//${parsed.host}`;
      return { isXtream: true, serverUrl, username, password };
    }
    return { isXtream: false };
  } catch {
    return { isXtream: false };
  }
}

export function generateMasterM3uUrl(serverUrl: string, username: string, password: string): string {
  let clean = serverUrl.trim();
  if (!clean.startsWith('http://') && !clean.startsWith('https://')) {
    clean = `http://${clean}`;
  }
  clean = clean.replace(/\/+$/, '');
  return `${clean}/get.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&type=m3u_plus&output=ts`;
}

