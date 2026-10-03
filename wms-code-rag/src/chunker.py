"""Code-aware chunking for WMS files (Java, SQL, Vue, Properties, YAML, Markdown)."""

import hashlib
import re
from pathlib import Path
from typing import Any, List, Optional
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
    r"(?i)(password|passwd|secret|jwt|token|credential|api[_-]?key|private[_-]?key|"
    r"access[_-]?key|auth[_-]?token|bearer[_-]?token|datasource\.password|"
    r"authorization|signing[_-]?key|encryption[_-]?key|master[_-]?key|"
    r"client[_-]?auth|auth[_-]?val(?:ue)?|cipher[_-]?key)"
)

PRIVATE_KEY_BLOCK_PATTERN = re.compile(
    r"-----BEGIN [A-Z0-9_-]+ PRIVATE KEY-----[\s\S]*?-----END [A-Z0-9_-]+ PRIVATE KEY-----",
    re.MULTILINE
)

CODE_ASSIGN_SECRET_PATTERN = re.compile(
    r"(?i)(\b[\w\.]*(?:password|passwd|secret|jwt|token|api[_-]?key|private[_-]?key|"
    r"signing[_-]?key|master[_-]?key|encryption[_-]?key|authorization|client[_-]?auth)[\w\.]*\s*=\s*)"
    r'(["\'])(?:\\.|[^\\])*?\2'
)

SETTER_SECRET_PATTERN = re.compile(
    r"(?i)(\.set(?:Password|Passwd|Secret|Token|ApiKey|PrivateKey|SigningKey|MasterKey|EncryptionKey)\s*\(\s*)"
    r'(["\'])(?:\\.|[^\\])*?\2(\s*\))'
)

