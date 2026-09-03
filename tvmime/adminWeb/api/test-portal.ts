export default async function handler(req: any, res: any) {
  // Set CORS headers
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,POST');
  res.setHeader(
    'Access-Control-Allow-Headers',
    'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version'
  );

  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }

  const { serverUrl, username, password } = req.query;

  if (!serverUrl || !username || !password) {
    return res.status(400).json({ success: false, message: 'Missing serverUrl, username, or password' });
  }

  try {
    let cleanUrl = String(serverUrl).trim();
    if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://')) {
      cleanUrl = `http://${cleanUrl}`;
    }
    cleanUrl = cleanUrl.replace(/\/+$/, '');

    const targetUrl = `${cleanUrl}/player_api.php?username=${encodeURIComponent(String(username))}&password=${encodeURIComponent(String(password))}`;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 7000);

    const response = await fetch(targetUrl, {
      method: 'GET',
      signal: controller.signal,
      headers: {
        'User-Agent': 'IPTVSmartersPro/1.1.1',
        'Accept': 'application/json'
      }
    });

    clearTimeout(timeout);

    if (!response.ok) {
      return res.status(200).json({ 
        success: false, 
        message: `IPTV server returned HTTP ${response.status} ${response.statusText}` 
      });
    }

    const data = await response.json();
    if (data && data.user_info) {
      const u = data.user_info;
      if (u.auth === 1 && u.status === 'Active') {
        const expDate = u.exp_date ? new Date(parseInt(u.exp_date, 10) * 1000).toLocaleDateString() : 'Unlimited';
        return res.status(200).json({
          success: true,
          message: 'Connection verified via server proxy!',
          status: u.status,
          expDate,
          activeCons: String(u.active_cons || 0),
          maxCons: String(u.max_connections || 1)
        });
      } else {
        return res.status(200).json({
          success: false,
          message: `Auth failed: ${u.message || u.status || 'Invalid credentials'}`
        });
      }
    }

    return res.status(200).json({ success: false, message: 'Unrecognized IPTV provider response format.' });
  } catch (err: any) {
    return res.status(200).json({ 
      success: false, 
      message: err?.name === 'AbortError' ? 'Connection timed out (7s)' : `Proxy error: ${err?.message || err}` 
    });
  }
}
