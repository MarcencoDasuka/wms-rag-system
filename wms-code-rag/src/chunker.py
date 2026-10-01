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


SENSITIVE_KEY_PATTERN = re.compile(
    r"(?i)(password|secret|jwt|token|credential|api[_-]?key|private[_-]?key|"
    r"access[_-]?key|auth[_-]?token|bearer[_-]?token|datasource\.password)"
)

PRIVATE_KEY_BLOCK_PATTERN = re.compile(
    r"-----BEGIN [A-Z0-9_-]+ PRIVATE KEY-----[\s\S]*?-----END [A-Z0-9_-]+ PRIVATE KEY-----",
    re.MULTILINE
)


def sanitize_secrets(content: str) -> str:
    """Sanitizes secret-bearing configurations and keys before chunking.
    
    Prevents passwords, JWT secrets, database credentials, and private keys
    from entering the vector embeddings, store, and retrieval context.
    """
    if not content:
        return ""

    # 1. Scrub private key PEM blocks
    sanitized = PRIVATE_KEY_BLOCK_PATTERN.sub("[REDACTED_PRIVATE_KEY]", content)

    # 2. Scrub key-value configuration lines (properties, yaml, env)
    lines = sanitized.splitlines()
    redacted_lines = []
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or stripped.startswith("//") or stripped.startswith("/*"):
            redacted_lines.append(line)
            continue

        # Check properties / yaml key: value or key=value
        kv_match = re.match(r"^(\s*[\w\.\-\[\]]+\s*[:=]\s*)(.*)$", line)
        if kv_match:
            prefix, val = kv_match.group(1), kv_match.group(2)
            key_part = prefix.split(":")[0].split("=")[0]
            if SENSITIVE_KEY_PATTERN.search(key_part):
                # Redact value, preserving any trailing comments
                comment_match = re.search(r"(\s+#.*|\s+//.*)$", val)
                comment = comment_match.group(1) if comment_match else ""
                redacted_lines.append(f"{prefix}[REDACTED]{comment}")
                continue

        # Scrub Spring property placeholders with default secrets: ${VAR:secret_fallback}
        def redact_placeholder(m):
            var_name = m.group(1)
            if SENSITIVE_KEY_PATTERN.search(var_name):
                return f"${{{var_name}:[REDACTED]}}"
            return m.group(0)

        line = re.sub(r"\$\{([A-Za-z0-9_\.\-]+):([^}]+)\}", redact_placeholder, line)
        redacted_lines.append(line)

    return "\n".join(redacted_lines)


def _mask_java_syntax(code: str) -> str:
    """Masks Java comments and string/char literals with spaces to enable reliable syntax parsing."""
    res = list(code)
    i = 0
    n = len(code)
    while i < n:
        if i + 1 < n and code[i:i+2] == '//':
            res[i] = ' '; res[i+1] = ' '
            i += 2
            while i < n and code[i] != '\n':
                res[i] = ' '
                i += 1
        elif i + 1 < n and code[i:i+2] == '/*':
            res[i] = ' '; res[i+1] = ' '
            i += 2
            while i + 1 < n and not (code[i] == '*' and i + 1 < n and code[i+1] == '/'):
                if code[i] != '\n':
                    res[i] = ' '
                i += 1
            if i + 1 < n:
                res[i] = ' '; res[i+1] = ' '; i += 2
        elif code[i] == '"':
            # Check for Java text block """
            if i + 2 < n and code[i+1] == '"' and code[i+2] == '"':
                res[i] = ' '; res[i+1] = ' '; res[i+2] = ' '
                i += 3
                while i + 2 < n and not (code[i] == '"' and code[i+1] == '"' and code[i+2] == '"'):
                    if code[i] == '\\':
                        res[i] = ' '
                        if i + 1 < n:
                            res[i+1] = ' '
                            i += 2
                            continue
                    elif code[i] != '\n':
                        res[i] = ' '
                    i += 1
                if i + 2 < n:
                    res[i] = ' '; res[i+1] = ' '; res[i+2] = ' '; i += 3
            else:
                res[i] = ' '; i += 1
                while i < n and code[i] != '"':
                    if code[i] == '\\':
                        res[i] = ' '
                        if i + 1 < n:
                            res[i+1] = ' '
                            i += 2
                            continue
                    elif code[i] != '\n':
                        res[i] = ' '
                    i += 1
                if i < n:
                    res[i] = ' '; i += 1
        elif code[i] == "'":
            res[i] = ' '; i += 1
            while i < n and code[i] != "'":
                if code[i] == '\\':
                    res[i] = ' '
                    if i + 1 < n:
                        res[i+1] = ' '
                        i += 2
                        continue
                elif code[i] != '\n':
                    res[i] = ' '
                i += 1
            if i < n:
                res[i] = ' '; i += 1
        else:
            i += 1
    return "".join(res)


