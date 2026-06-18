// ==========================================
// Sync Screen — Figma Plugin Sandbox Code
// Coordinates canvas drawing on the main thread
// ==========================================

// Show the plugin's HTML UI
figma.showUI(__html__, { width: 340, height: 500, themeColors: true });

// Listen for messages from the iframe UI
figma.ui.onmessage = async (msg) => {

  if (msg.type === 'save-session') {
    await figma.clientStorage.setAsync('supabase_session', msg.session);
    return;
  }

  if (msg.type === 'clear-session') {
    await figma.clientStorage.deleteAsync('supabase_session');
    return;
  }

  if (msg.type === 'get-session') {
    const session = await figma.clientStorage.getAsync('supabase_session');
    figma.ui.postMessage({ type: 'restore-session', session });
    return;
  }

  if (msg.type === 'import-flow') {
    const { flowName, screens } = msg;

    if (!screens || screens.length === 0) {
      figma.ui.postMessage({ type: 'error', message: 'No screens found in this session.' });
      return;
    }

    try {
      // 1. Determine viewport center to place screens exactly where the user is looking
      const viewportCenter = figma.viewport.center;
      const createdFrames: FrameNode[] = [];
      const gap = 80; // Standard layout horizontal gap
      
      // Calculate total flow width to center the entire imported flow horizontally
      let totalFlowWidth = 0;
      for (const s of screens) {
        totalFlowWidth += s.screen_width_px || 360;
      }
      totalFlowWidth += gap * (screens.length - 1);
      
      // Start placing frames so the whole flow is horizontally centered at the viewport center
      let currentX = viewportCenter.x - (totalFlowWidth / 2);

      // Ensure standard font is loaded for text layers
      await figma.loadFontAsync({ family: "Inter", style: "Medium" });
      await figma.loadFontAsync({ family: "Inter", style: "Regular" });

      // 2. Build each screen frame sequentially
      for (let i = 0; i < screens.length; i++) {
        const s = screens[i];
        
        // Use provided dimensions or fallback to standard phone screen
        const width = s.screen_width_px || 360;
        const height = s.screen_height_px || 800;

        // Create the core Frame
        const frame = figma.createFrame();
        frame.resize(width, height);
        frame.name = `${flowName} - Screen ${s.sequence_index + 1}`;

        // Position frame horizontally and center it vertically around the viewport center
        frame.x = currentX;
        frame.y = viewportCenter.y - (height / 2);

        // Update coordinate cursor for the next frame
        currentX += width + gap;

        // Apply clean image fill from bytes
        const image = figma.createImage(s.imageBytes);
        frame.fills = [
          {
            type: 'IMAGE',
            imageHash: image.hash,
            scaleMode: 'FILL'
          }
        ];

        // 3. Render the Tap Indicator layer if coordinates exist and are valid (non-negative and non-NaN)
        if (s.tap_x_pct !== undefined && s.tap_y_pct !== undefined && !isNaN(s.tap_x_pct) && !isNaN(s.tap_y_pct) && s.tap_x_pct >= 0 && s.tap_y_pct >= 0) {
          const absoluteX = s.tap_x_pct * width;
          const absoluteY = s.tap_y_pct * height;

          // Modern Rounded Blue Highlight Rectangle (matching the uploaded style, scaled by user setting)
          const highlightWidth = msg.tapSize || 48;
          // Maintain the exact 6:5 aspect ratio from the reference image
          const highlightHeight = Math.round(highlightWidth * (40 / 48));
          
          const highlight = figma.createRectangle();
          highlight.resize(highlightWidth, highlightHeight);
          highlight.x = absoluteX - (highlightWidth / 2); // Center horizontally on tap
          highlight.y = absoluteY - (highlightHeight / 2); // Center vertically on tap
          
          // Smoothly scale the corner radius based on size (min 4, max 16)
          highlight.cornerRadius = Math.max(4, Math.min(16, Math.round(highlightWidth * (8 / 48))));
          
          // Sleek vibrant blue border/stroke (#0084FF)
          highlight.strokes = [{ type: 'SOLID', color: { r: 0.0, g: 0.52, b: 1.0 } }];
          highlight.strokeWeight = 2;

          // Soft transparent light blue fill (#CBE5FC)
          highlight.fills = [{ type: 'SOLID', color: { r: 0.80, g: 0.90, b: 0.99 } }];
          highlight.opacity = 0.65;
          highlight.name = "Tap Spot";

          // Indicator sequence label text centered directly inside the highlight box
          const label = figma.createText();
          label.fontName = { family: "Inter", style: "Medium" };
          label.characters = `${s.sequence_index + 1}`;
          
          // Scale font size proportionally to keep it perfectly readable (min 8, max 24)
          const calculatedFontSize = Math.max(8, Math.min(24, Math.round(highlightWidth * (12 / 48))));
          label.fontSize = calculatedFontSize;
          
          label.fills = [{ type: 'SOLID', color: { r: 0.0, g: 0.52, b: 1.0 } }]; // Matching Royal Blue text
          label.textAlignHorizontal = "CENTER";
          label.textAlignVertical = "CENTER";
          label.resize(highlightWidth, highlightHeight);
          label.x = highlight.x;
          label.y = highlight.y;
          label.name = "Tap Label";

          // Append to frame before grouping
          frame.appendChild(highlight);
          frame.appendChild(label);

          // Group both inside the frame and name it 'Tap Indicator'
          const tapGroup = figma.group([highlight, label], frame);
          tapGroup.name = "Tap Indicator";
        }

        createdFrames.push(frame);
      }

      // 4. Wrap all frames in a clean group container named after the flow
      if (createdFrames.length > 0) {
        const flowGroup = figma.group(createdFrames, figma.currentPage);
        flowGroup.name = `Flow: ${flowName}`;
        
        // Focus viewport on the newly imported flow
        figma.viewport.scrollAndZoomIntoView([flowGroup]);
        
        figma.ui.postMessage({ type: 'success', message: `Successfully imported ${screens.length} screens!` });
      }

    } catch (err: any) {
      console.error(err);
      figma.ui.postMessage({ type: 'error', message: `Import failed: ${err.message || err}` });
    }
  }

  if (msg.type === 'toggle-indicators') {
    const { visible } = msg;
    
    // Find all 'Tap Indicator' group layers in the active page and toggle visibility
    const indicatorGroups = figma.currentPage.findAll(node => node.name === "Tap Indicator");
    
    for (const group of indicatorGroups) {
      group.visible = visible;
    }
    
    figma.ui.postMessage({ 
      type: 'status', 
      message: `Tap indicators ${visible ? 'shown' : 'hidden'} (${indicatorGroups.length} nodes updated).` 
    });
  }
};
