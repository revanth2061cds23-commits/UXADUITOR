// ----------------------------------
// Supabase Client Initialization
// ----------------------------------
const SB_URL = "https://naneeovpzwyfnbaaujpi.supabase.co";
const SB_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5hbmVlb3Zwend5Zm5iYWF1anBpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODAxOTYwMzIsImV4cCI6MjA5NTc3MjAzMn0.Ilb6N52RvkwpiQ8iI0vGpIvDOysNgkubzXFh5sSUoUk";

const supabaseClient = window.supabase.createClient(SB_URL, SB_KEY);

// ----------------------------------
// Global App States
// ----------------------------------
let currentUser = null;
let activeAuthMode = 'signin';
let screensList = []; // Array of { id, file, blobUrl, sequenceIndex, tapX: -1, tapY: -1, width: 1080, height: 1920 }

// ----------------------------------
// Lifecycle & Auth State Management
// ----------------------------------
document.addEventListener('DOMContentLoaded', () => {
  // Listen for Supabase Authentication state changes
  supabaseClient.auth.onAuthStateChange((event, session) => {
    const user = session?.user || null;
    currentUser = user;
    updateAuthUI(user);
  });

  // Drag and drop listeners for the dropzone
  const dropzone = document.getElementById('dropzone');
  
  dropzone.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropzone.classList.add('dragover');
  });

  dropzone.addEventListener('dragleave', () => {
    dropzone.classList.remove('dragover');
  });

  dropzone.addEventListener('drop', (e) => {
    e.preventDefault();
    dropzone.classList.remove('dragover');
    if (e.dataTransfer.files.length > 0) {
      processFiles(e.dataTransfer.files);
    }
  });
});

// Update display state based on login status
function updateAuthUI(user) {
  const authPanelWrapper = document.getElementById('auth-panel-wrapper');
  const mainDashboard = document.getElementById('main-dashboard');
  const headerStatusDot = document.getElementById('header-status-dot');
  const headerStatusText = document.getElementById('header-status-text');

  if (user) {
    // Logged In
    stopWebQrPolling();
    authPanelWrapper.style.display = 'none';
    mainDashboard.style.display = 'grid';
    document.getElementById('user-email-display').innerText = user.email;
    
    headerStatusDot.className = 'status-indicator-dot online';
    headerStatusText.innerText = 'Connected';
    logStatus("Signed in as " + user.email);
  } else {
    // Logged Out
    authPanelWrapper.style.display = 'grid';
    mainDashboard.style.display = 'none';
    
    headerStatusDot.className = 'status-indicator-dot offline';
    headerStatusText.innerText = 'Disconnected';
    
    // Reset onboarding slider to first slide
    showWebSlide(0);
    switchAuthTab('signin');
    
    // Reset local data
    clearScreensQueue();
  }
}

// ----------------------------------
// Tab toggling & Auth Handlers
// ----------------------------------
function switchAuthTab(mode) {
  activeAuthMode = mode;
  document.getElementById('tab-signin').classList.remove('active');
  document.getElementById('tab-signup').classList.remove('active');
  document.getElementById('tab-qr').classList.remove('active');
  document.getElementById(`tab-${mode}`).classList.add('active');
  
  hideAuthAlert();
  stopWebQrPolling();

  if (mode === 'qr') {
    document.getElementById('email-auth-inputs').style.display = 'none';
    document.getElementById('web-qr-login-view').style.display = 'flex';
    startWebQrPolling();
  } else {
    document.getElementById('web-qr-login-view').style.display = 'none';
    document.getElementById('email-auth-inputs').style.display = 'block';
    document.getElementById('btn-auth').innerText = mode === 'signin' ? 'Sign In' : 'Sign Up';
  }
}

function showAuthAlert(msg, isSuccess = false) {
  const alertBox = document.getElementById('auth-alert');
  alertBox.innerText = msg;
  alertBox.style.display = 'block';
  alertBox.className = isSuccess ? 'alert-box success' : 'alert-box error';
}

function hideAuthAlert() {
  document.getElementById('auth-alert').style.display = 'none';
}

