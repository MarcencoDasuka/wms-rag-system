"""Regression test for Finding 2: Secret-bearing configuration ingestion rejection & redaction."""

from pathlib import Path
from src.chunker import CodeAwareChunker, sanitize_secrets
from src.config import AppConfig
from src.indexer import CodebaseIndexer


def test_sanitize_secrets_redacts_credentials():
    """Verify that credentials, passwords, JWT secrets, and private keys are scrubbed."""
    raw_properties = """
# WMS Backend Configuration
server.port=8080
spring.datasource.url=jdbc:postgresql://localhost:5432/wms_db
spring.datasource.username=wms_admin
spring.datasource.password=sample_plain_pass_456
jwt.secret=sample_raw_jwt_secret_token_789
api.token=sample_api_bearer_token_abc # inline comment
wms.security.admin-credential=sample_cred_value
spring.datasource.secondary-password=${SECONDARY_DB_PASS:sample_default_fallback_pass}
"""
    sanitized = sanitize_secrets(raw_properties)

    # Invariants: sensitive values must be replaced with [REDACTED]
    assert "sample_plain_pass_456" not in sanitized
    assert "sample_raw_jwt_secret_token_789" not in sanitized
    assert "sample_api_bearer_token_abc" not in sanitized
    assert "sample_cred_value" not in sanitized
    assert "sample_default_fallback_pass" not in sanitized

    # Preserved non-sensitive configs
    assert "server.port=8080" in sanitized
    assert "wms_admin" in sanitized
    assert "jdbc:postgresql://localhost:5432/wms_db" in sanitized
    assert "spring.datasource.password=[REDACTED]" in sanitized
    assert "jwt.secret=[REDACTED]" in sanitized


def test_sanitize_secrets_redacts_private_key_blocks():
    """Verify that PEM private key blocks are scrubbed."""
    content_with_key = """
-----BEGIN RSA PRIVATE KEY-----
MIIEowIBAAKCAQEA0Y3y...DUMMY_KEY_DATA...
-----END RSA PRIVATE KEY-----
some_code()
"""
    sanitized = sanitize_secrets(content_with_key)
    assert "DUMMY_KEY_DATA" not in sanitized
    assert "[REDACTED_PRIVATE_KEY]" in sanitized
    assert "some_code()" in sanitized


def test_chunker_redacts_sensitive_properties_file(tmp_path: Path):
    """Verify that chunking a properties file strips secrets from chunk content."""
    prop_file = tmp_path / "application.properties"
    prop_file.write_text("spring.datasource.password=raw_test_pass_xyz\njwt.secret=raw_jwt_xyz\n", encoding="utf-8")

    chunker = CodeAwareChunker()
    chunks = chunker.chunk_file(prop_file, "application.properties")

    assert len(chunks) == 1
    assert "raw_test_pass_xyz" not in chunks[0].content
    assert "raw_jwt_xyz" not in chunks[0].content
    assert "spring.datasource.password=[REDACTED]" in chunks[0].content
    assert "jwt.secret=[REDACTED]" in chunks[0].content


def test_indexer_ignores_sensitive_files(tmp_path: Path):
    """Verify that indexer skips secret-bearing filenames and paths."""
    # Create allowed files
    (tmp_path / "OrderService.java").write_text("public class OrderService {}", encoding="utf-8")
    (tmp_path / "application.properties").write_text("app.name=wms", encoding="utf-8")

    # Create sensitive files that must be excluded
    (tmp_path / ".env").write_text("DB_PASS=123", encoding="utf-8")
    (tmp_path / ".env.local").write_text("SECRET=123", encoding="utf-8")
    (tmp_path / "jwt_secret.properties").write_text("jwt=123", encoding="utf-8")
    (tmp_path / "credentials.yaml").write_text("pass: 123", encoding="utf-8")
    (tmp_path / "id_rsa").write_text("KEY", encoding="utf-8")
    (tmp_path / "server.keystore").write_text("STORE", encoding="utf-8")

    config = AppConfig()
    indexer = CodebaseIndexer(config)
    files_scanned, _ = indexer.scan_and_index(target_dir_override=str(tmp_path), clear_first=False)

    # Only OrderService.java and application.properties should be scanned
    assert files_scanned == 2
