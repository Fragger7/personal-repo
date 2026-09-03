export default function handler(req: any, res: any) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', 'public, max-age=60, s-maxage=60');

  res.status(200).json({
    versionCode: 1,
    versionName: '1.0.0',
    tvApkUrl: 'https://github.com/Fragger7/personal-repo/releases/download/latest/tvmime-tv.apk',
    mobileApkUrl: 'https://github.com/Fragger7/personal-repo/releases/download/latest/tvmime-mobile.apk',
    changelog: 'Initial TVMime baseline with streaming catalog parser, cloud sync, and in-place OTA updater.'
  });
}
