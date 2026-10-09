package com.isd.wms.service.ai;

import com.isd.wms.entity.Product;
import com.isd.wms.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service that indexes product information into a vector store for semantic search.
 * <p>
 * On application startup, all existing products are indexed asynchronously. The index is updated
 * whenever a product is created, updated, or deleted. Each product is represented
 * as a {@link Document} containing its name, description, and metadata (barcode, ID).
 * </p>
 * <p>
 * The vector store (PGVector) enables similarity search via the AI tools, allowing
 * users to find products by conceptual description rather than exact barcode.
 * </p>
 *
 * @see VectorStore
 * @see Product
 * @see InventoryAiTools#searchProductByName(String)
 */
@Slf4j
@Service
public class ProductVectorIndexer {

    public enum VectorIndexStatus {
        NOT_STARTED,
        IN_PROGRESS,
        READY,
        FAILED
    }

    private final ProductRepository productRepository;
    private final VectorStore vectorStore;
    private final boolean indexingEnabled;

    private final AtomicReference<VectorIndexStatus> indexingStatus = new AtomicReference<>(VectorIndexStatus.NOT_STARTED);
    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private volatile Instant lastIndexedAt;
    private volatile String lastErrorMessage;
    private volatile int indexedCount = 0;

    @org.springframework.beans.factory.annotation.Autowired
    public ProductVectorIndexer(
        ProductRepository productRepository,
        VectorStore vectorStore,
        @Value("${wms.ai.vector-indexing.enabled:true}") boolean indexingEnabled
    ) {
        this.productRepository = productRepository;
        this.vectorStore = vectorStore;
        this.indexingEnabled = indexingEnabled;
    }

    public ProductVectorIndexer(ProductRepository productRepository, VectorStore vectorStore) {
        this(productRepository, vectorStore, true);
    }

    /**
     * Non-blocking listener triggered on {@link ApplicationReadyEvent}.
     * Dispatches vector indexing asynchronously to a background thread, ensuring
     * HTTP server readiness and health probes are never blocked by external AI latency.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!indexingEnabled) {
            log.info("Startup vector indexing is disabled by configuration (wms.ai.vector-indexing.enabled=false).");
            indexingStatus.set(VectorIndexStatus.NOT_STARTED);
            return;
        }
        CompletableFuture.runAsync(this::indexAllProducts);
    }

    /**
     * Indexes all products in the database into the vector store.
     * Guarded by an atomic mutex to prevent concurrent duplicate re-indexing runs.
     */
    public void indexAllProducts() {
        if (!isIndexing.compareAndSet(false, true)) {
            log.info("Product vector indexing is already in progress, skipping duplicate invocation.");
            return;
        }

        indexingStatus.set(VectorIndexStatus.IN_PROGRESS);
        log.info("Starting Product Vector Indexing in background thread...");

        try {
            List<Product> products = productRepository.findAll();

            if (products.isEmpty()) {
                log.info("No products found to index.");
                indexedCount = 0;
                lastIndexedAt = Instant.now();
                lastErrorMessage = null;
                indexingStatus.set(VectorIndexStatus.READY);
                return;
            }

            List<Document> documents = products.stream()
                .map(this::createDocument)
                .toList();

            vectorStore.add(documents);
            indexedCount = documents.size();
            lastIndexedAt = Instant.now();
            lastErrorMessage = null;
            indexingStatus.set(VectorIndexStatus.READY);
            log.info("Successfully indexed {} products into PGVector.", documents.size());
        } catch (Exception e) {
            lastErrorMessage = e.getMessage();
            indexingStatus.set(VectorIndexStatus.FAILED);
            log.warn("Failed to index products into PGVector on startup (OpenAI API key may not be configured or service unreachable): {}", e.getMessage());
        } finally {
            isIndexing.set(false);
        }
    }

    public VectorIndexStatus getIndexingStatus() {
        return indexingStatus.get();
    }

    public boolean isIndexingInProgress() {
        return isIndexing.get();
    }

    public Instant getLastIndexedAt() {
        return lastIndexedAt;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public int getIndexedCount() {
        return indexedCount;
    }

    /**
     * Indexes or re-indexes a single product after creation or update.
     *
     * @param product the product to index
     */
    public void indexProduct(Product product) {
        log.info("Indexing new/updated product into PGVector: {}", product.getName());
        try {
            Document doc = createDocument(product);
            vectorStore.add(List.of(doc));
        } catch (Exception e) {
            log.warn("Failed to index product {} into PGVector: {}", product.getName(), e.getMessage());
        }
    }

    /**
     * Removes a product from the vector store by its ID.
     *
     * @param productId the product ID
     */
    public void removeProduct(Long productId) {
        log.info("Removing product ID {} from PGVector...", productId);
        try {
            String documentId = generateDocumentId(productId);
            vectorStore.delete(List.of(documentId));
        } catch (Exception e) {
            log.warn("Failed to remove product ID {} from PGVector: {}", productId, e.getMessage());
        }
    }


    private Document createDocument(Product product) {
        String searchableContent = "Product Name: " + product.getName() +
            ". Description: " + (product.getDescription() != null ? product.getDescription() : "Warehouse inventory item");

        Map<String, Object> metadata = Map.of(
            "productId", product.getId(),
            "barcode", product.getBarcode(),
            "name", product.getName()
        );

        String documentId = generateDocumentId(product.getId());

        return new Document(documentId, searchableContent, metadata);
    }

    private String generateDocumentId(Long productId) {
        return UUID.nameUUIDFromBytes(("product-" + productId).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
