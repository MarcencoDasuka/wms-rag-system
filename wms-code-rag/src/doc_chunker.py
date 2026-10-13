"""Heading-aware Markdown chunker for specification / ADR documents (e.g. Ospysin docs).

Design contract:
- Chunk boundaries follow Markdown headings; ``#`` inside fenced code blocks is NOT a heading.
- Every chunk is prefixed with a breadcrumb line ``[rel_path | H1 > H2 > H3]`` so that
  context-free sections such as "Parameters" remain attributable to their parent tool/endpoint.
- Tables and fenced code blocks are atomic: they are never split mid-structure unless a single
  one alone exceeds the budget, in which case the split is re-wrapped (table header repeated,
  fence re-opened/closed).
- Chunk IDs depend on (file, heading path, occurrence, part) and NOT on line numbers, so edits that
  only shift lines do not create ghost duplicates.
- Category is derived deterministically from the file path, never from content.
"""

import hashlib
import re
from pathlib import Path
from typing import List, Optional, Tuple

from src.chunker import PRIVATE_KEY_BLOCK_PATTERN, CodeChunk

HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*#*\s*$")
FENCE_RE = re.compile(r"^\s{0,3}(`{3,}|~{3,})")
NUMBERED_DOC_RE = re.compile(r"^(\d{2})-")
ADR_RE = re.compile(r"^(\d{4})-")

# Minimal body size below which a section is merged into the following one.
MIN_SECTION_CHARS = 60

_CATEGORY_BY_NUMBER = {
    "00": "architecture",
    "01": "architecture",
    "02": "architecture",
    "03": "data",
    "04": "api",
    "05": "tools",
    "06": "llm",
    "07": "security",
    "08": "security",
    "09": "security",
    "10": "ui",
    "11": "data",
    "12": "ops",
    "13": "ops",
    "14": "ops",
    "15": "glossary",
}


def classify_doc(rel_path: str) -> Tuple[str, str]:
    """Return ``(category, doc_id)`` derived purely from the relative file path."""
    parts = rel_path.replace("\\", "/").split("/")
    name = parts[-1]
    if len(parts) > 1 and parts[-2].lower() == "adr":
        m = ADR_RE.match(name)
        return "adr", f"adr-{m.group(1)}" if m else f"adr-{Path(name).stem}"
    m = NUMBERED_DOC_RE.match(name)
    if m:
        return _CATEGORY_BY_NUMBER.get(m.group(1), "other"), m.group(1)
    return "other", Path(name).stem.lower()


def _clean_heading(text: str) -> str:
    """Strip inline-code backticks and bold/italic asterisks; underscores are kept (identifiers)."""
    return re.sub(r"[`*]{1,3}", "", text).strip()


class _Section:
    __slots__ = ("path", "title", "body", "start_line", "end_line", "body_line")

    def __init__(self, path: List[str], title: str, start_line: int):
        self.path = path
        self.title = title
        self.body: List[str] = []
        self.start_line = start_line
        self.end_line = start_line
        # Absolute line of the first body line (heading line + 1 for headed sections).
        self.body_line = start_line