// Handle login/signup buttons
async function handleAuth() {
  hideAuthAlert();
  const email = document.getElementById('auth-email').value.trim();
  const password = document.getElementById('auth-password').value;

  if (!email || !password) {
    showAuthAlert("Email and password fields are required.");
    return;
  }

  const btn = document.getElementById('btn-auth');
  btn.disabled = true;
  btn.innerText = activeAuthMode === 'signin' ? 'Signing in...' : 'Registering...';

  try {
    if (activeAuthMode === 'signin') {
      const { data, error } = await supabaseClient.auth.signInWithPassword({ email, password });
      if (error) throw error;
      showAuthAlert("Sign in successful!", true);
    } else {
      const { data, error } = await supabaseClient.auth.signUp({
        email,
        password,
        options: {
          emailRedirectTo: 'https://figma.com'
        }
      });
      if (error) throw error;
      
      // Auto-toggle to sign in with success feedback (if email confirmation is turned off, Supabase automatically logs in)
      if (data.session) {
        showAuthAlert("Account created and signed in successfully!", true);
      } else {
        switchAuthTab('signin');
        showAuthAlert("Account created successfully! Please sign in with your credentials.", true);
      }
    }
  } catch (err) {
    console.error(err);
    showAuthAlert(err.message || err.toString());
  } finally {
    btn.disabled = false;
    btn.innerText = activeAuthMode === 'signin' ? 'Sign In' : 'Sign Up';
  }
}

async function handleSignOut() {
  try {
    await supabaseClient.auth.signOut();
  } catch (e) {
    console.error(e);
  }
}

// ----------------------------------
// Files Management & Dropzone Controls
// ----------------------------------
function triggerFileSelect() {
  document.getElementById('file-input').click();
}

function handleFileSelect(e) {
  if (e.target.files.length > 0) {
    processFiles(e.target.files);
  }
}

// Processes file array into local state queue
function processFiles(filesList) {
  for (let i = 0; i < filesList.length; i++) {
    const file = filesList[i];
    if (!file.type.startsWith('image/')) continue;

    const blobUrl = URL.createObjectURL(file);
    const screenItem = {
      id: crypto.randomUUID(),
      file: file,
      blobUrl: blobUrl,
      sequenceIndex: screensList.length,
      tapX: -1,
      tapY: -1,
      width: 1080, // Default placeholders, will read actual dimensions on load
      height: 1920
    };

    // Load image properties in background to fetch actual dimensions
    const tempImg = new Image();
    tempImg.onload = () => {
      screenItem.width = tempImg.naturalWidth || 1080;
      screenItem.height = tempImg.naturalHeight || 1920;
    };
    tempImg.src = blobUrl;

    screensList.push(screenItem);
  }
  renderScreensQueue();
  validateSyncState();
}

// ----------------------------------
// Queue Operations (Reordering / Clicking hotspots)
// ----------------------------------
function removeScreen(id) {
  const index = screensList.findIndex(s => s.id === id);
  if (index !== -1) {
    // Revoke URL to prevent memory leaks
    URL.revokeObjectURL(screensList[index].blobUrl);
    screensList.splice(index, 1);
    
    // Recalculate sequences
    recalculateSequenceIndexes();
    renderScreensQueue();
    validateSyncState();
  }
}

function moveScreen(index, direction) {
  const targetIndex = index + direction;
  if (targetIndex >= 0 && targetIndex < screensList.length) {
    // Swap items
    const temp = screensList[index];
    screensList[index] = screensList[targetIndex];
    screensList[targetIndex] = temp;
    
    recalculateSequenceIndexes();
    renderScreensQueue();
  }
}

function recalculateSequenceIndexes() {
  screensList.forEach((screen, idx) => {
    screen.sequenceIndex = idx;
  });
}

// Interactive tap coordinates selector
function handleImageClick(e, screenId) {
  const rect = e.currentTarget.getBoundingClientRect();
  
  // Calculate relative coordinate percentages
  const offsetX = e.clientX - rect.left;
  const offsetY = e.clientY - rect.top;
  
  const tapX = Number((offsetX / rect.width).toFixed(4));
  const tapY = Number((offsetY / rect.height).toFixed(4));
  
  const screen = screensList.find(s => s.id === screenId);
  if (screen) {
    // If click is near an existing hotspot, clear it
    if (screen.tapX !== -1 && Math.abs(screen.tapX - tapX) < 0.05 && Math.abs(screen.tapY - tapY) < 0.05) {
      screen.tapX = -1;
      screen.tapY = -1;
      logStatus(`Cleared tap hotspot on screen #${screen.sequenceIndex + 1}`);
    } else {
      screen.tapX = tapX;
      screen.tapY = tapY;
      logStatus(`Set hotspot on screen #${screen.sequenceIndex + 1} at: X:${Math.round(tapX * 100)}%, Y:${Math.round(tapY * 100)}%`);
    }
    renderScreensQueue();
  }
}

