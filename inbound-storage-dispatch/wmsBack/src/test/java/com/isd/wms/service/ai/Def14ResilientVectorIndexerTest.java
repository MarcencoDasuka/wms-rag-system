package com.isd.wms.service.ai;

import com.isd.wms.entity.Product;
import com.isd.wms.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class Def14ResilientVectorIndexerTest {

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @Test
    @DisplayName("DEF-14: Successful indexing transitions status to READY and sets count and timestamp")
    void indexAllProducts_Success_UpdatesStatusToReady() {
        Product p1 = new Product();
        ReflectionTestUtils.setField(p1, "id", 1L);
        p1.setName("Widget A");
        p1.setBarcode("PROD-001");
        p1.setDescription("Description A");

        Product p2 = new Product();
        ReflectionTestUtils.setField(p2, "id", 2L);
        p2.setName("Widget B");
        p2.setBarcode("PROD-002");
        p2.setDescription("Description B");

        List<Document> addedDocs = new ArrayList<>();

        ProductRepository productRepository = createProxy(ProductRepository.class, (proxy, method, args) -> {
            if ("findAll".equals(method.getName())) {
                return List.of(p1, p2);
            }
            return null;
        });

        VectorStore vectorStore = createProxy(VectorStore.class, (proxy, method, args) -> {
            if ("add".equals(method.getName())) {
                List<Document> docs = (List<Document>) args[0];
                addedDocs.addAll(docs);
                return null;
            }
            return null;
        });

        ProductVectorIndexer indexer = new ProductVectorIndexer(productRepository, vectorStore, true);

        assertThat(indexer.getIndexingStatus()).isEqualTo(ProductVectorIndexer.VectorIndexStatus.NOT_STARTED);

        indexer.indexAllProducts();

        assertThat(indexer.getIndexingStatus()).isEqualTo(ProductVectorIndexer.VectorIndexStatus.READY);
        assertThat(indexer.getIndexedCount()).isEqualTo(2);
        assertThat(indexer.getLastIndexedAt()).isNotNull();
        assertThat(indexer.getLastErrorMessage()).isNull();
        assertThat(indexer.isIndexingInProgress()).isFalse();
        assertThat(addedDocs).hasSize(2);
    }

    @Test
    @DisplayName("DEF-14: Empty product repository marks READY with zero count")
    void indexAllProducts_EmptyDatabase_SetsReadyWithZeroCount() {
        ProductRepository productRepository = createProxy(ProductRepository.class, (proxy, method, args) -> {
            if ("findAll".equals(method.getName())) {
                return Collections.emptyList();
            }
            return null;
        });

        VectorStore vectorStore = createProxy(VectorStore.class, (proxy, method, args) -> null);

        ProductVectorIndexer indexer = new ProductVectorIndexer(productRepository, vectorStore, true);

        indexer.indexAllProducts();

        assertThat(indexer.getIndexingStatus()).isEqualTo(ProductVectorIndexer.VectorIndexStatus.READY);
        assertThat(indexer.getIndexedCount()).isEqualTo(0);
        assertThat(indexer.getLastErrorMessage()).isNull();
    }

    @Test
    @DisplayName("DEF-14: VectorStore failure degrades gracefully without crashing startup")
    void indexAllProducts_ExternalServiceFailure_GracefullyFailsWithStatusFAILED() {
        Product p = new Product();
        ReflectionTestUtils.setField(p, "id", 1L);
        p.setName("Widget A");
        p.setBarcode("PROD-001");
        p.setDescription("Desc");

        ProductRepository productRepository = createProxy(ProductRepository.class, (proxy, method, args) -> {
            if ("findAll".equals(method.getName())) {
                return List.of(p);
            }
            return null;
        });

        VectorStore failingVectorStore = createProxy(VectorStore.class, (proxy, method, args) -> {
            if ("add".equals(method.getName())) {
                throw new RuntimeException("OpenAI API unreachable (401 Unauthorized / Connection timeout)");
            }
            return null;
        });

        ProductVectorIndexer indexer = new ProductVectorIndexer(productRepository, failingVectorStore, true);

        assertThatCode(indexer::indexAllProducts).doesNotThrowAnyException();

        assertThat(indexer.getIndexingStatus()).isEqualTo(ProductVectorIndexer.VectorIndexStatus.FAILED);
        assertThat(indexer.getLastErrorMessage()).contains("OpenAI API unreachable");
        assertThat(indexer.isIndexingInProgress()).isFalse();
    }

    @Test
    @DisplayName("DEF-14: Mutex guard prevents concurrent duplicate re-indexing runs")
    void indexAllProducts_ConcurrentExecution_SkippedByMutex() {
        AtomicInteger findAllCallCount = new AtomicInteger(0);

        ProductRepository productRepository = createProxy(ProductRepository.class, (proxy, method, args) -> {
            if ("findAll".equals(method.getName())) {
                findAllCallCount.incrementAndGet();
                return Collections.emptyList();
            }
            return null;
        });

        VectorStore vectorStore = createProxy(VectorStore.class, (proxy, method, args) -> null);

        ProductVectorIndexer indexer = new ProductVectorIndexer(productRepository, vectorStore, true);

        // Simulate an ongoing indexing operation by locking the mutex
        AtomicBoolean isIndexing = (AtomicBoolean) ReflectionTestUtils.getField(indexer, "isIndexing");
        isIndexing.set(true);

        indexer.indexAllProducts();

        // Must skip without querying product repository
        assertThat(findAllCallCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("DEF-14: onApplicationReady honors disabled configuration")
    void onApplicationReady_WhenDisabled_RemainsNotStarted() {
        ProductRepository productRepository = createProxy(ProductRepository.class, (proxy, method, args) -> null);
        VectorStore vectorStore = createProxy(VectorStore.class, (proxy, method, args) -> null);

        ProductVectorIndexer indexer = new ProductVectorIndexer(productRepository, vectorStore, false);

        indexer.onApplicationReady();

        assertThat(indexer.getIndexingStatus()).isEqualTo(ProductVectorIndexer.VectorIndexStatus.NOT_STARTED);
        assertThat(indexer.isIndexingInProgress()).isFalse();
    }
}
