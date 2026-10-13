"""Tool-facing logic for the Ospysin docs index (kept free of FastMCP imports so it is unit-testable)."""

import re
from typing import List, Optional, Tuple

from src.chunker import CodeChunk
from src.doc_indexer import OspysinDocsIndexer
from src.retriever import CodeRetriever

# Upper bound on text returned by a single get_doc call (characters of chunk content).
MAX_DOC_RESPONSE_CHARS = 24000
MAX_SEARCH_TOP_N = 10


def _esc(text: str) -> str:
    """Neutralise angle brackets/newlines in values echoed back into tool output."""
    return str(text).replace("<", "&lt;").replace(">", "&gt;").replace("\r", " ").replace("\n", " ").strip()


def _chunk_from_row(cid: str, doc: Optional[str], meta: Optional[dict]) -> CodeChunk:
    m = dict(meta or {})
    return CodeChunk(
        id=cid,
        file_path=m.get("file_path", "unknown"),
        file_name=m.get("file_name", "unknown"),
        language=m.get("language", "markdown"),
        chunk_type=m.get("chunk_type", "spec_doc"),
        symbol_name=m.get("symbol_name", ""),
        content=doc or "",
        start_line=int(m.get("start_line", 1)),
        end_line=int(m.get("end_line", 1)),
        metadata=m,
    )


def normalize_doc_key(name_or_id: str) -> Tuple[str, str]:
    """Classify a user-supplied document reference.

    Returns ``(kind, value)`` where kind is ``adr`` (value ``adr-0003``), ``num`` (value ``05``)
    or ``name`` (lower-cased text matched against file stems). Rules:
    - ``adr-3``, ``adr 3``, ``adr0003``, ``0003`` (4 digits) -> ADR
    - bare 1-2 digit numbers (``5``, ``05``) -> numbered spec document
    """
    key = (name_or_id or "").strip().lower()
    m = re.fullmatch(r"adr[\s_-]*0*(\d{1,4})", key)
    if m:
        return "adr", f"adr-{int(m.group(1)):04d}"
    if re.fullmatch(r"\d{4}", key):
        return "adr", f"adr-{key}"
    if re.fullmatch(r"\d{1,2}", key):
        return "num", f"{int(key):02d}"
    return "name", key


