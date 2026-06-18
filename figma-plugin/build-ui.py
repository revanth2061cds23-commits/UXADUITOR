import os
import base64

def build():
    src_ui = "src/ui.html"
    supabase_min = "src/supabase.min.js"
    intro_img = "src/intro-mockup.png"
    dist_dir = "dist"
    dist_ui = "dist/ui.html"

    if not os.path.exists(dist_dir):
        os.makedirs(dist_dir)

    if not os.path.exists(src_ui):
        print(f"Error: {src_ui} does not exist!")
        return

    if not os.path.exists(supabase_min):
        print(f"Error: {supabase_min} does not exist!")
        return

    with open(src_ui, "r", encoding="utf-8") as f:
        html = f.read()

    # 1. Base64 encode the intro image mockup
    if os.path.exists(intro_img):
        with open(intro_img, "rb") as f:
            img_data = f.read()
            base64_str = base64.b64encode(img_data).decode("utf-8")
            data_url = f"data:image/png;base64,{base64_str}"
            if "INTRO_MOCKUP_BASE64_PLACEHOLDER" in html:
                html = html.replace("INTRO_MOCKUP_BASE64_PLACEHOLDER", data_url)
                print("Inlined intro mockup image in base64 format.")
            else:
                print("Warning: INTRO_MOCKUP_BASE64_PLACEHOLDER not found in ui.html.")
    else:
        print(f"Warning: {intro_img} does not exist. Skipping image inlining.")

    # 2. Inline Supabase script
    with open(supabase_min, "r", encoding="utf-8") as f:
        supabase_code = f.read()

    target_tag = '<script src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2"></script>'
    if target_tag in html:
        html = html.replace(target_tag, f"<script>{supabase_code}</script>")
        print("Bundled Supabase library inline.")
    else:
        print("Warning: Could not find external Supabase script tag to inline in ui.html.")

    with open(dist_ui, "w", encoding="utf-8") as f:
        f.write(html)
    print("Successfully compiled ui.html to dist/ui.html")

if __name__ == "__main__":
    build()
