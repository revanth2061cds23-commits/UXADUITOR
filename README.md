# UX Audit Tool

An automated interaction capture ecosystem for UX auditors, consisting of an **Android App** and a **Figma Plugin**, supported by a **Supabase** backend.

## Overview

UX Audit Tool simplifies the process of capturing user flows on Android devices and importing them directly into Figma. The tool automatically captures screenshots and touch coordinates as you navigate any application, then syncs the session data so that it can be laid out automatically on the Figma canvas as a structured flow.

## Project Structure

This repository is organized into the following main directories:

- **[android-app/](file:///c:/Users/srite/UXADUITOR/android-app)**: A Flutter + Native Kotlin application that handles session management, background screen projection, and touch coordinate capture using Android's Accessibility Service.
- **[figma-plugin/](file:///c:/Users/srite/UXADUITOR/figma-plugin)**: A TypeScript Figma plugin that pairs with the mobile app using a session code and renders the captured screens as standard Figma frames.
- **[supabase/](file:///c:/Users/srite/UXADUITOR/supabase)**: Database schemas and configuration for Postgres, Storage, and Realtime sync services.

## Running the Figma Plugin on Another Device

To run this plugin manually on another machine:

1. **Copy the Required Files:**  
   Transfer the `manifest.json` file and the `dist` folder from `figma-plugin/` onto the other machine (ensure they remain in the same directory relative to each other).
2. **Open Figma:**  
   Open Figma in your browser or launch the Figma Desktop app.
3. **Import from Manifest:**  
   - Right-click on the canvas, go to **Plugins -> Development -> Import plugin from manifest...**
   - Select the `manifest.json` file you copied.
4. **Run the Plugin:**  
   The plugin **Sync Screen** will now appear under **Plugins -> Development**. Click it to launch!

## License

All rights reserved.
