const fs = require('fs');
const path = require('path');

const srcDir = path.join(__dirname, 'src');
const distDir = path.join(__dirname, 'dist');

// Ensure dist directory exists
if (!fs.existsSync(distDir)){
    fs.mkdirSync(distDir, { recursive: true });
}

// Copy ui.html to dist/ui.html
const srcUI = path.join(srcDir, 'ui.html');
const distUI = path.join(distDir, 'ui.html');

if (fs.existsSync(srcUI)) {
    fs.copyFileSync(srcUI, distUI);
    console.log('Successfully copied ui.html to dist/ui.html');
} else {
    console.error('Error: src/ui.html does not exist!');
}
