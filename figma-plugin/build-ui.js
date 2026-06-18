const fs = require('fs');
const path = require('path');
const https = require('https');

const srcDir = path.join(__dirname, 'src');
const distDir = path.join(__dirname, 'dist');

// Ensure dist directory exists
if (!fs.existsSync(distDir)){
    fs.mkdirSync(distDir, { recursive: true });
}

const srcUI = path.join(srcDir, 'ui.html');
const distUI = path.join(distDir, 'ui.html');
const localSupabase = path.join(srcDir, 'supabase.min.js');

function buildUI(supabaseCode) {
    if (fs.existsSync(srcUI)) {
        let html = fs.readFileSync(srcUI, 'utf8');

        // Inline the intro mockup image in base64 format
        const introImg = path.join(srcDir, 'intro-mockup.png');
        if (fs.existsSync(introImg)) {
            const imgData = fs.readFileSync(introImg);
            const base64Str = imgData.toString('base64');
            const dataUrl = `data:image/png;base64,${base64Str}`;
            if (html.includes('INTRO_MOCKUP_BASE64_PLACEHOLDER')) {
                html = html.replace('INTRO_MOCKUP_BASE64_PLACEHOLDER', dataUrl);
                console.log('Inlined intro mockup image in base64 format.');
            } else {
                console.warn('Warning: INTRO_MOCKUP_BASE64_PLACEHOLDER not found in ui.html.');
            }
        } else {
            console.warn('Warning: intro-mockup.png does not exist. Skipping image inlining.');
        }

        // Replace script tag with inline supabase code
        const targetTag = '<script src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2"></script>';
        if (html.includes(targetTag)) {
            html = html.replace(targetTag, `<script>${supabaseCode}</script>`);
            console.log('Bundled Supabase library inline.');
        } else {
            console.warn('Warning: Could not find external Supabase script tag to inline in ui.html.');
        }
        fs.writeFileSync(distUI, html, 'utf8');
        console.log('Successfully compiled ui.html to dist/ui.html');
    } else {
        console.error('Error: src/ui.html does not exist!');
    }
}

if (fs.existsSync(localSupabase)) {
    const code = fs.readFileSync(localSupabase, 'utf8');
    buildUI(code);
} else {
    console.log('supabase.min.js not found locally. Downloading from CDN...');
    const cdnUrl = 'https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2/dist/umd/supabase.min.js';
    
    function fetchUrl(url) {
        https.get(url, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                fetchUrl(res.headers.location);
            } else if (res.statusCode === 200) {
                const chunks = [];
                res.on('data', (chunk) => chunks.push(chunk));
                res.on('end', () => {
                    const buffer = Buffer.concat(chunks);
                    fs.writeFileSync(localSupabase, buffer);
                    console.log('Downloaded supabase.min.js successfully.');
                    buildUI(buffer.toString('utf8'));
                });
            } else {
                console.error(`Failed to download Supabase library from CDN (Status: ${res.statusCode})`);
            }
        }).on('error', (err) => {
            console.error('Error downloading Supabase:', err.message);
        });
    }
    fetchUrl(cdnUrl);
}