function clearScreensQueue() {
  screensList.forEach(s => URL.revokeObjectURL(s.blobUrl));
  screensList = [];
  renderScreensQueue();
  validateSyncState();
}

// Renders the screenshot grid list to the screen
function renderScreensQueue() {
  const gridContainer = document.getElementById('screens-grid-container');
  const emptyPreview = document.getElementById('empty-preview-state');
  const countBadge = document.getElementById('flow-count');
  const tipBanner = document.getElementById('interaction-tip');
  
  countBadge.innerText = `${screensList.length} screen${screensList.length === 1 ? '' : 's'}`;
  
  if (screensList.length === 0) {
    gridContainer.style.display = 'none';
    emptyPreview.style.display = 'flex';
    tipBanner.style.display = 'none';
    return;
  }

  gridContainer.style.display = 'grid';
  emptyPreview.style.display = 'none';
  tipBanner.style.display = 'block';
  
  gridContainer.innerHTML = '';
  
  screensList.forEach((screen, index) => {
    const card = document.createElement('div');
    card.className = 'screen-card';
    
    // Draw hotspot indicator if set
    let hotspotHtml = '';
    if (screen.tapX !== -1 && screen.tapY !== -1) {
      hotspotHtml = `<div class="hotspot-dot" style="left: ${screen.tapX * 100}%; top: ${screen.tapY * 100}%;"></div>`;
    }

    card.innerHTML = `
      <div class="screen-image-wrapper" onclick="handleImageClick(event, '${screen.id}')">
        <img src="${screen.blobUrl}" class="screen-image" alt="Screen ${index + 1}">
        ${hotspotHtml}
      </div>
      <div class="screen-card-toolbar">
        <span class="screen-badge">#${index + 1}</span>
        <div class="toolbar-btn-group">
          <button class="tool-btn" onclick="moveScreen(${index}, -1)" ${index === 0 ? 'disabled' : ''} title="Move Left">←</button>
          <button class="tool-btn" onclick="moveScreen(${index}, 1)" ${index === screensList.length - 1 ? 'disabled' : ''} title="Move Right">→</button>
          <button class="tool-btn delete-btn" onclick="removeScreen('${screen.id}')" title="Delete">✕</button>
        </div>
      </div>
    `;
    
    gridContainer.appendChild(card);
  });
}

function validateSyncState() {
  const syncBtn = document.getElementById('btn-sync-session');
  syncBtn.disabled = screensList.length === 0;
}

