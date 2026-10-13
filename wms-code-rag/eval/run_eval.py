"""Retrieval evaluation for the Ospysin docs index: new MarkdownSpecChunker vs the legacy baseline.

Usage (from wms-code-rag/):
    python -m eval.run_eval --mode both
    python -m eval.run_eval --mode new --no-rerank

Builds throw-away indexes in a temporary directory; the production ``data/chroma`` is never touched.
Metrics: Hit@1, Hit@4, MRR over positive queries; negative queries report the share that correctly
returned nothing. Matching: file path equals AND expected heading is a case-insensitive substring of
the chunk's leaf section title (empty heading = any section in the file).
"""

import argparse
import sys
import tempfile
from pathlib import Path
from typing import List, Tuple

import yaml

PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from src.chunker import CodeAwareChunker, CodeChunk  # noqa: E402
from src.config import AppConfig, load_config  # noqa: E402
from src.doc_indexer import OspysinDocsIndexer  # noqa: E402
from src.doc_service import OspysinDocsService  # noqa: E402
from src.embedder import SentenceTransformerEmbedder  # noqa: E402
from src.retriever import CodeRetriever  # noqa: E402
from src.vector_store import CodeVectorStore  # noqa: E402

QUERIES_PATH = Path(__file__).resolve().parent / "ospysin_queries.yaml"
TOP_N = 4


def leaf_title(chunk: CodeChunk) -> str:
    return str(chunk.metadata.get("section_title") or chunk.symbol_name or "")


def is_hit(chunk: CodeChunk, expected: List[dict]) -> bool:
    title = leaf_title(chunk).lower()
    for tgt in expected:
        if chunk.file_path == tgt["file"] and tgt.get("heading", "").lower() in title:
            return True
    return False


def build_new(config: AppConfig, tmp: Path, embedder) -> Tuple[CodeRetriever, OspysinDocsIndexer]:
    config.vector_db.persist_dir = str(tmp / "new")
    config.ospysin.collection_name = "eval_new"
    indexer = OspysinDocsIndexer(config, embedder=embedder)
    indexer.scan_and_index(clear_first=True)
    return CodeRetriever(config, store=indexer.store), indexer


def build_baseline(config: AppConfig, tmp: Path, embedder) -> CodeRetriever:
    """Index the same docs with the legacy ``_chunk_markdown`` into a separate collection."""
    store = CodeVectorStore(
        persist_dir=str(tmp / "baseline"),
        collection_name="eval_baseline",
        embedding_model=config.embeddings.default_model,
    )
    docs_dir = Path(config.ospysin.docs_dir)
    if not docs_dir.is_absolute():
        docs_dir = (PROJECT_ROOT / docs_dir).resolve()
    chunker = CodeAwareChunker()
    chunks: List[CodeChunk] = []
    for f in sorted(docs_dir.rglob("*.md")):
        chunks.extend(chunker.chunk_file(f, f.relative_to(docs_dir).as_posix()))
    store.add_chunks(chunks, embedder.embed_texts([c.content for c in chunks]))
    print(f"  baseline chunks: {len(chunks)}")
    return CodeRetriever(config, store=store)


def evaluate(retriever: CodeRetriever, queries: List[dict], label: str) -> dict:
    hit1 = hit4 = 0
    rr_sum = 0.0
    positives = [q for q in queries if q["type"] != "negative"]
    negatives = [q for q in queries if q["type"] == "negative"]
    misses = []
    for q in positives:
        results = retriever.retrieve(query=q["query"], top_n=TOP_N)
        rank = next((i for i, (c, _) in enumerate(results, 1) if is_hit(c, q["expected"])), None)
        if rank == 1:
            hit1 += 1
        if rank is not None and rank <= TOP_N:
            hit4 += 1
            rr_sum += 1.0 / rank
        else:
            top = [f"{c.file_path}#{leaf_title(c)}" for c, _ in results[:2]]
            misses.append((q["id"], q["query"], top))
    neg_ok = 0
    neg_bad = []
    for q in negatives:
        results = retriever.retrieve(query=q["query"], top_n=TOP_N)
        if not results:
            neg_ok += 1
        else:
            neg_bad.append((q["id"], q["query"], f"{results[0][0].file_path}#{leaf_title(results[0][0])}"))
    n = len(positives)
    out = {
        "label": label,
        "n": n,
        "hit1": hit1 / n,
        "hit4": hit4 / n,
        "mrr": rr_sum / n,
        "neg_ok": neg_ok,
        "neg_n": len(negatives),
        "misses": misses,
        "neg_bad": neg_bad,
    }
    return out


def print_report(r: dict) -> None:
    print(f"\n=== {r['label']} ===")
    print(f"positives={r['n']}  Hit@1={r['hit1']:.3f}  Hit@{TOP_N}={r['hit4']:.3f}  MRR={r['mrr']:.3f}")
    print(f"true negatives returning nothing: {r['neg_ok']}/{r['neg_n']}")
    for qid, q, top in r["misses"]:
        print(f"  MISS {qid}: {q!r} -> top: {top}")
    for qid, q, top in r["neg_bad"]:
        print(f"  NEG-FP {qid}: {q!r} -> {top}")


def run_lookups(service: OspysinDocsService, lookups: List[dict]) -> None:
    ok = 0
    for item in lookups:
        out = service.get_doc(item["name"], item.get("section"))
        passed = out.startswith(item["prefix"])
        ok += passed
        if not passed:
            print(f"  LOOKUP FAIL {item['name']!r}/{item.get('section')!r}: got {out[:80]!r}")
    print(f"\n=== deterministic get_ospysin_doc checks: {ok}/{len(lookups)} passed ===")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mode", choices=["new", "baseline", "both"], default="both")
    parser.add_argument("--no-rerank", action="store_true", help="Disable cross-encoder reranking")
    args = parser.parse_args()

    spec = yaml.safe_load(QUERIES_PATH.read_text(encoding="utf-8"))
    queries, lookups = spec["queries"], spec.get("lookups", [])

    config = load_config()
    config.ospysin.enabled = True
    if args.no_rerank:
        config.reranking.enabled = False
    embedder = SentenceTransformerEmbedder.get_instance(
        model_name=config.embeddings.default_model, device=config.embeddings.device
    )
    rerank_label = "rerank=off" if args.no_rerank else "rerank=on"

    with tempfile.TemporaryDirectory(prefix="ospysin_eval_", ignore_cleanup_errors=True) as td:
        tmp = Path(td)
        if args.mode in ("baseline", "both"):
            print("Building baseline index...")
            base = build_baseline(config, tmp, embedder)
            print_report(evaluate(base, queries, f"BASELINE legacy _chunk_markdown ({rerank_label})"))
        if args.mode in ("new", "both"):
            print("Building new index...")
            retriever, indexer = build_new(config, tmp, embedder)
            print(f"  new chunks: {indexer.store.count()}")
            print_report(evaluate(retriever, queries, f"NEW MarkdownSpecChunker ({rerank_label})"))
            run_lookups(OspysinDocsService(indexer, retriever), lookups)
    return 0


if __name__ == "__main__":
    sys.exit(main())
