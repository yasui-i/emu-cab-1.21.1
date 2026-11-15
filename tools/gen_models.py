"""Regenerate integration after export_edited_cab.py exports the saved blend."""
import runpy
from pathlib import Path
runpy.run_path(str(Path(__file__).with_name("integrate_original_cab.py")), run_name="__main__")
