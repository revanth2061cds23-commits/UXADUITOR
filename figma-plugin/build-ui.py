import os

def build():
    src_ui = "src/ui.html"
    supabase_min = "src/supabase.min.js"
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
