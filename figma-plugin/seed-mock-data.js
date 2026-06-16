// ==========================================
// Sync Screen — Supabase Seed Mock Script
// Seeds mock session and screens directly to your database
// Run using: node seed-mock-data.js <SUPABASE_URL> <SUPABASE_ANON_KEY>
// ==========================================

const sysArgs = process.argv.slice(2);
const supabaseUrl = sysArgs[0];
const supabaseKey = sysArgs[1];

if (!supabaseUrl || !supabaseKey) {
  console.error('Error: Missing parameters.');
  console.log('Usage: node seed-mock-data.js <SUPABASE_URL> <SUPABASE_ANON_KEY>');
  process.exit(1);
}

// Ensure clean URL format
const cleanUrl = supabaseUrl.replace(/\/$/, "");

async function seedData() {
  console.log('Connecting to Supabase...');
  
  // Sample high-quality mobile screen illustrations from Unsplash
  const mockImages = [
    "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&q=80", // Premium screen abstract
    "https://images.unsplash.com/photo-1634017839464-5c339ebe3cb4?w=400&q=80", // Mobile vibrant mockup background
    "https://images.unsplash.com/photo-1618005198143-e5283b519a7f?w=400&q=80"  // Success abstract
  ];

  const sessionId = "a8b9c1d2-e3f4-5a6b-7c8d-9e0f1a2b3c4d"; // Static test UUID
  
  const headers = {
    "apikey": supabaseKey,
    "Authorization": `Bearer ${supabaseKey}`,
    "Content-Type": "application/json",
    "Prefer": "return=representation"
  };

  try {
    // 1. Insert Mock Session
    console.log('Inserting mock session for user: "revanth"...');
    const sessionPayload = {
      id: sessionId,
      user_id: "revanth",
      flow_name: "Mobile Onboarding Flow",
      device_model: "Pixel 8 Pro",
      screen_width_px: 393,
      screen_height_px: 852,
      status: "complete",
      completed_at: new Date().toISOString()
    };

    const sessionRes = await fetch(`${cleanUrl}/rest/v1/sessions`, {
      method: "POST",
      headers,
      body: JSON.stringify(sessionPayload)
    });

    if (!sessionRes.ok) {
      const errText = await sessionRes.text();
      // If session already exists, we will continue to screens
      if (sessionRes.status === 409) {
         console.log('Session already exists. Seeding screens...');
      } else {
         throw new Error(`Failed to insert session: ${sessionRes.statusText} - ${errText}`);
      }
    } else {
      console.log('Mock session created successfully!');
    }

    // 2. Clear pre-existing test screens to avoid duplication errors on re-run
    console.log('Clearing old test screens...');
    await fetch(`${cleanUrl}/rest/v1/screens?session_id=eq.${sessionId}`, {
      method: "DELETE",
      headers
    });

    // 3. Insert Mock screens in order
    console.log('Inserting 3 mock screens with tap coordinates...');
    const screensPayload = [
      {
        session_id: sessionId,
        sequence_index: 0,
        image_url: mockImages[0],
        tap_x_pct: 0.5234,   // Center tap (e.g. "Get Started" button)
        tap_y_pct: 0.8125
      },
      {
        session_id: sessionId,
        sequence_index: 1,
        image_url: mockImages[1],
        tap_x_pct: 0.1542,   // Left-aligned tap (e.g. Form Input)
        tap_y_pct: 0.4285
      },
      {
        session_id: sessionId,
        sequence_index: 2,
        image_url: mockImages[2],
        tap_x_pct: 0.8250,   // Right-aligned tap (e.g. "Next" button)
        tap_y_pct: 0.0890
      }
    ];

    const screensRes = await fetch(`${cleanUrl}/rest/v1/screens`, {
      method: "POST",
      headers,
      body: JSON.stringify(screensPayload)
    });

    if (!screensRes.ok) {
      const errText = await screensRes.text();
      throw new Error(`Failed to insert screens: ${screensRes.statusText} - ${errText}`);
    }

    console.log('\n==========================================');
    console.log('🎉 MOCK AUDIT FLOW SEEDED SUCCESSFULLY!');
    console.log('User ID: "revanth"');
    console.log('Flow: "Mobile Onboarding Flow"');
    console.log('Screens: 3 loaded with Tap indicators');
    console.log('==========================================');
    console.log('\nInstructions:');
    console.log('1. Load the Figma plugin in Developer mode.');
    console.log('2. In Settings, paste your Supabase URL & Anon Key, and enter "revanth" as User ID.');
    console.log('3. In the Sync tab, click the newly listed session to watch it draw automatically!');

  } catch (err) {
    console.error('\n❌ Seed failed:', err.message);
  }
}

seedData();