// ----------------------------------
// Supabase Flow Syncing Module
// ----------------------------------
async function syncFlowToSupabase() {
  const flowName = document.getElementById('session-flow-name').value.trim();
  const deviceModel = document.getElementById('session-device-model').value.trim() || "Web Uploader";

  if (!flowName) {
    alert("Please enter a Session Flow Name before syncing.");
    document.getElementById('session-flow-name').focus();
    return;
  }

  if (screensList.length === 0) {
    alert("Please add at least one screenshot to sync.");
    return;
  }

  const syncBtn = document.getElementById('btn-sync-session');
  const fileInput = document.getElementById('file-input');
  const flowNameInput = document.getElementById('session-flow-name');
  
  // Disable UI inputs during sync
  syncBtn.disabled = true;
  flowNameInput.disabled = true;
  showProgress(true);
  
  const sessionId = crypto.randomUUID();
  const userUuid = currentUser.id;
  const headerStatusDot = document.getElementById('header-status-dot');
  
  headerStatusDot.className = 'status-indicator-dot loading';
  
  try {
    updateProgress(10, "Creating session on database...");
    
    // 1. Create a session row on 'sessions'
    // Width and height are read from the first screen if available, otherwise defaults to 1080x1920
    const firstScreen = screensList[0];
    const screenWidth = firstScreen ? firstScreen.width : 1080;
    const screenHeight = firstScreen ? firstScreen.height : 1920;
    
    const { error: sessionError } = await supabaseClient
      .from('sessions')
      .insert({
        id: sessionId,
        user_id: userUuid,
        flow_name: flowName,
        device_model: deviceModel,
        screen_width_px: screenWidth,
        screen_height_px: screenHeight,
        status: 'uploading'
      });

    if (sessionError) throw sessionError;

    // 2. Loop and upload screen binary assets to Storage, then insert into 'screens'
    for (let i = 0; i < screensList.length; i++) {
      const screen = screensList[i];
      const stepPct = 20 + Math.round((i / screensList.length) * 70);
      updateProgress(stepPct, `Uploading screen ${i + 1}/${screensList.length}...`);

      const fileExtension = screen.file.name.split('.').pop() || 'png';
      const remotePath = `${userUuid}/${sessionId}/${screen.sequenceIndex}.${fileExtension}`;
      
      // Upload file binary directly to 'screens' storage bucket
      const { data: uploadData, error: uploadError } = await supabaseClient.storage
        .from('screens')
        .upload(remotePath, screen.file, {
          cacheControl: '3600',
          upsert: true
        });

      if (uploadError) throw uploadError;

      // Construct public link URL
      const { data: { publicUrl } } = supabaseClient.storage
        .from('screens')
        .getPublicUrl(remotePath);

      // Insert record row into 'screens' database table
      const { error: screenError } = await supabaseClient
        .from('screens')
        .insert({
          id: screen.id,
          session_id: sessionId,
          sequence_index: screen.sequenceIndex,
          image_url: publicUrl,
          tap_x_pct: screen.tapX !== -1 ? screen.tapX : -1,
          tap_y_pct: screen.tapY !== -1 ? screen.tapY : -1
        });

      if (screenError) throw screenError;
    }

    // 3. Complete Session
    updateProgress(95, "Completing flow mapping...");
    const isoTimestamp = new Date().toISOString();
    
    const { error: completeError } = await supabaseClient
      .from('sessions')
      .update({
        status: 'complete',
        completed_at: isoTimestamp
      })
      .eq('id', sessionId);

    if (completeError) throw completeError;

    // Success Completion
    updateProgress(100, "Successfully synced to Figma!");
    logStatus("Flow synced! Open your Figma plugin to load '" + flowName + "'");
    alert(`🎉 Flow "${flowName}" synced successfully! Open the Figma plugin to import.`);
    
    // Reset view form
    document.getElementById('session-flow-name').value = '';
    clearScreensQueue();

  } catch (err) {
    console.error(err);
    logStatus("Error: " + (err.message || err.toString()));
    alert("Sync failed: " + (err.message || err.toString()));
  } finally {
    // Re-enable UI inputs
    syncBtn.disabled = false;
    flowNameInput.disabled = false;
    headerStatusDot.className = 'status-indicator-dot online';
    showProgress(false);
  }
}

// ----------------------------------
// UI Progress and Logging Utilities
// ----------------------------------
function showProgress(show) {
  document.getElementById('upload-progress-container').style.display = show ? 'block' : 'none';
}

function updateProgress(pct, message) {
  document.getElementById('upload-progress-fill').style.width = `${pct}%`;
  document.getElementById('upload-progress-val').innerText = `${pct}%`;
  document.getElementById('upload-progress-label').innerText = message;
  logStatus(message);
}

function logStatus(msg) {
  document.getElementById('status-logger').innerText = msg;
}

// ----------------------------------
// Web Onboarding Slide Logic
// ----------------------------------
let activeWebSlide = 0;

function showWebSlide(slideIndex) {
  const slides = document.querySelectorAll('.web-onboarding-slide');
  const dots = document.querySelectorAll('.slide-dot');
  if (slides.length === 0) return;

  slides.forEach(s => s.classList.remove('active'));
  dots.forEach(d => d.classList.remove('active'));

  activeWebSlide = slideIndex;
  slides[slideIndex].classList.add('active');
  dots[slideIndex].classList.add('active');
}

// Auto transition slides every 4 seconds
let onboardingInterval = setInterval(() => {
  const slides = document.querySelectorAll('.web-onboarding-slide');
  const wrapper = document.getElementById('auth-panel-wrapper');
  if (slides.length > 0 && wrapper && wrapper.style.display !== 'none') {
    const nextIdx = (activeWebSlide + 1) % slides.length;
    showWebSlide(nextIdx);
  }
}, 4000);

// ----------------------------------
// Web QR Code Login (Polling)
// ----------------------------------
let webQrPollInterval = null;
let webPairingToken = null;

