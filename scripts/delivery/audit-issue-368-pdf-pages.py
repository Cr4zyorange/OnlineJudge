#!/usr/bin/env python3
"""Audit every rasterized Issue #368 PDF page and build visual contact sheets."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pages", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--contacts", type=Path, required=True)
    parser.add_argument("--expected", type=int, default=545)
    return parser.parse_args()


def audit_page(path: Path) -> dict[str, object]:
    with Image.open(path) as opened:
        image = opened.convert("RGB")
    white = Image.new("RGB", image.size, "white")
    difference = ImageChops.difference(image, white)
    bbox = difference.getbbox()
    content_pixels = 0 if bbox is None else sum(1 for pixel in difference.convert("L").getdata() if pixel > 12)
    ratio = content_pixels / (image.width * image.height)
    edge = Image.new("L", image.size, 0)
    edge_draw = ImageDraw.Draw(edge)
    edge_draw.rectangle((0, 0, image.width - 1, image.height - 1), outline=255, width=3)
    edge_content = ImageChops.multiply(difference.convert("L"), edge).getbbox() is not None
    return {
        "path": path.as_posix(),
        "width": image.width,
        "height": image.height,
        "contentRatio": round(ratio, 6),
        "nearBlank": ratio < 0.001,
        "edgeContent": edge_content,
    }


def build_contacts(page_root: Path, contact_root: Path) -> list[str]:
    contact_root.mkdir(parents=True, exist_ok=True)
    outputs: list[str] = []
    for document in sorted(path for path in page_root.iterdir() if path.is_dir()):
        pages = sorted(document.glob("page-*.png"))
        for start in range(0, len(pages), 30):
            batch = pages[start : start + 30]
            sheet = Image.new("RGB", (1200, 1800), "#dbe4ec")
            draw = ImageDraw.Draw(sheet)
            for index, page in enumerate(batch):
                with Image.open(page) as opened:
                    thumb = opened.convert("RGB")
                thumb.thumbnail((220, 270))
                column = index % 5
                row = index // 5
                x = 12 + column * 238
                y = 24 + row * 294
                sheet.paste(thumb, (x, y + 18))
                draw.text((x, y), page.stem, fill="#172033")
            output = contact_root / f"{document.name}-{start + 1:04d}-{start + len(batch):04d}.jpg"
            sheet.save(output, "JPEG", quality=88, optimize=True)
            outputs.append(output.as_posix())
    return outputs


def main() -> int:
    args = parse_args()
    pages = sorted(args.pages.glob("*/page-*.png"))
    audits = [audit_page(path) for path in pages]
    dimensions = sorted({(item["width"], item["height"]) for item in audits})
    near_blank = [item["path"] for item in audits if item["nearBlank"]]
    edge_content = [item["path"] for item in audits if item["edgeContent"]]
    contacts = build_contacts(args.pages, args.contacts)
    report = {
        "expectedPages": args.expected,
        "actualPages": len(audits),
        "dimensions": [{"width": width, "height": height} for width, height in dimensions],
        "nearBlankPages": near_blank,
        "edgeContentPages": edge_content,
        "contactSheets": contacts,
        "status": "PASS" if len(audits) == args.expected and not near_blank and not edge_content else "FAIL",
        "pages": audits,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"PDF_PAGE_AUDIT status={report['status']} pages={len(audits)}/{args.expected} "
        f"dimensions={len(dimensions)} near_blank={len(near_blank)} edge_content={len(edge_content)} "
        f"contact_sheets={len(contacts)}"
    )
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
