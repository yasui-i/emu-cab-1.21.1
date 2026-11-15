"""Render an original/reduced comparison using Blender in the background."""
import subprocess
from pathlib import Path
subprocess.run([r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe", "--background", "--python", str(Path(__file__).with_name("preview_original_cab.py"))], check=True)
