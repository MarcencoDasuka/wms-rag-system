"""Code-aware chunking for WMS files (Java, SQL, Vue, Properties, YAML, Markdown)."""

import hashlib
import re
from pathlib import Path
from typing import Any, List
from pydantic import BaseModel, Field


class CodeChunk(BaseModel):
    id: str
    file_path: str
    file_name: str
    language: str
    chunk_type: str  # class_summary, method, sql_table, vue_script, vue_template, config, doc
    symbol_name: str
    content: str
    start_line: int
    end_line: int
    metadata: dict[str, Any] = Field(default_factory=dict)


class CodeAwareChunker:
    """Specialized chunker that preserves semantic boundaries for code and configs."""

    def __init__(self, max_chunk_lines: int = 80):
        self.max_chunk_lines = max_chunk_lines

    def chunk_file(self, file_path: Path, rel_path: str) -> List[CodeChunk]:
        """Route file to appropriate language chunker based on extension."""
        try:
            content = file_path.read_text(encoding="utf-8", errors="replace")
        except Exception:
            return []

        suffix = file_path.suffix.lower()
        if suffix == ".java":
            return self._chunk_java(content, rel_path, file_path.name)
        elif suffix == ".sql":
            return self._chunk_sql(content, rel_path, file_path.name)
        elif suffix == ".vue":
            return self._chunk_vue(content, rel_path, file_path.name)
        elif suffix in [".properties", ".yaml", ".yml"]:
            return self._chunk_config(content, rel_path, file_path.name)
        elif suffix == ".md":
            return self._chunk_markdown(content, rel_path, file_path.name)
        else:
            return self._chunk_fallback(content, rel_path, file_path.name)

    def _chunk_java(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        """Extract Java class overview and individual methods."""
        chunks = []
        lines = content.splitlines()
        total_lines = len(lines)

        # Detect package and class name
        package_match = re.search(r"^\s*package\s+([\w\.]+);", content, re.MULTILINE)
        package_name = package_match.group(1) if package_match else ""

        class_match = re.search(
            r"((?:@[\w\(\)\"=,\s\.\*\n]+\s+)*public\s+(?:class|interface|enum|record)\s+(\w+)[^{]*)\{",
            content
        )
        class_name = class_match.group(2) if class_match else Path(file_name).stem
        class_header = class_match.group(1).strip() if class_match else ""

        # Chunk 1: Class Summary / Header
        header_lines = min(40, total_lines)
        summary_text = (
            f"// File: {rel_path}\n"
            f"// Package: {package_name}\n"
            f"// Class: {class_name}\n\n"
            + "\n".join(lines[:header_lines])
        )
        chunk_id = hashlib.md5(f"{rel_path}:class_summary".encode()).hexdigest()
        chunks.append(
            CodeChunk(
                id=chunk_id,
                file_path=rel_path,
                file_name=file_name,
                language="java",
                chunk_type="class_summary",
                symbol_name=class_name,
                content=summary_text,
                start_line=1,
                end_line=header_lines,
                metadata={"package": package_name, "class": class_name, "is_class_header": True}
            )
        )

        # Parse individual methods via regex boundary detection
        method_pattern = re.compile(
            r"((?:@(?:[A-Z]\w*)(?:\([^\)]*\))?\s*)*"
            r"(?:public|protected|private|static|\s)+[\w<>\[\],\s]+\s+(\w+)\s*\([^\)]*\)\s*(?:throws\s+[\w,\s]+)?\s*\{)",
            re.MULTILINE
        )

        for match in method_pattern.finditer(content):
            method_name = match.group(2)
            if method_name in ["if", "for", "while", "switch", "catch"]:
                continue

            start_char = match.start()
            start_line = content[:start_char].count("\n") + 1

            # Find matching closing brace
            brace_count = 1
            idx = match.end()
            method_body_end = len(content)
            while idx < len(content):
                char = content[idx]
                if char == "{":
                    brace_count += 1
                elif char == "}":
                    brace_count -= 1
                    if brace_count == 0:
                        method_body_end = idx + 1
                        break
                idx += 1

            method_text = content[start_char:method_body_end].strip()
            end_line = start_line + method_text.count("\n")

            annotated_content = (
                f"// File: {rel_path} (Lines {start_line}-{end_line})\n"
                f"// Class: {class_name} | Method: {method_name}\n\n"
                f"{method_text}"
            )

            m_chunk_id = hashlib.md5(f"{rel_path}:{method_name}:{start_line}".encode()).hexdigest()
            chunks.append(
                CodeChunk(
                    id=m_chunk_id,
                    file_path=rel_path,
                    file_name=file_name,
                    language="java",
                    chunk_type="method",
                    symbol_name=f"{class_name}.{method_name}",
                    content=annotated_content,
                    start_line=start_line,
                    end_line=end_line,
                    metadata={"package": package_name, "class": class_name, "method": method_name}
                )
            )

        return chunks

    def _chunk_sql(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        """Split SQL migrations by DDL statements (CREATE TABLE, ALTER TABLE, CREATE INDEX)."""
        chunks = []
        statements = [s.strip() for s in content.split(";") if s.strip()]
        current_line = 1

        for i, stmt in enumerate(statements):
            table_match = re.search(r"(?:CREATE\s+TABLE|ALTER\s+TABLE|CREATE\s+INDEX)\s+(?:IF\s+NOT\s+EXISTS\s+)?([^\s\(;]+)", stmt, re.IGNORECASE)
            symbol = table_match.group(1).replace('"', '') if table_match else f"stmt_{i+1}"
            
            stmt_lines = stmt.count("\n") + 1
            end_line = current_line + stmt_lines - 1

            formatted_content = f"-- Migration: {file_name}\n-- Path: {rel_path}\n-- Target: {symbol}\n\n{stmt};"
            chunk_id = hashlib.md5(f"{rel_path}:{symbol}:{current_line}".encode()).hexdigest()
            
            chunks.append(
                CodeChunk(
                    id=chunk_id,
                    file_path=rel_path,
                    file_name=file_name,
                    language="sql",
                    chunk_type="sql_schema",
                    symbol_name=symbol,
                    content=formatted_content,
                    start_line=current_line,
                    end_line=end_line,
                    metadata={"table_or_index": symbol, "migration_file": file_name}
                )
            )
            current_line = end_line + 1

        return chunks

    def _chunk_vue(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        """Split Vue component into template and script setup."""
        chunks = []
        comp_name = Path(file_name).stem

        # Extract <script setup> or <script>
        script_match = re.search(r"(<script[^>]*>.*?</script>)", content, re.DOTALL)
        if script_match:
            script_content = script_match.group(1).strip()
            chunk_id = hashlib.md5(f"{rel_path}:script".encode()).hexdigest()
            chunks.append(
                CodeChunk(
                    id=chunk_id,
                    file_path=rel_path,
                    file_name=file_name,
                    language="vue",
                    chunk_type="vue_script",
                    symbol_name=f"{comp_name} (Script)",
                    content=f"// Vue Component Script: {rel_path}\n\n{script_content}",
                    start_line=1,
                    end_line=script_content.count("\n") + 1,
                    metadata={"component": comp_name, "section": "script"}
                )
            )

        # Extract <template>
        template_match = re.search(r"(<template>.*?</template>)", content, re.DOTALL)
        if template_match:
            tpl_content = template_match.group(1).strip()
            # If template is huge, take first 60 lines
            tpl_lines = tpl_content.splitlines()[:60]
            chunk_id = hashlib.md5(f"{rel_path}:template".encode()).hexdigest()
            chunks.append(
                CodeChunk(
                    id=chunk_id,
                    file_path=rel_path,
                    file_name=file_name,
                    language="vue",
                    chunk_type="vue_template",
                    symbol_name=f"{comp_name} (Template)",
                    content=f"<!-- Vue Component Template: {rel_path} -->\n\n" + "\n".join(tpl_lines),
                    start_line=1,
                    end_line=len(tpl_lines),
                    metadata={"component": comp_name, "section": "template"}
                )
            )

        return chunks

    def _chunk_config(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        """Chunk configuration files by blocks."""
        chunks = []
        lines = content.splitlines()
        chunk_id = hashlib.md5(f"{rel_path}:config".encode()).hexdigest()
        chunks.append(
            CodeChunk(
                id=chunk_id,
                file_path=rel_path,
                file_name=file_name,
                language="config",
                chunk_type="config",
                symbol_name=file_name,
                content=f"# Configuration File: {rel_path}\n\n{content}",
                start_line=1,
                end_line=len(lines),
                metadata={"config_type": Path(file_name).suffix}
            )
        )
        return chunks

    def _chunk_markdown(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        """Chunk Markdown by headings."""
        chunks = []
        sections = re.split(r"\n(?=#{1,3}\s+)", content)
        line_offset = 1

        for i, sec in enumerate(sections):
            if not sec.strip():
                continue
            lines = sec.splitlines()
            title = lines[0].replace("#", "").strip() if lines else f"Section {i+1}"
            chunk_id = hashlib.md5(f"{rel_path}:{i}:{title}".encode()).hexdigest()

            chunks.append(
                CodeChunk(
                    id=chunk_id,
                    file_path=rel_path,
                    file_name=file_name,
                    language="markdown",
                    chunk_type="doc",
                    symbol_name=title,
                    content=f"# Doc: {rel_path}\n\n{sec.strip()}",
                    start_line=line_offset,
                    end_line=line_offset + len(lines),
                    metadata={"section_title": title}
                )
            )
            line_offset += len(lines)

        return chunks

    def _chunk_fallback(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        """Fallback chunker for other source files."""
        lines = content.splitlines()
        if not lines:
            return []
        chunk_id = hashlib.md5(f"{rel_path}:general".encode()).hexdigest()
        return [
            CodeChunk(
                id=chunk_id,
                file_path=rel_path,
                file_name=file_name,
                language="text",
                chunk_type="general",
                symbol_name=file_name,
                content=f"// File: {rel_path}\n\n" + "\n".join(lines[:100]),
                start_line=1,
                end_line=min(100, len(lines)),
                metadata={}
            )
        ]
