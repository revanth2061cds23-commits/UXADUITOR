-- ==========================================
-- UX Audit Tool — Supabase Database Schema
-- Platform: Android Companion App + Figma Plugin
-- Version: 1.0 (Authenticated Sync via auth.users)
-- ==========================================

-- 1. Enable UUID Extension if not enabled
create extension if not exists "uuid-ossp";

-- 2. Create SESSIONS Table
create table if not exists public.sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references auth.users(id) on delete cascade not null, -- Links App and Figma automatically
    flow_name varchar(255) not null,                   -- Flow name entered by the auditor at start
    device_model varchar(100),                         -- e.g., "Pixel 7 Pro", "Samsung S23"
    screen_width_px integer,                           -- Screen dimensions for layout scaling
    screen_height_px integer,
    status varchar(50) default 'active' 
        check (status in ('active', 'uploading', 'complete', 'failed')),
    created_at timestamptz default current_timestamp,
    completed_at timestamptz
);

-- Add database index for fast pairing/lookup on User ID
create index if not exists idx_sessions_user_id on public.sessions(user_id);
create index if not exists idx_sessions_created_at on public.sessions(created_at desc);

-- 3. Create SCREENS Table (Flow screenshots and tap events)
create table if not exists public.screens (
    id uuid primary key default gen_random_uuid(),
    session_id uuid references public.sessions(id) on delete cascade not null,
    sequence_index integer not null,                   -- Order of navigation (0, 1, 2...)
    image_url text not null,                           -- Supabase Storage public image link (PNG)
    tap_x_pct numeric(5, 4) not null,                  -- Normalized touch X (0.0000 to 1.0000)
    tap_y_pct numeric(5, 4) not null,                  -- Normalized touch Y (0.0000 to 1.0000)
    captured_at timestamptz default current_timestamp
);

-- Optimize sequence retrieval for plugin rendering
create index if not exists idx_screens_session_sequence on public.screens(session_id, sequence_index asc);

-- 4. Create RECORDINGS Table (Optional think-aloud commentaries)
create table if not exists public.recordings (
    id uuid primary key default gen_random_uuid(),
    session_id uuid references public.sessions(id) on delete cascade not null,
    video_url text not null,                           -- Supabase Storage video link (.mp4)
    duration_seconds integer not null,
    mic_audio boolean default false,                   -- True if microphone was enabled
    created_at timestamptz default current_timestamp
);

-- Index for checking session recordings
create index if not exists idx_recordings_session_id on public.recordings(session_id);

-- 5. Row-Level Security (RLS) Configuration (Authenticated User Rules)
alter table public.sessions enable row level security;
alter table public.screens enable row level security;
alter table public.recordings enable row level security;

-- Strict user-level authenticated RLS policies
create policy "Allow users to read their own sessions" on public.sessions
    for select to authenticated using ( (select auth.uid()) = user_id );

create policy "Allow users to insert their own sessions" on public.sessions
    for insert to authenticated with check ( (select auth.uid()) = user_id );

create policy "Allow users to update their own sessions" on public.sessions
    for update to authenticated using ( (select auth.uid()) = user_id );

create policy "Allow users to read their own screens" on public.screens
    for select to authenticated using (
        exists (
            select 1 from public.sessions 
            where public.sessions.id = session_id 
            and public.sessions.user_id = (select auth.uid())
        )
    );

create policy "Allow users to insert their own screens" on public.screens
    for insert to authenticated with check (
        exists (
            select 1 from public.sessions 
            where public.sessions.id = session_id 
            and public.sessions.user_id = (select auth.uid())
        )
    );

create policy "Allow users to read their own recordings" on public.recordings
    for select to authenticated using (
        exists (
            select 1 from public.sessions 
            where public.sessions.id = session_id 
            and public.sessions.user_id = (select auth.uid())
        )
    );

create policy "Allow users to insert their own recordings" on public.recordings
    for insert to authenticated with check (
        exists (
            select 1 from public.sessions 
            where public.sessions.id = session_id 
            and public.sessions.user_id = (select auth.uid())
        )
    );

-- 6. Enable Realtime Replication for Instant Sync
-- Note: Requires checking replication settings in the Supabase Dashboard
begin;
  -- Add sessions to publication to broadcast status updates ('active' -> 'uploading' -> 'complete')
  alter publication supabase_realtime add table public.sessions;
commit;