class OspysinDocsService:
    """Search / deterministic lookup / reindex over the isolated Ospysin collection."""

    def __init__(self, indexer: OspysinDocsIndexer, retriever: CodeRetriever):
        self.indexer = indexer
        self.retriever = retriever
        self.store = indexer.store
        self._cached_catalog: Optional[dict[str, str]] = None

    # ------------------------------------------------------------------ search

    def search(self, query: str, top_n: int = 4) -> str:
        count = self.store.count()
        if count == 0:
            return (
                "Ospysin docs index is empty. Run 'reindex_ospysin_docs' (confirm=True) "
                "after making sure the docs directory is mounted/configured."
            )
        n = min(max(1, int(top_n)), MAX_SEARCH_TOP_N)
        min_score = getattr(self.indexer.config.ospysin, "min_score", -2.5)
        results = self.retriever.retrieve(query=query, top_n=n, min_score=min_score)
        if not results:
            return (
                "NO_MATCH\nNo Ospysin documentation chunk passed the relevance threshold for this query. "
                "Rephrase, or use 'get_ospysin_doc' for a known document or ADR."
            )
        return (
            self.retriever.format_for_agent(
                results,
                context_tag="untrusted_ospysin_docs_context",
            )
            + "\n"
            + self._index_footer()
        )

    # ------------------------------------------------------------------ deterministic lookup

    def _all_chunks(self) -> List[CodeChunk]:
        data = self.store.collection.get(include=["documents", "metadatas"])
        ids = data.get("ids") or []
        docs = data.get("documents") or []
        metas = data.get("metadatas") or []
        return [_chunk_from_row(i, d, m) for i, d, m in zip(ids, docs, metas)]

    def _get_catalog(self) -> dict[str, str]:
        """Returns mapping of {file_path: doc_id} from metadata (lazy-cached)."""
        if self._cached_catalog is not None:
            return self._cached_catalog
        data = self.store.collection.get(include=["metadatas"])
        metas = data.get("metadatas") or []
        catalog: dict[str, str] = {}
        for m in metas:
            fp = m.get("file_path")
            did = m.get("doc_id")
            if fp and did:
                catalog[fp] = did
        self._cached_catalog = catalog
        return catalog

    def _fetch_chunks_by_filter(self, where: dict) -> List[CodeChunk]:
        data = self.store.collection.get(where=where, include=["documents", "metadatas"])
        ids = data.get("ids") or []
        docs = data.get("documents") or []
        metas = data.get("metadatas") or []
        return [_chunk_from_row(i, d, m) for i, d, m in zip(ids, docs, metas)]

    @staticmethod
    def _order(c: CodeChunk) -> Tuple[int, int]:
        return int(c.metadata.get("start_line", 0)), int(c.metadata.get("part", 1))

    def get_doc(self, name_or_id: str, section: Optional[str] = None) -> str:
        if self.store.count() == 0:
            return "Ospysin docs index is empty. Run 'reindex_ospysin_docs' (confirm=True)."

        catalog = self._get_catalog()
        if not catalog:
            return "Ospysin docs index is empty. Run 'reindex_ospysin_docs' (confirm=True)."

        kind, value = normalize_doc_key(name_or_id)
        if kind in ("adr", "num"):
            chunks = self._fetch_chunks_by_filter({"doc_id": value})
            if not chunks:
                avail = ", ".join(sorted(catalog))
                return f"NOT_FOUND\nname: {_esc(name_or_id)}\navailable_documents: {avail}"
            path = chunks[0].file_path
            doc_chunks = sorted(chunks, key=self._order)
        else:
            stem = lambda p: p.rsplit("/", 1)[-1].rsplit(".", 1)[0].lower()  # noqa: E731
            matches = [p for p in catalog if stem(p) == value] or [p for p in catalog if value and value in stem(p)]
            if not matches:
                avail = ", ".join(sorted(catalog))
                return f"NOT_FOUND\nname: {_esc(name_or_id)}\navailable_documents: {avail}"
            if len(matches) > 1:
                return f"AMBIGUOUS\nname: {_esc(name_or_id)}\ncandidates: {', '.join(sorted(matches))}"
            path = matches[0]
            chunks = self._fetch_chunks_by_filter({"file_path": path})
            doc_chunks = sorted(chunks, key=self._order)

        if section and section.strip():
            needle = section.strip().lower()
            selected = [c for c in doc_chunks if needle in c.metadata.get("heading_path", "").lower()]
            if not selected:
                headings = sorted({c.metadata.get("heading_path", "") for c in doc_chunks}, key=str)
                return (
                    f"SECTION_NOT_FOUND\ndocument: {path}\nsection: {_esc(section)}\n"
                    f"available_sections:\n" + "\n".join(f"- {_esc(h)}" for h in headings)
                )
        else:
            selected = doc_chunks

        kept: List[CodeChunk] = []
        used = 0
        for c in selected:
            if kept and used + len(c.content) > MAX_DOC_RESPONSE_CHARS:
                break
            kept.append(c)
            used += len(c.content)

        out = self.retriever.format_for_agent(
            [(c, 1.0) for c in kept],
            exact=True,
            context_tag="untrusted_ospysin_docs_context",
        )
        notes = [f"FOUND document: {path} ({len(kept)}/{len(selected)} chunks returned)"]
        if len(kept) < len(selected):
            seen: List[str] = []
            for c in doc_chunks:
                hp = c.metadata.get("heading_path", "")
                if hp not in seen:
                    seen.append(hp)
            notes.append(
                "TRUNCATED: response budget reached. Call again with section=<heading substring>. "
                "available_sections:\n" + "\n".join(f"- {_esc(h)}" for h in seen)
            )
        return "\n".join(notes) + "\n" + out + "\n" + self._index_footer()

    # ------------------------------------------------------------------ reindex / status

    def reindex(self, confirm: bool) -> str:
        if not confirm:
            return (
                "Warning: reindex_ospysin_docs clears and rebuilds ONLY the Ospysin docs collection "
                f"('{self.store.collection_name}'); the WMS code index is not touched. Pass confirm=True to proceed."
            )
        try:
            files, chunks = self.indexer.scan_and_index(clear_first=True)
            self._cached_catalog = None
        except RuntimeError as e:
            return f"Error: {e}"
        meta = self.indexer.read_index_meta()
        return (
            "Ospysin docs re-indexing complete!\n"
            f"- Scanned Files: {files}\n"
            f"- Indexed Chunks: {chunks}\n"
            f"- Total in Collection: {self.store.count()}\n"
            f"- Source Revision: {meta.get('source_commit', 'unknown')}\n"
        )

    def _index_footer(self) -> str:
        meta = self.indexer.read_index_meta()
        return (
            f"[ospysin-index: collection={self.store.collection_name} chunks={self.store.count()} "
            f"source_commit={_esc(meta.get('source_commit', 'unknown'))}]"
        )

    def status(self) -> str:
        meta = self.indexer.read_index_meta()
        return (
            "Ospysin Docs Index:\n"
            f"- Collection: {self.store.collection_name}\n"
            f"- Docs Dir: {self.indexer.resolve_docs_dir()}\n"
            f"- Total Indexed Chunks: {self.store.count()}\n"
            f"- Source Revision: {meta.get('source_commit', 'unknown')}\n"
            f"- Indexed At: {meta.get('indexed_at', 'never')}\n"
        )
