/*
 *  Copyright (c) 2017 Otávio Santana and others
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   and Apache License v2.0 which accompanies this distribution.
 *   The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *   and the Apache License v2.0 is available at http://www.opensource.org/licenses/apache2.0.php.
 *
 *   You may elect to redistribute this code under either of these licenses.
 *
 *   Contributors:
 *
 *   Otavio Santana
 */
package org.eclipse.jnosql.diana.elasticsearch.document;

import jakarta.nosql.document.Document;
import jakarta.nosql.document.DocumentCollectionManager;
import jakarta.nosql.document.DocumentDeleteQuery;
import jakarta.nosql.document.DocumentEntity;
import jakarta.nosql.document.DocumentQuery;
import org.eclipse.jnosql.diana.document.Documents;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jakarta.nosql.document.DocumentDeleteQuery.delete;
import static jakarta.nosql.document.DocumentQuery.select;
import static org.awaitility.Awaitility.await;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ElasticsearchDocumentCollectionManagerAsyncTest {

    private ElasticsearchDocumentCollectionManagerAsync entityManagerAsync;

    private DocumentCollectionManager entityManager;

    @BeforeEach
    public void setUp() {
        ElasticsearchDocumentCollectionManagerFactory managerFactory = ElasticsearchDocumentCollectionManagerFactorySupplier.INSTACE.get();
        entityManagerAsync = managerFactory.getAsync(DocumentEntityGerator.COLLECTION_NAME);
        entityManager = managerFactory.get(DocumentEntityGerator.INDEX);
        DocumentEntity documentEntity = DocumentEntityGerator.getEntity();
        Document id = documentEntity.find("name").get();
        DocumentQuery query = select().from(DocumentEntityGerator.COLLECTION_NAME).where(id.getName()).eq(id.get()).build();
        DocumentDeleteQuery deleteQuery = delete().from(DocumentEntityGerator.COLLECTION_NAME).where(id.getName()).eq(id.get()).build();
        entityManagerAsync.delete(deleteQuery);
    }

    @Test
    public void shouldClose() {
        entityManager.close();
    }


    @Test
    public void shouldInsertAsync() throws InterruptedException {
        DocumentEntity entity = DocumentEntityGerator.getEntity();
        entityManagerAsync.insert(entity);

        Thread.sleep(1_000L);
        Document id = entity.find("name").get();

        DocumentQuery query = select().from(DocumentEntityGerator.COLLECTION_NAME).where(id.getName()).eq(id.get()).build();
        List<DocumentEntity> entities = entityManager.select(query).collect(Collectors.toList());
        assertFalse(entities.isEmpty());

    }

    @Test
    public void shouldUpdateAsync() {
        DocumentEntity entity = DocumentEntityGerator.getEntity();
        DocumentEntity documentEntity = entityManager.insert(entity);
        Document newField = Documents.of("newField", "10");
        entity.add(newField);
        entityManagerAsync.update(entity);
    }

    @Test
    public void shouldRemoveEntityAsync() throws InterruptedException {
        DocumentEntity documentEntity = entityManager.insert(DocumentEntityGerator.getEntity());
        Document id = documentEntity.find("name").get();
        DocumentQuery query = select().from(DocumentEntityGerator.COLLECTION_NAME).where(id.getName()).eq(id.get()).build();
        DocumentDeleteQuery deleteQuery = delete().from(DocumentEntityGerator.COLLECTION_NAME).where(id.getName()).eq(id.get()).build();
        entityManagerAsync.delete(deleteQuery);

        AtomicBoolean condition = new AtomicBoolean(false);
        AtomicReference<Stream<DocumentEntity>> entities = new AtomicReference<>();
        entityManagerAsync.select(query, l -> {
            condition.set(true);
            entities.set(l);

        });
        await().untilTrue(condition);
        assertTrue(entities.get().collect(Collectors.toList()).isEmpty());
    }

    @Test
    public void shouldUserSearchBuilder() throws InterruptedException {
        DocumentEntity entity = DocumentEntityGerator.getEntity();
        entityManager.insert(entity);
        Thread.sleep(1_000L);
        TermQueryBuilder query = termQuery("name", "Poliana");
        AtomicReference<Stream<DocumentEntity>> result = new AtomicReference<>();
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        entityManagerAsync.search(query, l -> {
            result.set(l);
            atomicBoolean.set(true);
        }, "person");

        await().untilTrue(atomicBoolean);
        List<DocumentEntity> account = result.get().collect(Collectors.toList());
        assertFalse(account.isEmpty());
    }

    @Test
    public void shouldReturnAll() throws InterruptedException {
        DocumentEntity entity = DocumentEntityGerator.getEntity();
        entityManagerAsync.insert(entity);
        Thread.sleep(1_000L);
        DocumentQuery query = select().from(DocumentEntityGerator.COLLECTION_NAME).build();
        AtomicBoolean condition = new AtomicBoolean(false);
        AtomicReference<Stream<DocumentEntity>> result = new AtomicReference<>();

        entityManagerAsync.select(query, l -> {
            condition.set(true);
            result.set(l);
        });
        await().untilTrue(condition);
        List<DocumentEntity> entities = result.get().collect(Collectors.toList());
        assertFalse(entities.isEmpty());
    }

    @Test
    public void shouldCount() throws InterruptedException {
        AtomicBoolean condition = new AtomicBoolean(false);
        AtomicLong value = new AtomicLong(0L);
        DocumentEntity entity = DocumentEntityGerator.getEntity();
        entityManagerAsync.insert(entity);

        Thread.sleep(1_000L);
        Consumer<Long> callback = l -> {
            condition.set(true);
            value.set(l);
        };
        entityManagerAsync.count(DocumentEntityGerator.COLLECTION_NAME, callback);
        await().untilTrue(condition);
        assertTrue(value.get() > 0);

    }

    @Test
    public void shouldInsertTTL() {
        assertThrows(UnsupportedOperationException.class, () -> {
            entityManagerAsync.insert(DocumentEntityGerator.getEntity(), Duration.ofSeconds(1L));
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            entityManagerAsync.insert(DocumentEntityGerator.getEntity(), Duration.ofSeconds(1L), l -> {
            });
        });
    }
}