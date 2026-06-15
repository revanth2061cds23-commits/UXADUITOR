# UX Audit Tool

An automated interaction capture ecosystem for UX auditors, consisting of an **Android App** and a **Figma Plugin**, supported by a **Supabase** backend.

## Overview

UX Audit Tool simplifies the process of capturing user flows on Android devices and importing them directly into Figma. The tool automatically captures screenshots and touch coordinates as you navigate any application, then syncs the session data so that it can be laid out automatically on the Figma canvas as a structured flow.

## Project Structure

This repository is organized into the following main directories:

- **[android-app/](file:///c:/Users/srite/UXADUITOR/android-app)**: A Flutter + Native Kotlin application that handles session management, background screen projection, and touch coordinate capture using Android's Accessibility Service.
- **[figma-plugin/](file:///c:/Users/srite/UXADUITOR/figma-plugin)**: A TypeScript Figma plugin that pairs with the mobile app using a session code and renders the captured screens as standard Figma frames.
- **[supabase/](file:///c:/Users/srite/UXADUITOR/supabase)**: Database schemas and configuration for Postgres, Storage, and Realtime sync services.

## Getting Started

Refer to the product requirements and design journey documents for additional context:
- [UX Audit Tool PRD (Markdown)](file:///c:/Users/srite/UXADUITOR/UX_Audit_Tool_PRD_v1.md)
- [Product Journey and Iterations](file:///c:/Users/srite/UXADUITOR/product_journey_and_iterations.md)

## License

All rights reserved.