SQL_PASSWORD_PATTERN = re.compile(
    r"(?i)(\b(?:IDENTIFIED\s+BY|PASSWORD)\s+)(['\"])(?:\\.|[^\\])*?\2"
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

    # 2. Scrub method setters and SQL password clauses
    sanitized = SETTER_SECRET_PATTERN.sub(r'\1"[REDACTED]"\3', sanitized)
    sanitized = SQL_PASSWORD_PATTERN.sub(r"\1'[REDACTED]'", sanitized)

    # 3. Scrub key-value configuration lines and variable assignments
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

        # Scrub code variable assignments (Java, JS, Vue, Python): e.g. String jwtSecret = "..."
        line = CODE_ASSIGN_SECRET_PATTERN.sub(r'\1"[REDACTED]"', line)

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


def _extract_vue_block(content: str, tag_name: str) -> Optional[str]:
    """Safely extracts a root Vue block (<template> or <script>), masking comments and string literals."""
    tag_lower = tag_name.lower()
    open_pattern = re.compile(rf"<{tag_lower}\b", re.IGNORECASE)
    match = open_pattern.search(content)
    if not match:
        return None
    start_pos = match.start()

    # Find end of opening tag, respecting attribute quotes (e.g. <template v-if="x > 0">)
    i = match.end()
    in_quote = None
    while i < len(content):
        ch = content[i]
        if in_quote:
            if ch == in_quote and content[i-1] != '\\':
                in_quote = None
        elif ch in ('"', "'"):
            in_quote = ch
        elif ch == '>':
            i += 1
            break
        i += 1

    open_tag_end = i
    depth = 1
    i = open_tag_end
    in_quote = None
    n = len(content)

    while i < n:
        # Ignore HTML comments: <!-- ... </template> ... -->
        if not in_quote and content[i:i+4] == "<!--":
            end_cmt = content.find("-->", i + 4)
            if end_cmt == -1:
                break
            i = end_cmt + 3
            continue

        ch = content[i]
        if in_quote:
            if ch == in_quote and content[i-1] != '\\':
                in_quote = None
            i += 1
            continue

        if ch in ('"', "'", '`'):
            in_quote = ch
            i += 1
            continue

        if content[i:i+2] == "//":
            end_line = content.find("\n", i + 2)
            i = n if end_line == -1 else end_line + 1
            continue

        if content[i:i+2] == "/*":
            end_block = content.find("*/", i + 2)
            i = n if end_block == -1 else end_block + 2
            continue

        if tag_lower == "template" and content[i:i+9].lower() == "<template":
            depth += 1
            i += 9
            continue

        close_tag = f"</{tag_lower}"
        if content[i:i+len(close_tag)].lower() == close_tag:
            after = content[i+len(close_tag):]
            m_close = re.match(r"^\s*>", after)
            if m_close:
                depth -= 1
                if depth == 0:
                    end_pos = i + len(close_tag) + m_close.end()
                    return content[start_pos:end_pos]
                i += len(close_tag) + m_close.end()
                continue

        i += 1

    return None


class CodeAwareChunker:
    """Specialized chunker that preserves semantic boundaries for code and configs."""

    def __init__(self, max_chunk_lines: int = 80):
        self.max_chunk_lines = max_chunk_lines

    def chunk_file(self, file_path: Path, rel_path: str) -> List[CodeChunk]:
        """Route file to appropriate language chunker based on extension."""
        try:
            raw_content = file_path.read_text(encoding="utf-8", errors="replace")
        except Exception:
            return []

        # Sanitize secrets across all ingested files (Java, SQL, Vue, Properties, YAML, etc.)
        content = sanitize_secrets(raw_content)

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
        """Extract Java class overview, inner types, and methods (including interface/abstract/record declarations)."""
        chunks = []
        lines = content.splitlines()
        total_lines = len(lines)

        masked = _mask_java_syntax(content)

        # Detect package and primary class/interface/enum/record
        package_match = re.search(r"^\s*package\s+([\w\.]+);", masked, re.MULTILINE)
        package_name = package_match.group(1) if package_match else ""

        class_match = re.search(
            r"\b(?:(?:public|protected|private|abstract|static|final|sealed|non-sealed)\s+)*(class|interface|enum|record)\s+(\w+)[^{]*\{",
            masked
        )
        if class_match:
            decl_type = class_match.group(1)
            class_name = class_match.group(2)
            decl_line = content[:class_match.start(2)].count("\n") + 1
            class_body_start = class_match.end() - 1
        else:
            decl_type = "class"
            class_name = Path(file_name).stem
            decl_line = 1
            class_body_start = 0

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
                    "declaration_type": decl_type,
                    "declaration_line": decl_line,
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

        # Check for compact constructor in records: RecordName { ... }
        if decl_type == "record":
            compact_ctor_match = re.search(
                rf"\b(?:(?:public|protected|private)\s+)?({re.escape(class_name)})\s*\{{",
                masked[class_body_start:]
            )
            if compact_ctor_match:
                c_start_offset = class_body_start + compact_ctor_match.start(1)
                c_brace_open = class_body_start + compact_ctor_match.end() - 1
                b_count = 1
                b_idx = c_brace_open + 1
                while b_idx < len(masked) and b_count > 0:
                    if masked[b_idx] == '{':
                        b_count += 1
                    elif masked[b_idx] == '}':
                        b_count -= 1
                    b_idx += 1
                if b_count == 0:
                    c_body_text = content[c_start_offset:b_idx].strip()
                    c_start_line = content[:c_start_offset].count("\n") + 1
                    c_end_line = c_start_line + c_body_text.count("\n")
                    annotated = (
                        f"// File: {rel_path} (Lines {c_start_line}-{c_end_line})\n"
                        f"// Class: {class_name} | Method: {class_name}\n\n"
                        f"{c_body_text}"
                    )
                    c_id = hashlib.sha256(f"{rel_path}:method:{class_name}.{class_name}".encode("utf-8")).hexdigest()[:32]
                    chunks.append(
                        CodeChunk(
                            id=c_id,
                            file_path=rel_path,
                            file_name=file_name,
                            language="java",
                            chunk_type="method",
                            symbol_name=f"{class_name}.{class_name}",
                            content=annotated,
                            start_line=c_start_line,
                            end_line=c_end_line,
                            metadata={
                                "package": package_name,
                                "class": class_name,
                                "method": class_name,
                                "declaration_type": "constructor",
                                "declaration_line": c_start_line,
                                "content_hash": hashlib.sha256(annotated.encode("utf-8")).hexdigest(),
                            }
                        )
                    )

        idx = class_body_start + 1
        last_boundary = class_body_start + 1
        ident_pattern = re.compile(r"\b([a-zA-Z_]\w*)\s*\(")
        inner_type_pattern = re.compile(
            r"\b(?:(?:public|protected|private|abstract|static|final|sealed|non-sealed)\s+)*(record|class|interface|enum)\s+(\w+)[^{;]*\{"
        )
        method_counts: dict[str, int] = {}

        while idx < len(masked):
            m_method = ident_pattern.search(masked, idx)
            m_inner = inner_type_pattern.search(masked, idx)

            # If inner type declaration (e.g. inner record / class / enum) comes first
            if m_inner and (not m_method or m_inner.start() < m_method.start()):
                inner_kind = m_inner.group(1)
                inner_name = m_inner.group(2)
                brace_open = m_inner.end() - 1
                b_count = 1
                b_idx = brace_open + 1
                while b_idx < len(masked) and b_count > 0:
                    if masked[b_idx] == '{':
                        b_count += 1
                    elif masked[b_idx] == '}':
                        b_count -= 1
                    b_idx += 1

                if b_count == 0:
                    inner_prefix_start = last_boundary
                    prefix_region = content[last_boundary:m_inner.start(1)]
                    lines_in_prefix = prefix_region.splitlines(keepends=True)
                    for line in lines_in_prefix:
                        stripped = line.strip()
                        if not stripped:
                            continue
                        if stripped.startswith("@") or stripped.startswith("/*") or stripped.startswith("//") or any(
                            stripped.startswith(m) for m in ["public", "protected", "private", "static", "final", "sealed", "non-sealed", inner_kind]
                        ):
                            line_idx = content.find(line, last_boundary)
                            if line_idx != -1:
                                inner_prefix_start = line_idx
                                break

                    inner_text = content[inner_prefix_start:b_idx].strip()
                    i_start_line = content[:inner_prefix_start].count("\n") + 1
                    i_end_line = i_start_line + inner_text.count("\n")
                    i_decl_line = content[:m_inner.start(2)].count("\n") + 1

                    annotated = (
                        f"// File: {rel_path} (Lines {i_start_line}-{i_end_line})\n"
                        f"// Class: {class_name} | {inner_kind.capitalize()}: {inner_name}\n\n"
                        f"{inner_text}"
                    )
                    inner_chunk_id = hashlib.sha256(f"{rel_path}:class_summary:{class_name}.{inner_name}".encode("utf-8")).hexdigest()[:32]
                    chunks.append(
                        CodeChunk(
                            id=inner_chunk_id,
                            file_path=rel_path,
                            file_name=file_name,
                            language="java",
                            chunk_type="class_summary",
                            symbol_name=f"{class_name}.{inner_name}",
                            content=annotated,
                            start_line=i_start_line,
                            end_line=i_end_line,
                            metadata={
                                "package": package_name,
                                "class": class_name,
                                "inner_name": inner_name,
                                "declaration_type": inner_kind,
                                "declaration_line": i_decl_line,
                                "is_inner_declaration": True,
                                "content_hash": hashlib.sha256(annotated.encode("utf-8")).hexdigest(),
                            }
                        )
                    )
                    last_boundary = b_idx
                    idx = b_idx
                    continue
                else:
                    idx = m_inner.end()
                    continue

            if not m_method:
                break

            method_name = m_method.group(1)
            paren_open = m_method.end() - 1

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

            paren_close = p_idx
            tail = masked[paren_close:]

            body_match = re.match(r"^(\s*(?:throws\s+[\w,\s\.\<\>\[\]]+)?\s*)(\{)", tail)
            decl_match = re.match(r"^(\s*(?:throws\s+[\w,\s\.\<\>\[\]]+)?\s*)(;)", tail)

            if body_match:
                brace_open = paren_close + body_match.start(2)
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

                end_pos = b_idx
            elif decl_match:
                # Validation checks for declaration-only methods (abstract / interface / repository)
                prefix_to_ident = masked[last_boundary:m_method.start(1)]
                # Check 1: Must not be a method call like obj.method()
                if prefix_to_ident.rstrip().endswith("."):
                    idx = paren_close
                    continue

                # Check 2: Strip annotations and ensure no '=' assignment operator exists in prefix
                unannotated = re.sub(r"@\w+(?:\([^)]*\))?", " ", prefix_to_ident)
                if "=" in unannotated:
                    idx = paren_close
                    continue

                # Check 3: Must not be a statement keyword like return, throw, new, assert
                if re.search(r"\b(?:return|throw|new|assert)\b", unannotated):
                    idx = paren_close
                    continue

                # Check 4: Must contain a return type or modifier
                if not re.search(r"\b(?:public|protected|private|abstract|default|static|final|native|void|boolean|byte|short|int|long|char|float|double|[A-Z]\w*)\b", unannotated):
                    idx = paren_close
                    continue

                semicolon_pos = paren_close + decl_match.end(2)
                end_pos = semicolon_pos
            else:
                idx = paren_close
                continue

            # Determine method start: include annotations, javadocs, modifiers, return type
            prefix_region = content[last_boundary:m_method.start(1)]
            lines_in_prefix = prefix_region.splitlines(keepends=True)
            method_prefix_start = last_boundary
            for line in lines_in_prefix:
                stripped = line.strip()
                if not stripped:
                    continue
                if (
                    stripped.startswith("@")
                    or stripped.startswith("/*")
                    or stripped.startswith("*")
                    or stripped.startswith("//")
                    or any(stripped.startswith(m) for m in ["public", "protected", "private", "static", "final", "synchronized", "default", "abstract", "native"])
                    or re.match(r"^(?:<[\w\s,\.\<\>\[\]]+>\s+)?[\w\<\>\[\]]+\s+", stripped)
                ):
                    line_idx = content.find(line, last_boundary)
                    if line_idx != -1:
                        method_prefix_start = line_idx
                        break

            method_text = content[method_prefix_start:end_pos].strip()
            start_line = content[:method_prefix_start].count("\n") + 1
            end_line = start_line + method_text.count("\n")
            decl_line = content[:m_method.start(1)].count("\n") + 1

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
                        "declaration_type": "method",
                        "declaration_line": decl_line,
                        "content_hash": m_content_hash,
                    }
                )
            )

            last_boundary = end_pos
            idx = end_pos

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

        # Extract <script setup> or <script> using syntax-safe parser
        script_block = _extract_vue_block(content, "script")
        if script_block:
            script_content = script_block.strip()
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

        # Extract <template> using syntax-safe parser
        template_block = _extract_vue_block(content, "template")
        if template_block:
            tpl_content = template_block.strip()
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
