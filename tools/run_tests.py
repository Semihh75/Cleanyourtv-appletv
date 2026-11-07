#!/usr/bin/env python3
"""Run tests/ as CI does, minus the checks of the original project's long README.

The Apple TV edition has a short README (README.md) instead of upstream's long front page: no Tools table,
no press card and counts, no translations block, no FAQ. The tests below read exactly those parts of
README.md (or the translations that mirror it), so here they are skipped with that reason instead of
failing. Everything else in tests/ runs unchanged. CI runs this file; `python -m unittest discover -s tests`
still runs the full upstream suite.
"""
import sys
import unittest
from pathlib import Path

REASON = "Apple TV edition: checks upstream's long README, which this edition replaced with a short one"

# Whole modules that only check the long README, its translations or the old settings screen's docs.
MODULES = {
    "test_press_counts", "test_readme_quick_start_clone", "test_readme_theme_count",
    "test_readme_translations", "test_readme_wireless_debugging", "test_tools_table",
    "test_stock_launcher_docs", "test_launcher_settings_rows",
}
# Single tests (module.method) that read a README section this edition dropped.
TESTS = {
    "test_adb_closed_only_a_refusal.test_the_readme_row_gives_exit_0_for_a_refusal_only",
    "test_card_scripts.test_the_card_script_writes_the_picture_the_front_page_shows",
    "test_check_exit_codes.test_the_readme_names_every_code_the_checker_can_return",
    "test_gen_tables.test_the_brand_grid_counts_every_folder_once",
    "test_haier_h50h5f_loop_note.test_readme_points_to_the_factory_reset_and_the_bullet",
    "test_issue577_report.test_readme_no_longer_promises_a_fire_tv_debloat",
    "test_kirkwood_setupwraith_note.test_the_readme_clause_covers_both_boxes",
    "test_launcher_min_sdk.test_readme_states_the_launcher_requirement_with_a_pointer",
    "test_match.test_the_readme_lists_the_tool",
    "test_markdown_links.test_every_anchor_names_a_section_of_its_page",
    "test_printed_commands_run_as_typed.test_the_write_step_is_the_readme_form",
    "test_privacy_audit.test_readme_lists_the_tool_and_what_it_cannot_say",
    "test_reboot_docs.test_the_readme_tool_row_names_reboot",
    "test_samsung_tizen_note.test_readme_sends_tizen_readers_to_the_page",
    "test_showcase_project_count.test_readme_spells_the_same_number",
}


def skipped(test_id):
    parts = test_id.split(".")
    module, method = parts[0], parts[-1]
    return module in MODULES or f"{module}.{method}" in TESTS


def mark(suite):
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            mark(item)
        elif skipped(item.id()):
            setattr(item, item._testMethodName, unittest.skip(REASON)(getattr(item, item._testMethodName)))


def main():
    root = Path(__file__).resolve().parent.parent
    suite = unittest.defaultTestLoader.discover(str(root / "tests"), top_level_dir=str(root / "tests"))
    mark(suite)
    verbosity = 2 if "-v" in sys.argv else 1
    result = unittest.TextTestRunner(verbosity=verbosity).run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)


if __name__ == "__main__":
    main()
