const https = require('https');
const fs = require('fs');

https.get('https://raw.githubusercontent.com/Fragger7/personal-repo/51c4d80/daily-push/src/App.tsx', (res) => {
  let data = '';
  res.on('data', (chunk) => data += chunk);
  res.on('end', () => {
    fs.writeFileSync('src/App.tsx.backup', data);
    console.log('Restored to src/App.tsx.backup');
  });
});
