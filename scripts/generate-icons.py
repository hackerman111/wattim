import os
from PIL import Image

def generate_icons():
    src_path = 'wattim.png'
    dest_dir = 'extension/icons'
    os.makedirs(dest_dir, exist_ok=True)

    if not os.path.exists(src_path):
        print(f"Error: {src_path} not found")
        return

    img = Image.open(src_path)
    # Ensure RGBA
    img = img.convert('RGBA')

    sizes = [16, 32, 48, 128]
    for size in sizes:
        resized = img.resize((size, size), Image.Resampling.LANCZOS)
        out_path = os.path.join(dest_dir, f'icon-{size}.png')
        resized.save(out_path, 'PNG', optimize=True)
        print(f"Generated: {out_path} ({size}x{size})")

if __name__ == '__main__':
    generate_icons()