async function startWebQrPolling() {
  stopWebQrPolling();
  if (!supabaseClient) return;

  document.getElementById('web-qr-code-img').style.display = 'none';
  document.getElementById('web-qr-loading-spinner').style.display = 'block';
  document.getElementById('web-qr-status').innerText = "Generating pairing token...";
  document.getElementById('web-qr-status').style.color = "var(--color-text-muted)";

  webPairingToken = crypto.randomUUID();

  try {
    const { error } = await supabaseClient
      .from('qr_pairings')
      .insert({
        token: webPairingToken,
        status: 'pending'
      });

    if (error) throw error;

    const qrImg = document.getElementById('web-qr-code-img');
    qrImg.src = `https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${webPairingToken}`;
    qrImg.onload = () => {
      document.getElementById('web-qr-loading-spinner').style.display = 'none';
      qrImg.style.display = 'block';
      document.getElementById('web-qr-status').innerText = "Ready to scan";
      document.getElementById('web-qr-status').style.color = "var(--color-brand)";
    };

    webQrPollInterval = setInterval(async () => {
      if (!webPairingToken) return;

      const { data, error: pollError } = await supabaseClient
        .from('qr_pairings')
        .select('*')
        .eq('token', webPairingToken)
        .maybeSingle();

      if (pollError) {
        console.error("Web QR polling error:", pollError);
        return;
      }

      if (data && data.status === 'paired') {
        stopWebQrPolling();
        document.getElementById('web-qr-status').innerText = "Scanned! Logging in...";
        document.getElementById('web-qr-status').style.color = "var(--color-success, #10b981)";

        const { data: sessionData, error: sessionError } = await supabaseClient.auth.setSession({
          access_token: data.access_token,
          refresh_token: data.refresh_token || ''
        });

        if (sessionError) {
          showAuthAlert("Session synchronization failed: " + sessionError.message);
          return;
        }

        await supabaseClient
          .from('qr_pairings')
          .delete()
          .eq('token', data.token);
      }
    }, 2000);

  } catch (err) {
    console.error("Web QR login setup error:", err);
    document.getElementById('web-qr-loading-spinner').style.display = 'none';
    document.getElementById('web-qr-status').innerText = "Failed to load QR code";
    document.getElementById('web-qr-status').style.color = "red";
  }
}

function stopWebQrPolling() {
  if (webQrPollInterval) {
    clearInterval(webQrPollInterval);
    webQrPollInterval = null;
  }
  webPairingToken = null;
}

// ----------------------------------
// Mobile QR Scanner Camera Interface
// ----------------------------------
let html5QrScanner = null;

function openScanner() {
  document.getElementById('scanner-modal').style.display = 'flex';
  document.getElementById('scanner-status-text').innerText = "Accessing camera...";
  document.getElementById('scanner-status-text').style.color = "var(--color-text-muted)";

  html5QrScanner = new Html5Qrcode("qr-reader");

  html5QrScanner.start(
    { facingMode: "environment" },
    {
      fps: 10,
      qrbox: { width: 220, height: 220 }
    },
    async (decodedText, decodedResult) => {
      console.log("Scanned QR Text:", decodedText);
      document.getElementById('scanner-status-text').innerText = "Code scanned! Linking...";
      document.getElementById('scanner-status-text').style.color = "var(--color-brand)";

      const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
      if (!uuidRegex.test(decodedText.trim())) {
        document.getElementById('scanner-status-text').innerText = "Invalid QR code format.";
        document.getElementById('scanner-status-text').style.color = "var(--color-error)";
        return;
      }

      try {
        await closeScanner();

        const session = (await supabaseClient.auth.getSession()).data.session;
        if (!session) {
          alert("Your session has expired. Please log in again.");
          return;
        }

        const { error } = await supabaseClient
          .from('qr_pairings')
          .update({
            status: 'paired',
            user_id: session.user.id,
            email: session.user.email,
            access_token: session.access_token,
            refresh_token: session.refresh_token
          })
          .eq('token', decodedText.trim());

        if (error) throw error;

        alert("🎉 Device paired successfully! The desktop device should log in instantly.");

      } catch (pairErr) {
        console.error("Pairing failed:", pairErr);
        alert("Pairing failed: " + (pairErr.message || pairErr));
      }
    },
    (errorMessage) => {
      // Scan failure callback
    }
  ).then(() => {
    document.getElementById('scanner-status-text').innerText = "Position QR code within the frame";
  }).catch(err => {
    console.error("Failed to start camera scanner:", err);
    document.getElementById('scanner-status-text').innerText = "Camera Access Error: " + err;
    document.getElementById('scanner-status-text').style.color = "var(--color-error)";
  });
}

async function closeScanner() {
  if (html5QrScanner) {
    try {
      if (html5QrScanner.isScanning) {
        await html5QrScanner.stop();
      }
    } catch (err) {
      console.error("Error stopping scanner:", err);
    }
    html5QrScanner = null;
  }
  document.getElementById('scanner-modal').style.display = 'none';
}
