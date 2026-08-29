import fs from 'fs';

const filePath = 'src/App.tsx';
let content = fs.readFileSync(filePath, 'utf-8');

content = content.replace(/fill="#10b981"/g, 'fill="#F7B600"'); 
content = content.replace(/fill="#6366f1"/g, 'fill="#1434CB"');

fs.writeFileSync(filePath, content, 'utf-8');
console.log('Radar chart updated');
