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
  const authPanel = document.getElementById('auth-panel');
  const mainDashboard = document.getElementById('main-dashboard');
  const headerStatusDot = document.getElementById('header-status-dot');
  const headerStatusText = document.getElementById('header-status-text');

  if (user) {
    // Logged In
    authPanel.style.display = 'none';
    mainDashboard.style.display = 'grid';
    document.getElementById('user-email-display').innerText = user.email;
    
    headerStatusDot.className = 'status-indicator-dot online';
    headerStatusText.innerText = 'Connected';
    logStatus("Signed in as " + user.email);
  } else {
    // Logged Out
    authPanel.style.display = 'block';
    mainDashboard.style.display = 'none';
    
    headerStatusDot.className = 'status-indicator-dot offline';
    headerStatusText.innerText = 'Disconnected';
    
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
  document.getElementById(`tab-${mode}`).classList.add('active');
  
  document.getElementById('btn-auth').innerText = mode === 'signin' ? 'Sign In' : 'Sign Up';
  hideAuthAlert();
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
