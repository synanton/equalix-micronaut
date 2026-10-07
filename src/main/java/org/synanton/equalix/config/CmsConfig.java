package org.synanton.equalix.config;

import io.lettuce.core.api.StatefulRedisConnection;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.hibernate.SessionFactory;
import org.synanton.equalix.adapter.out.cms.CountMinSketchAdapter;
import org.synanton.equalix.adapter.out.cms.HierarchicalCmsProvider;
import org.synanton.equalix.adapter.out.cms.RedisCMSAdapter;
import org.synanton.equalix.adapter.out.cms.TransactionAwareCmsProvider;
import org.synanton.equalix.config.properties.QueueProperties;
import org.synanton.equalix.domain.port.out.CMSProviderPort;
import org.synanton.equalix.domain.service.FairnessHierarchy;

/**
 * Selects the CMS implementation. Both are wrapped so updates apply only when their transaction commits, and in
 * hierarchical mode so that internal nodes are counted too.
 */
@Factory
public class CmsConfig {

    @Singleton
    @Requires(property = "app.queue.cms.mode", value = "local", defaultValue = "local")
    public CMSProviderPort localCmsProvider(QueueProperties props, FairnessHierarchy hierarchy,
        SessionFactory sessionFactory) {
        return decorate(new CountMinSketchAdapter(props), hierarchy, sessionFactory);
    }

    @Singleton
    @Requires(property = "app.queue.cms.mode", value = "redis")
    @Requires(beans = StatefulRedisConnection.class)
    public CMSProviderPort redisCmsProvider(QueueProperties props,
        StatefulRedisConnection<String, String> redisConnection,
        FairnessHierarchy hierarchy,
        SessionFactory sessionFactory) {
        return decorate(new RedisCMSAdapter(props, redisConnection), hierarchy, sessionFactory);
    }

    private static CMSProviderPort decorate(CMSProviderPort sketch, FairnessHierarchy hierarchy,
        SessionFactory sessionFactory) {
        CMSProviderPort transactional = new TransactionAwareCmsProvider(sketch, sessionFactory);
        return hierarchy.isEnabled() ? new HierarchicalCmsProvider(transactional, hierarchy) : transactional;
    }
}