def _mask_sql_syntax(sql: str) -> str:
    """Masks SQL comments and string literals with spaces while preserving keywords and syntax."""
    res = list(sql)
    i = 0
    n = len(sql)
    while i < n:
        if i + 1 < n and sql[i:i+2] == '--':
            res[i] = ' '; res[i+1] = ' '
            i += 2
            while i < n and sql[i] != '\n':
                res[i] = ' '; i += 1
        elif i + 1 < n and sql[i:i+2] == '/*':
            res[i] = ' '; res[i+1] = ' '
            i += 2
            while i + 1 < n and not (sql[i] == '*' and i + 1 < n and sql[i+1] == '/'):
                if sql[i] != '\n':
                    res[i] = ' '
                i += 1
            if i + 1 < n:
                res[i] = ' '; res[i+1] = ' '; i += 2
        elif i + 1 < n and sql[i:i+2] == '$$':
            res[i] = ' '; res[i+1] = ' '
            i += 2
            while i + 1 < n and not (sql[i] == '$' and i + 1 < n and sql[i+1] == '$'):
                if sql[i] != '\n':
                    res[i] = ' '
                i += 1
            if i + 1 < n:
                res[i] = ' '; res[i+1] = ' '; i += 2
        elif sql[i] == "'":
            res[i] = ' '; i += 1
            while i < n:
                if sql[i] == "'" and i + 1 < n and sql[i+1] == "'":
                    res[i] = ' '; res[i+1] = ' '; i += 2
                elif sql[i] == "'":
                    res[i] = ' '; i += 1
                    break
                else:
                    if sql[i] != '\n':
                        res[i] = ' '
                    i += 1
        elif sql[i] == '"':
            res[i] = ' '; i += 1
            while i < n and sql[i] != '"':
                if sql[i] != '\n':
                    res[i] = ' '
                i += 1
            if i < n:
                res[i] = ' '; i += 1
        else:
            i += 1
    return "".join(res)


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
            clean_content = sanitize_secrets(content)
            return self._chunk_config(clean_content, rel_path, file_path.name)
        elif suffix == ".md":
            return self._chunk_markdown(content, rel_path, file_path.name)
        else:
            clean_content = sanitize_secrets(content)
            return self._chunk_fallback(clean_content, rel_path, file_path.name)

    def _chunk_java(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        """Extract Java class overview and individual methods using syntax-aware masking."""
        chunks = []
        lines = content.splitlines()
        total_lines = len(lines)

        masked = _mask_java_syntax(content)

        # Detect package and class name
        package_match = re.search(r"^\s*package\s+([\w\.]+);", masked, re.MULTILINE)
        package_name = package_match.group(1) if package_match else ""

        class_match = re.search(
            r"\b(?:public|protected|private)?\s*(?:class|interface|enum|record)\s+(\w+)[^{]*\{",
            masked
        )
        class_name = class_match.group(1) if class_match else Path(file_name).stem
        class_body_start = class_match.end() - 1 if class_match else 0

        # Chunk 1: Class Summary / Header
        header_lines = min(40, total_lines)
        summary_text = (
            f"// File: {rel_path}\n"
            f"// Package: {package_name}\n"
            f"// Class: {class_name}\n\n"
            + "\n".join(lines[:header_lines])
        )
        chunk_id = hashlib.sha256(f"{rel_path}:class_summary:{class_name}".encode("utf-8")).hexdigest()[:32]
        content_hash = hashlib.sha256(summary_text.encode("utf-8")).hexdigest()
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
                metadata={
                    "package": package_name,
                    "class": class_name,
                    "is_class_header": True,
                    "content_hash": content_hash,
                }
            )
        )

        JAVA_KEYWORDS = {
            "if", "for", "while", "switch", "catch", "synchronized", "super",
            "this", "return", "throw", "new", "assert", "else", "try", "finally",
            "do", "yield", "class", "interface", "enum", "record"
        }

        idx = class_body_start + 1
        last_boundary = class_body_start + 1
        ident_pattern = re.compile(r"\b([a-zA-Z_]\w*)\s*\(")
        method_counts: dict[str, int] = {}

        while idx < len(masked):
            match = ident_pattern.search(masked, idx)
            if not match:
                break

            method_name = match.group(1)
            paren_open = match.end() - 1  # at '('

            if method_name in JAVA_KEYWORDS:
                idx = paren_open + 1
                continue

            # Find matching ')'
            p_count = 1
            p_idx = paren_open + 1
            while p_idx < len(masked) and p_count > 0:
                if masked[p_idx] == '(':
                    p_count += 1
                elif masked[p_idx] == ')':
                    p_count -= 1
                p_idx += 1

            if p_count != 0:
                idx = paren_open + 1
                continue

            paren_close = p_idx  # index after ')'

            # Look ahead from paren_close for '{' (opening brace of method body)
            tail = masked[paren_close:]
            head_match = re.match(r"^(\s*(?:throws\s+[\w,\s\.\<\>\[\]]+)?\s*)(\{)", tail)
            if not head_match:
                # Abstract method, interface method, or field call
                idx = paren_close
                continue

            brace_open = paren_close + head_match.start(2)

            # Find matching '}' for method body
            b_count = 1
            b_idx = brace_open + 1
            while b_idx < len(masked) and b_count > 0:
                if masked[b_idx] == '{':
                    b_count += 1
                elif masked[b_idx] == '}':
                    b_count -= 1
                b_idx += 1

            if b_count != 0:
                idx = brace_open + 1
                continue

            brace_close = b_idx

            # Determine method start: include annotations, javadocs, and modifiers
            prefix_region = content[last_boundary:match.start(1)]
            lines_in_prefix = prefix_region.splitlines(keepends=True)
            method_prefix_start = last_boundary
            for line in lines_in_prefix:
                stripped = line.strip()
                if stripped.startswith("@") or any(
                    stripped.startswith(m) for m in ["public", "protected", "private", "static", "final", "synchronized", "default"]
                ):
                    line_idx = content.find(line, last_boundary)
                    if line_idx != -1:
                        method_prefix_start = line_idx
                        break

            method_text = content[method_prefix_start:brace_close].strip()
            start_line = content[:method_prefix_start].count("\n") + 1
            end_line = start_line + method_text.count("\n")

            count = method_counts.get(method_name, 0)
            method_counts[method_name] = count + 1
            occ_suffix = f":{count}" if count > 0 else ""

            annotated_content = (
                f"// File: {rel_path} (Lines {start_line}-{end_line})\n"
                f"// Class: {class_name} | Method: {method_name}\n\n"
                f"{method_text}"
            )

            m_chunk_id = hashlib.sha256(
                f"{rel_path}:method:{class_name}.{method_name}{occ_suffix}".encode("utf-8")
            ).hexdigest()[:32]
            m_content_hash = hashlib.sha256(annotated_content.encode("utf-8")).hexdigest()

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
                    metadata={
                        "package": package_name,
                        "class": class_name,
                        "method": method_name,
                        "content_hash": m_content_hash,
                    }
                )
            )

            last_boundary = brace_close
            idx = brace_close

        return chunks

    def _chunk_sql(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        """Split SQL migrations by statements, preserving stored procedures and blocks."""
        chunks = []
        masked = _mask_sql_syntax(content)
        n = len(content)

        def is_word(pos: int, word: str) -> bool:
            w_len = len(word)
            if masked[pos:pos+w_len].upper() == word:
                before_ok = (pos == 0 or not masked[pos-1].isalnum() and masked[pos-1] != '_')
                after_ok = (pos + w_len >= n or not masked[pos+w_len].isalnum() and masked[pos+w_len] != '_')
                return before_ok and after_ok
            return False

        statements = []
        start = 0
        i = 0
        block_depth = 0

        while i < n:
            if is_word(i, "BEGIN"):
                block_depth += 1
                i += 5
                continue
            elif is_word(i, "END"):
                block_depth = max(0, block_depth - 1)
                i += 3
                continue
            elif masked[i] == ';':
                if block_depth == 0:
                    stmt = content[start:i+1].strip()
                    if stmt:
                        stmt_start_line = content[:start].count("\n") + 1
                        stmt_end_line = stmt_start_line + stmt.count("\n")
                        statements.append((stmt, stmt_start_line, stmt_end_line))
                    start = i + 1
            i += 1

        remaining = content[start:].strip()
        if remaining:
            stmt_start_line = content[:start].count("\n") + 1
            stmt_end_line = stmt_start_line + remaining.count("\n")
            statements.append((remaining, stmt_start_line, stmt_end_line))

        sql_counts: dict[str, int] = {}
        for i, (stmt, start_line, end_line) in enumerate(statements):
            table_match = re.search(
                r"(?:CREATE\s+TABLE|ALTER\s+TABLE|CREATE\s+INDEX|CREATE\s+(?:OR\s+REPLACE\s+)?(?:PROCEDURE|FUNCTION|VIEW|TRIGGER))\s+(?:IF\s+NOT\s+EXISTS\s+)?([^\s\(;]+)",
                stmt,
                re.IGNORECASE
            )
            symbol = table_match.group(1).replace('"', '') if table_match else f"stmt_{i+1}"
            count = sql_counts.get(symbol, 0)
            sql_counts[symbol] = count + 1
            occ_suffix = f":{count}" if count > 0 else ""

            formatted_content = f"-- Migration: {file_name}\n-- Path: {rel_path}\n-- Target: {symbol}\n\n{stmt}"
            if not formatted_content.endswith(";"):
                formatted_content += ";"

            chunk_id = hashlib.sha256(f"{rel_path}:sql:{symbol}{occ_suffix}".encode("utf-8")).hexdigest()[:32]
            content_hash = hashlib.sha256(formatted_content.encode("utf-8")).hexdigest()

            chunks.append(
                CodeChunk(
                    id=chunk_id,
                    file_path=rel_path,
                    file_name=file_name,
                    language="sql",
                    chunk_type="sql_schema",
                    symbol_name=symbol,
                    content=formatted_content,
                    start_line=start_line,
                    end_line=end_line,
                    metadata={"table_or_index": symbol, "migration_file": file_name, "content_hash": content_hash}
                )
            )

        return chunks

    def _chunk_vue(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        """Split Vue component into template and script setup."""
        chunks = []
        comp_name = Path(file_name).stem

        # Extract <script setup> or <script>
        script_match = re.search(r"(<script[^>]*>.*?</script>)", content, re.DOTALL | re.IGNORECASE)
        if script_match:
            script_content = script_match.group(1).strip()
            chunk_id = hashlib.sha256(f"{rel_path}:vue_script".encode("utf-8")).hexdigest()[:32]
            content_hash = hashlib.sha256(script_content.encode("utf-8")).hexdigest()
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
                    metadata={"component": comp_name, "section": "script", "content_hash": content_hash}
                )
            )

        # Extract <template> (handling any attributes, e.g. <template lang="html"> or <template #header>)
        template_match = re.search(r"(<template(?:\s+[^>]*)?>.*?</template>)", content, re.DOTALL | re.IGNORECASE)
        if template_match:
            tpl_content = template_match.group(1).strip()
            tpl_lines = tpl_content.splitlines()
            chunk_id = hashlib.sha256(f"{rel_path}:vue_template".encode("utf-8")).hexdigest()[:32]
            content_hash = hashlib.sha256(tpl_content.encode("utf-8")).hexdigest()
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
                    metadata={"component": comp_name, "section": "template", "content_hash": content_hash}
                )
            )

        return chunks

    def _chunk_config(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        """Chunk configuration files by blocks."""
        chunks = []
        lines = content.splitlines()
        chunk_id = hashlib.sha256(f"{rel_path}:config".encode("utf-8")).hexdigest()[:32]
        content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
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
                metadata={"config_type": Path(file_name).suffix, "content_hash": content_hash}
            )
        )
        return chunks

    def _chunk_markdown(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        """Chunk Markdown by headings."""
        chunks = []
        sections = re.split(r"\n(?=#{1,3}\s+)", content)
        line_offset = 1
        sec_counts: dict[str, int] = {}

        for i, sec in enumerate(sections):
            if not sec.strip():
                continue
            lines = sec.splitlines()
            title = lines[0].replace("#", "").strip() if lines else f"Section {i+1}"
            title_slug = re.sub(r"[^\w]+", "-", title.lower()).strip("-") or f"section-{i+1}"
            count = sec_counts.get(title_slug, 0)
            sec_counts[title_slug] = count + 1
            occ_suffix = f":{count}" if count > 0 else ""

            chunk_id = hashlib.sha256(f"{rel_path}:doc:{title_slug}{occ_suffix}".encode("utf-8")).hexdigest()[:32]
            content_hash = hashlib.sha256(sec.encode("utf-8")).hexdigest()

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
                    metadata={"section_title": title, "content_hash": content_hash}
                )
            )
            line_offset += len(lines)

        return chunks

    def _chunk_fallback(self, content: str, rel_path: str, file_name: str) -> List[CodeChunk]:
        """Fallback chunker for other source files."""
        lines = content.splitlines()
        if not lines:
            return []
        chunk_id = hashlib.sha256(f"{rel_path}:general".encode("utf-8")).hexdigest()[:32]
        content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
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
                metadata={"content_hash": content_hash}
            )
        ]