class MarkdownSpecChunker:
    """Splits Markdown specifications into breadcrumb-prefixed, budget-bounded chunks."""

    def __init__(self, max_chars: int = 1200, overlap_chars: int = 150):
        if max_chars < 200:
            raise ValueError("max_chars must be >= 200")
        if overlap_chars < 0 or overlap_chars >= max_chars // 2:
            raise ValueError("overlap_chars must be >= 0 and < max_chars / 2")
        self.max_chars = max_chars
        self.overlap_chars = overlap_chars

    # ------------------------------------------------------------------ public API

    def chunk_file(self, file_path: Path, rel_path: str) -> List[CodeChunk]:
        content = file_path.read_text(encoding="utf-8", errors="replace")
        return self.chunk_text(content, rel_path, file_path.name)

    def chunk_text(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        content = PRIVATE_KEY_BLOCK_PATTERN.sub("[REDACTED_PRIVATE_KEY]", content or "")
        rel_path = rel_path.replace("\\", "/")
        sections = self._merge_tiny(self._parse_sections(content, file_name))
        category, doc_id = classify_doc(rel_path)

        chunks: List[CodeChunk] = []
        occurrences: dict[str, int] = {}
        for sec in sections:
            body_text = "\n".join(sec.body).strip()
            if not body_text:
                continue
            heading_path = " > ".join(sec.path)
            occ = occurrences.get(heading_path, 0)
            occurrences[heading_path] = occ + 1

            parts = self._split_body(sec.body, sec.body_line)
            total = len(parts)
            for idx, (part_text, p_start, p_end) in enumerate(parts, 1):
                label = heading_path + (f" (part {idx}/{total})" if total > 1 else "")
                text = f"[{rel_path} | {label}]\n{part_text}"
                chunk_id = hashlib.sha256(
                    f"{rel_path}:spec:{heading_path}:{occ}:{idx}".encode("utf-8")
                ).hexdigest()[:32]
                chunks.append(
                    CodeChunk(
                        id=chunk_id,
                        file_path=rel_path,
                        file_name=file_name,
                        language="markdown",
                        chunk_type="spec_doc",
                        symbol_name=sec.title,
                        content=text,
                        start_line=p_start,
                        end_line=p_end,
                        metadata={
                            "category": category,
                            "doc_id": doc_id,
                            "heading_path": heading_path,
                            "section_title": sec.title,
                            "part": idx,
                            "parts": total,
                            "content_hash": hashlib.sha256(text.encode("utf-8")).hexdigest(),
                        },
                    )
                )
        return chunks

    # ------------------------------------------------------------------ parsing

    def _parse_sections(self, content: str, file_name: str) -> List[_Section]:
        lines = content.splitlines()
        stack: List[Tuple[int, str]] = []  # (level, cleaned title)
        sections: List[_Section] = []
        default_title = Path(file_name).stem
        current = _Section([default_title], default_title, 1)
        sections.append(current)

        fence_marker: Optional[str] = None
        for lineno, line in enumerate(lines, 1):
            fm = FENCE_RE.match(line)
            if fm:
                marker = fm.group(1)
                if fence_marker is None:
                    fence_marker = marker
                elif marker[0] == fence_marker[0] and len(marker) >= len(fence_marker):
                    fence_marker = None
                current.body.append(line)
                current.end_line = lineno
                continue

            hm = HEADING_RE.match(line) if fence_marker is None else None
            if hm:
                level = len(hm.group(1))
                title = _clean_heading(hm.group(2))
                while stack and stack[-1][0] >= level:
                    stack.pop()
                stack.append((level, title))
                current = _Section([t for _, t in stack], title, lineno)
                current.body_line = lineno + 1
                sections.append(current)
                continue

            current.body.append(line)
            current.end_line = lineno
        return sections

    def _merge_tiny(self, sections: List[_Section]) -> List[_Section]:
        """Fold a near-empty section into the next one ONLY if the next section is its descendant.

        Folding into a sibling/unrelated section would place text under the wrong breadcrumb, so
        in that case the tiny section is emitted on its own.
        """
        merged: List[_Section] = []
        carry: Optional[_Section] = None
        for sec in sections:
            if carry is not None:
                is_descendant = len(sec.path) > len(carry.path) and sec.path[: len(carry.path)] == carry.path
                if is_descendant:
                    carry_text = "\n".join(carry.body).strip()
                    prefix = [carry.title, carry_text] if carry_text else [carry.title]
                    sec.body = prefix + [""] + sec.body
                    # Line numbers of merged sections are approximate (prefix lines are synthetic).
                    sec.start_line = min(sec.start_line, carry.start_line)
                    sec.body_line = sec.start_line
                else:
                    merged.append(carry)
                carry = None
            body_len = len("\n".join(sec.body).strip())
            if 0 < body_len < MIN_SECTION_CHARS and sec is not sections[-1]:
                carry = sec
                continue
            merged.append(sec)
        if carry is not None:
            merged.append(carry)
        return merged

    # ------------------------------------------------------------------ splitting

    def _blocks(self, body: List[str], first_line: int) -> List[Tuple[str, str, int, int]]:
        """Group body lines into atomic blocks: ``(kind, text, start_line, end_line)``."""
        blocks: List[Tuple[str, str, int, int]] = []
        i, n = 0, len(body)
        while i < n:
            line = body[i]
            if not line.strip():
                i += 1
                continue
            start = i
            fm = FENCE_RE.match(line)
            if fm:
                marker = fm.group(1)
                i += 1
                while i < n:
                    cm = FENCE_RE.match(body[i])
                    i += 1
                    if cm and cm.group(1)[0] == marker[0] and len(cm.group(1)) >= len(marker):
                        break
                kind = "fence"
            elif line.lstrip().startswith("|"):
                while i < n and body[i].lstrip().startswith("|"):
                    i += 1
                kind = "table"
            else:
                while i < n and body[i].strip() and not FENCE_RE.match(body[i]):
                    if body[i].lstrip().startswith("|"):
                        break
                    i += 1
                kind = "text"
            blocks.append((kind, "\n".join(body[start:i]), first_line + start, first_line + i - 1))
        return blocks

    def _split_oversized(self, kind: str, text: str, start: int) -> List[Tuple[str, str, int, int]]:
        """Break one block larger than the budget into budget-sized, structurally valid pieces.

        Tables repeat their header rows in every piece; fences are closed and re-opened so every
        piece is a well-formed block. A single line longer than the budget is hard-wrapped.
        """
        lines = text.split("\n")
        budget = self.max_chars
        header: List[str] = []
        footer: List[str] = []
        body_start = 0
        body = lines
        if kind == "table" and len(lines) > 2 and re.match(r"^\s*\|[\s:|-]+\|\s*$", lines[1]):
            header, body, body_start = lines[:2], lines[2:], 2
        elif kind == "fence":
            header = [lines[0]]
            has_close = len(lines) > 1 and FENCE_RE.match(lines[-1]) is not None
            body = lines[1:-1] if has_close else lines[1:]
            footer = [lines[-1]] if has_close else [FENCE_RE.match(lines[0]).group(1)]
            body_start = 1

        # (text, absolute line number); over-long lines become several units on the same line.
        units: List[Tuple[str, int]] = []
        for idx, ln in enumerate(body):
            segments = [ln[j:j + budget] for j in range(0, len(ln), budget)] or [ln]
            units.extend((seg, start + body_start + idx) for seg in segments)

        overhead = sum(len(x) + 1 for x in header + footer)
        pieces: List[Tuple[str, str, int, int]] = []
        cur: List[Tuple[str, int]] = []
        size = overhead

        def flush() -> None:
            if cur:
                piece = "\n".join(header + [u[0] for u in cur] + footer)
                pieces.append((kind, piece, cur[0][1], cur[-1][1]))

        for unit in units:
            if cur and size + len(unit[0]) + 1 > budget:
                flush()
                cur = []
                size = overhead
            cur.append(unit)
            size += len(unit[0]) + 1
        flush()
        return pieces

    def _split_body(self, body: List[str], first_line: int) -> List[Tuple[str, int, int]]:
        blocks: List[Tuple[str, str, int, int]] = []
        for kind, text, s, e in self._blocks(body, first_line):
            if len(text) > self.max_chars:
                blocks.extend(self._split_oversized(kind, text, s))
            else:
                blocks.append((kind, text, s, e))

        parts: List[Tuple[str, int, int]] = []
        cur: List[Tuple[str, str, int, int]] = []
        size = 0
        has_fresh = False  # cur holds at least one block not already emitted as overlap
        for blk in blocks:
            blk_len = len(blk[1]) + 2
            if cur and size + blk_len > self.max_chars:
                parts.append(self._join(cur))
                cur = self._overlap_tail(cur)
                size = sum(len(b[1]) + 2 for b in cur)
                has_fresh = False
            cur.append(blk)
            size += blk_len
            has_fresh = True
        if cur and has_fresh:
            parts.append(self._join(cur))
        return parts

    def _overlap_tail(self, blocks: List[Tuple[str, str, int, int]]) -> List[Tuple[str, str, int, int]]:
        """Trailing prose block(s) carried into the next part. Tables and fences are never duplicated."""
        if not self.overlap_chars:
            return []
        last = blocks[-1]
        if last[0] == "text" and len(last[1]) <= self.overlap_chars:
            return [last]
        return []

    @staticmethod
    def _join(blocks: List[Tuple[str, str, int, int]]) -> Tuple[str, int, int]:
        text = "\n\n".join(b[1] for b in blocks)
        return text, blocks[0][2], blocks[-1][3]
