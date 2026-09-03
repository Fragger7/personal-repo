export default function handler(req: any, res: any) {
  res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
  // 302 redirect directly to the latest GitHub release APK
  res.redirect(302, 'https://github.com/Fragger7/personal-repo/releases/download/latest/tvmime-tv.apk');
}
